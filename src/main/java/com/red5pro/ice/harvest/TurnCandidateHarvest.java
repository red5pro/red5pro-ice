/* See LICENSE.md for license information */
package com.red5pro.ice.harvest;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.EnumSet;

import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.AlternateServerAttribute;
import com.red5pro.ice.attribute.AttributeFactory;
import com.red5pro.ice.attribute.ChannelNumberAttribute;
import com.red5pro.ice.attribute.LifetimeAttribute;
import com.red5pro.ice.attribute.ReservationTokenAttribute;
import com.red5pro.ice.attribute.RequestedTransportAttribute;
import com.red5pro.ice.attribute.UnknownAttributesAttribute;
import com.red5pro.ice.attribute.XorPeerAddressAttribute;
import com.red5pro.ice.attribute.XorRelayedAddressAttribute;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.socket.RelayedCandidateConnection;
import com.red5pro.ice.stack.TransactionID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Candidate;
import com.red5pro.ice.CandidateType;
import com.red5pro.ice.HostCandidate;
import com.red5pro.ice.LocalCandidate;
import com.red5pro.ice.RelayedCandidate;
import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunException;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * Represents the harvesting of TURN Candidates for a specific HostCandidate performed by a specific TurnCandidateHarvester.
 *
 * @author Lyubomir Marinov
 */
public class TurnCandidateHarvest extends StunCandidateHarvest {

    private static final Logger logger = LoggerFactory.getLogger(TurnCandidateHarvest.class);

    /**
     * The Request created by the last call to createRequestToStartResolvingCandidate.
     */
    private Request requestToStartResolvingCandidate;

    /**
     * Allocate attributes disabled due to server errors (e.g. 420 Unknown Attribute).
     */
    private final EnumSet<Attribute.Type> disabledAllocateAttributes = EnumSet.noneOf(Attribute.Type.class);

    /**
     * Indicates whether a fallback to Binding has been attempted.
     */
    private boolean bindingFallbackAttempted;

    /**
     * Initializes a new TurnCandidateHarvest which is to represent the harvesting of TURN Candidates for a specific HostCandidate performed
     * by a specific TurnCandidateHarvester.
     *
     * @param harvester the TurnCandidateHarvester which is performing the TURN harvesting
     * @param hostCandidate the HostCandidate for which TURN Candidates are to be harvested
     */
    public TurnCandidateHarvest(TurnCandidateHarvester harvester, HostCandidate hostCandidate) {
        super(harvester, hostCandidate);
    }

    /**
     * Notifies this TurnCandidateHarvest that a specific RelayedCandidateDatagramSocket is closing and that this instance
     * is to delete the associated TURN Allocation.
     * <p>
     * <b>Note</b>: The method is part of the internal API of RelayedCandidateDatagramSocket and TurnCandidateHarvest
     * and is not intended for public use.
     *
     * @param relayedCandidateSocket the RelayedCandidateDatagramSocket which notifies this instance and which requests that the associated TURN
     * Allocation be deleted
     */
    public void close(RelayedCandidateConnection relayedCandidateSocket) {
        // FIXME As far as logic goes, it seems that it is possible to send a TURN Refresh, cancel the STUN keep-alive functionality here and only
        // then receive the response to the TURN Refresh which will enable the STUN keep-alive functionality again.
        setSendKeepAliveMessageInterval(SEND_KEEP_ALIVE_MESSAGE_INTERVAL_NOT_SPECIFIED);
        // TURN Refresh with a LIFETIME value equal to zero deletes the TURN Allocation.
        try {
            sendRequest(MessageFactory.createRefreshRequest(0), false, null);
        } catch (StunException sex) {
            logger.warn("Failed to send TURN Refresh request to delete Allocation", sex);
        }
    }

    /**
     * Completes the harvesting of Candidates for {@link #hostCandidate}. Notifies {@link #harvester} about the completion
     * of the harvesting of Candidate for hostCandidate performed by this StunCandidateHarvest.
     *
     * @param request the Request sent by this StunCandidateHarvest with which the harvesting of
     * Candidates for hostCandidate has completed
     * @param response the Response received by this StunCandidateHarvest, if any, with which the harvesting of
     * Candidates for hostCandidate has completed
     * @return true if the harvesting of Candidates for hostCandidate performed by this StunCandidateHarvest has completed; otherwise, false
     * @see StunCandidateHarvest#completedResolvingCandidate(Request, Response)
     */
    @Override
    protected boolean completedResolvingCandidate(Request request, Response response) {
        // TODO If the Allocate request is rejected because the server lacks resources to fulfill it, the agent SHOULD instead send a Binding request
        // to obtain a server reflexive candidate.
        if (response == null || (!response.isSuccessResponse() && request.getMessageType() == Message.ALLOCATE_REQUEST)) {
            try {
                if (startResolvingCandidate()) {
                    return false;
                }
            } catch (Exception ex) {
                // Complete the harvesting of Candidates for hostCandidate because the new attempt has just failed
            }
        }
        return super.completedResolvingCandidate(request, response);
    }

    /**
     * Creates new Candidates determined by a specific STUN Response.
     *
     * @param response the received STUN Response
     * @see StunCandidateHarvest#createCandidates(Response)
     */
    @Override
    protected void createCandidates(Response response) {
        // Let the super create the ServerReflexiveCandidate first
        super.createCandidates(response);
        // Creates a RelayedCandidate using the XOR-RELAYED-ADDRESS attribute in a specific STUN Response for the actual TransportAddress of the
        // new candidate. If the message is malformed and/or does not contain the corresponding attribute, no relay candidate is created.
        Attribute attribute = response.getAttribute(Attribute.Type.XOR_RELAYED_ADDRESS);
        if (attribute instanceof XorRelayedAddressAttribute) {
            TransportAddress relayedAddress = ((XorRelayedAddressAttribute) attribute).getAddress(response.getTransactionID());
            RelayedCandidate relayedCandidate = createRelayedCandidate(relayedAddress, getMappedAddress(response));
            if (relayedCandidate != null) {
                IceSocketWrapper socket = relayedCandidate.getCandidateIceSocketWrapper();
                // connectivity checks utilize STUN on the (application-purposed) socket of the RelayedCandidate, add it to the StunStack
                //harvester.getStunStack().addSocket(socket, relayedAddress, false);
                relayedCandidate.getParentComponent().getComponentSocket().addSocketWrapper(socket);
                addCandidate(relayedCandidate);
            }
        }
    }

    /**
     * Creates a new STUN Message to be sent to the STUN server associated with the StunCandidateHarvester of this instance in
     * order to keep a specific LocalCandidate (harvested by this instance) alive.
     *
     * @param candidate the LocalCandidate (harvested by this instance) to create a new keep-alive STUN message for
     * @return a new keep-alive STUN Message for the specified candidate or null if no keep-alive sending is to occur
     * @throws StunException if anything goes wrong while creating the new keep-alive STUN Message for the specified candidate
     * or the candidate is of an unsupported CandidateType
     * @see StunCandidateHarvest#createKeepAliveMessage(LocalCandidate)
     */
    @Override
    protected Message createKeepAliveMessage(LocalCandidate candidate) throws StunException {
        switch (candidate.getType()) {
            case RELAYED_CANDIDATE:
                return MessageFactory.createRefreshRequest();
            case SERVER_REFLEXIVE_CANDIDATE:
                // RFC 5245: The Refresh requests will also refresh the server reflexive candidate
                boolean existsRelayedCandidate = false;
                for (Candidate<?> aCandidate : getCandidates()) {
                    if (CandidateType.RELAYED_CANDIDATE.equals(aCandidate.getType())) {
                        existsRelayedCandidate = true;
                        break;
                    }
                }
                return existsRelayedCandidate ? null : super.createKeepAliveMessage(candidate);
            default:
                return super.createKeepAliveMessage(candidate);
        }
    }

    /**
     * Creates a new RelayedCandidate instance which is to represent a specific TransportAddress harvested through
     * {@link #hostCandidate} and the TURN server associated with {@link #harvester}.
     *
     * @param transportAddress the TransportAddress to be represented by the new RelayedCandidate instance
     * @param mappedAddress the mapped TransportAddress reported by the TURN server with the delivery of the relayed transportAddress to
     * be represented by the new RelayedCandidate instance
     * @return a new RelayedCandidate instance which represents the specified TransportAddress harvested through
     * {@link #hostCandidate} and the TURN server associated with {@link #harvester}
     */
    protected RelayedCandidate createRelayedCandidate(TransportAddress transportAddress, TransportAddress mappedAddress) {
        return new RelayedCandidate(transportAddress, this, mappedAddress);
    }

    /**
     * Creates a new Request instance which is to be sent by this StunCandidateHarvest in order to retry a specific
     * Request. For example, the long-term credential mechanism dictates that a Request is first sent by the client without any
     * credential-related attributes, then it gets challenged by the server and the client retries the original Request with the appropriate
     * credential-related attributes in response.
     *
     * @param request the Request which is to be retried by this StunCandidateHarvest
     * @return the new Request instance which is to be sent by this StunCandidateHarvest in order to retry the specified request
     * @see StunCandidateHarvest#createRequestToRetry(Request)
     */
    @Override
    protected Request createRequestToRetry(Request request) {
        logger.debug("createRequestToRetry: {}", request.getName());
        char type = request.getMessageType();
        switch (type) {
            case Message.ALLOCATE_REQUEST: {
                RequestedTransportAttribute requestedTransportAttribute = (RequestedTransportAttribute) request
                        .getAttribute(Attribute.Type.REQUESTED_TRANSPORT);
                // XXX defaults to UDP if no transport is requested via attribute
                int requestedTransport = (requestedTransportAttribute == null) ? Transport.UDP.getProtocolNumber()
                        : requestedTransportAttribute.getRequestedTransport();
                return createAllocateRequest((byte) requestedTransport);
            }
            case Message.CHANNELBIND_REQUEST: {
                ChannelNumberAttribute channelNumberAttribute = (ChannelNumberAttribute) request
                        .getAttribute(Attribute.Type.CHANNEL_NUMBER);
                char channelNumber = channelNumberAttribute.getChannelNumber();
                XorPeerAddressAttribute peerAddressAttribute = (XorPeerAddressAttribute) request
                        .getAttribute(Attribute.Type.XOR_PEER_ADDRESS);
                TransportAddress peerAddress = peerAddressAttribute.getAddress(request.getTransactionID());
                logger.debug("Retry channel bind for {}", peerAddress);
                byte[] retryTransactionID = TransactionID.createNewTransactionID().getBytes();
                Request retryChannelBindRequest = MessageFactory.createChannelBindRequest(channelNumber, peerAddress, retryTransactionID);
                try {
                    retryChannelBindRequest.setTransactionID(retryTransactionID);
                } catch (StunException sex) {
                    throw new UndeclaredThrowableException(sex);
                }
                return retryChannelBindRequest;
            }
            case Message.CREATEPERMISSION_REQUEST: {
                XorPeerAddressAttribute peerAddressAttribute = (XorPeerAddressAttribute) request
                        .getAttribute(Attribute.Type.XOR_PEER_ADDRESS);
                TransportAddress peerAddress = peerAddressAttribute.getAddress(request.getTransactionID());
                logger.debug("Retry permission for {}", peerAddress);
                byte[] retryTransactionID = TransactionID.createNewTransactionID().getBytes();
                Request retryCreatePermissionRequest = MessageFactory.createCreatePermissionRequest(peerAddress, retryTransactionID);
                try {
                    retryCreatePermissionRequest.setTransactionID(retryTransactionID);
                } catch (StunException sex) {
                    throw new UndeclaredThrowableException(sex);
                }
                return retryCreatePermissionRequest;
            }
            case Message.REFRESH_REQUEST: {
                LifetimeAttribute lifetimeAttribute = (LifetimeAttribute) request.getAttribute(Attribute.Type.LIFETIME);
                if (lifetimeAttribute == null) {
                    return MessageFactory.createRefreshRequest();
                } else {
                    return MessageFactory.createRefreshRequest(lifetimeAttribute.getLifetime());
                }
            }
            default:
                return super.createRequestToRetry(request);
        }
    }

    /**
     * Creates a new Request which is to be sent to {@link TurnCandidateHarvester#stunServer} in order to start resolving {@link #hostCandidate}.
     *
     * @return a new Request which is to be sent to TurnCandidateHarvester#stunServer in order to start resolving hostCandidate
     * @see StunCandidateHarvest#createRequestToStartResolvingCandidate()
     */
    @Override
    protected Request createRequestToStartResolvingCandidate() {
        logger.debug("createRequestToStartResolvingCandidate");
        if (requestToStartResolvingCandidate == null) {
            // get the protocol number matching our transport
            byte protocol = getRequestedTransportProtocol(hostCandidate.getTransport());
            if (hostCandidate.getTransport() == Transport.TCP && !StackProperties.getBoolean(StackProperties.TURN_ENABLE_TCP, true)) {
                logger.info("TURN TCP disabled, skipping allocation");
                return null;
            }
            if (hostCandidate.getTransport() == Transport.TLS && !StackProperties.getBoolean(StackProperties.TURN_ENABLE_TLS, false)) {
                logger.info("TURN TLS disabled, skipping allocation");
                return null;
            }
            if (harvester.stunServer.getTransport() == Transport.TLS
                    && !StackProperties.getBoolean(StackProperties.TURN_ENABLE_TLS, false)) {
                logger.info("TURN TLS disabled for server {}, skipping allocation", harvester.stunServer);
                return null;
            }
            logger.info("createRequestToStartResolvingCandidate - protocol: {}", protocol);
            requestToStartResolvingCandidate = createAllocateRequest(protocol);
            return requestToStartResolvingCandidate;
            //        } else if (requestToStartResolvingCandidate.getMessageType() == Message.ALLOCATE_REQUEST) {
            //            requestToStartResolvingCandidate = super.createRequestToStartResolvingCandidate();
            //            return requestToStartResolvingCandidate;
        }
        return null;
    }

    private byte getRequestedTransportProtocol(Transport transport) {
        if (transport == Transport.TLS || transport == Transport.SSLTCP) {
            return Transport.TCP.getProtocolNumber();
        }
        return transport.getProtocolNumber();
    }

    private Request createAllocateRequest(byte protocol) {
        boolean useEvenPort = StackProperties.getBoolean(StackProperties.TURN_USE_EVEN_PORT, false);
        boolean rFlag = StackProperties.getBoolean(StackProperties.TURN_EVEN_PORT_RFLAG, false);
        byte[] reservationToken = (useEvenPort && rFlag) ? harvester.getReservationToken() : null;
        Request request = MessageFactory.createAllocateRequest(protocol, rFlag, reservationToken);
        if (useEvenPort && !rFlag) {
            request.putAttribute(AttributeFactory.createEvenPortAttribute(false));
        }
        if (!disabledAllocateAttributes.isEmpty()) {
            for (Attribute.Type type : disabledAllocateAttributes) {
                request.removeAttribute(type);
            }
        }
        return request;
    }

    /**
     * Notifies this StunCandidateHarvest that a specific Request has either received an error Response or has failed to receive any Response.
     *
     * @param response the error Response which has been received for request
     * @param request the Request to which Response responds
     * @param transactionID the TransactionID of response and request because response and request only have
     * it as a byte array and TransactionID is required for the applicationData property value
     * @return true if the error or failure condition has been processed and this instance can continue its execution (e.g. the
     * resolution of the candidate) as if it was expected; otherwise, false
     * @see StunCandidateHarvest#processErrorOrFailure(Response, Request, TransactionID)
     */
    @Override
    protected boolean processErrorOrFailure(Response response, Request request, TransactionID transactionID) {
        // TurnCandidateHarvest uses the applicationData of TransactionID to deliver the results of Requests sent by RelayedCandidateDatagramSocket back to it.
        Object applicationData = transactionID.getApplicationData();
        if ((applicationData instanceof RelayedCandidateConnection)
                && ((RelayedCandidateConnection) applicationData).processErrorOrFailure(response, request)) {
            return true;
        }
        if (response == null || request == null) {
            return super.processErrorOrFailure(response, request, transactionID);
        }
        com.red5pro.ice.attribute.ErrorCodeAttribute errorCodeAttr = (com.red5pro.ice.attribute.ErrorCodeAttribute) response
                .getAttribute(Attribute.Type.ERROR_CODE);
        if (errorCodeAttr == null) {
            return super.processErrorOrFailure(response, request, transactionID);
        }
        char errorCode = errorCodeAttr.getErrorCode();
        switch (errorCode) {
            case com.red5pro.ice.attribute.ErrorCodeAttribute.TRY_ALTERNATE: {
                if (!StackProperties.getBoolean(StackProperties.TURN_TRY_ALTERNATE, true)) {
                    return super.processErrorOrFailure(response, request, transactionID);
                }
                AlternateServerAttribute alternate = (AlternateServerAttribute) response.getAttribute(Attribute.Type.ALTERNATE_SERVER);
                if (alternate != null) {
                    TransportAddress alternateServer = alternate.getAddress();
                    if (alternateServer != null) {
                        logger.info("TRY_ALTERNATE: switching TURN server to {}", alternateServer);
                        setStunServerOverride(alternateServer);
                        clearLongTermCredentialSession();
                        return retryRequest(request, request.getMessageType() == Message.ALLOCATE_REQUEST);
                    }
                }
                break;
            }
            case com.red5pro.ice.attribute.ErrorCodeAttribute.UNKNOWN_ATTRIBUTE: {
                UnknownAttributesAttribute unknown = (UnknownAttributesAttribute) response.getAttribute(Attribute.Type.UNKNOWN_ATTRIBUTES);
                if (unknown != null && disableUnknownAllocateAttributes(unknown)) {
                    logger.info("UNKNOWN_ATTRIBUTE: retrying without unsupported attributes");
                    return retryRequest(request, request.getMessageType() == Message.ALLOCATE_REQUEST);
                }
                break;
            }
            case com.red5pro.ice.attribute.ErrorCodeAttribute.ADDRESS_FAMILY_NOT_SUPPORTED: {
                disabledAllocateAttributes.add(Attribute.Type.REQUESTED_ADDRESS_FAMILY);
                return retryRequest(request, request.getMessageType() == Message.ALLOCATE_REQUEST);
            }
            case com.red5pro.ice.attribute.ErrorCodeAttribute.ALLOCATION_MISMATCH: {
                if (request.getMessageType() != Message.ALLOCATE_REQUEST) {
                    return restartAllocation();
                }
                break;
            }
            case com.red5pro.ice.attribute.ErrorCodeAttribute.UNSUPPORTED_TRANSPORT_PROTOCOL:
            case com.red5pro.ice.attribute.ErrorCodeAttribute.ALLOCATION_QUOTA_REACHED:
            case com.red5pro.ice.attribute.ErrorCodeAttribute.INSUFFICIENT_CAPACITY: {
                return fallbackToBinding();
            }
            default:
                break;
        }
        return super.processErrorOrFailure(response, request, transactionID);
    }

    private boolean retryRequest(Request request, boolean firstRequest) {
        Request retry = createRequestToRetry(request);
        if (retry == null) {
            return false;
        }
        if (!disabledAllocateAttributes.isEmpty()) {
            for (Attribute.Type type : disabledAllocateAttributes) {
                retry.removeAttribute(type);
            }
        }
        try {
            sendRequest(retry, firstRequest, null);
            return true;
        } catch (StunException sex) {
            logger.warn("Failed to retry request {}", retry, sex);
            return false;
        }
    }

    private boolean restartAllocation() {
        try {
            return startResolvingCandidate();
        } catch (Exception ex) {
            logger.warn("Failed to restart allocation", ex);
            return false;
        }
    }

    private boolean fallbackToBinding() {
        if (bindingFallbackAttempted) {
            return false;
        }
        bindingFallbackAttempted = true;
        try {
            Request bindingRequest = super.createRequestToStartResolvingCandidate();
            addShortTermCredentialAttributes(bindingRequest);
            sendRequest(bindingRequest, true, null);
            return true;
        } catch (StunException sex) {
            logger.warn("Failed to fallback to Binding request", sex);
            return false;
        }
    }

    private boolean disableUnknownAllocateAttributes(UnknownAttributesAttribute unknown) {
        boolean changed = false;
        for (int i = 0; i < unknown.getAttributeCount(); i++) {
            int attributeId = unknown.getAttribute(i);
            Attribute.Type type = Attribute.Type.valueOf(attributeId);
            switch (type) {
                case DONT_FRAGMENT:
                case EVEN_PORT:
                case REQUESTED_ADDRESS_FAMILY:
                case REQUESTED_TRANSPORT:
                case RESERVATION_TOKEN:
                    changed |= disabledAllocateAttributes.add(type);
                    break;
                default:
                    break;
            }
        }
        return changed;
    }

    /**
     * Handles a specific STUN success Response to a specific STUN Request.
     *
     * @param response the received STUN success Response which is to be handled
     * @param request the STUN Request to which response responds
     * @param transactionID the TransactionID of response and request because response and request only have
     * it as a byte array and TransactionID is required for the applicationData property value
     * @see StunCandidateHarvest#processSuccess(Response, Request, TransactionID)
     */
    @Override
    protected void processSuccess(Response response, Request request, TransactionID transactionID) {
        super.processSuccess(response, request, transactionID);
        LifetimeAttribute lifetimeAttribute;
        // lifetime is in seconds
        int lifetime = -1;
        // TurnCandidateHarvest uses the applicationData of TransactionID to deliver the results of Requests sent by RelayedCandidateConnection back to it
        Object applicationData = transactionID.getApplicationData();
        switch (response.getMessageType()) {
            case Message.ALLOCATE_RESPONSE:
                // The default lifetime of an allocation is 10 minutes.
                lifetimeAttribute = (LifetimeAttribute) response.getAttribute(Attribute.Type.LIFETIME);
                lifetime = (lifetimeAttribute == null) ? 600 : lifetimeAttribute.getLifetime();
                ReservationTokenAttribute reservationToken = (ReservationTokenAttribute) response
                        .getAttribute(Attribute.Type.RESERVATION_TOKEN);
                if (reservationToken != null) {
                    harvester.setReservationToken(reservationToken.getReservationToken());
                }
                // attach a relayed connection to the transaction in lieu of it successfully
                if (applicationData == null) {
                    candidates.forEach(candidate -> {
                        if (candidate instanceof RelayedCandidate) {
                            TransportAddress relayedAddr = ((RelayedCandidate) candidate).getRelayedAddress();
                            logger.debug("Relayed address: {}", relayedAddr);
                            try {
                                transactionID.setApplicationData(new RelayedCandidateConnection((RelayedCandidate) candidate, this));
                            } catch (Exception e) {
                                logger.warn("Exception creating relayed connection", e);
                            }
                            return;
                        }
                    });
                    applicationData = transactionID.getApplicationData();
                    logger.debug("Transaction application data: {}", applicationData);
                }
                break;
            case Message.REFRESH_RESPONSE:
                lifetimeAttribute = (LifetimeAttribute) response.getAttribute(Attribute.Type.LIFETIME);
                if (lifetimeAttribute != null) {
                    lifetime = lifetimeAttribute.getLifetime();
                }
                break;
        }
        if (lifetime >= 0) {
            // milliseconds
            setSendKeepAliveMessageInterval(1000L * lifetime);
        }
        // forward the success if relay connection is attached
        if (applicationData instanceof RelayedCandidateConnection) {
            logger.debug("Forwarding success to the relayed candidate");
            ((RelayedCandidateConnection) applicationData).processSuccess(response, request);
        }
    }

    /**
     * Sends a specific Request on behalf of a specific RelayedCandidateConnection to the TURN server associated with this TurnCandidateHarvest.
     *
     * @param relayedConnection the RelayedCandidateConnection which sends the specified Request and which is to be notified of the result
     * @param request the Request to be sent to the TURN server associated with this TurnCandidateHarvest
     * @return an array of bytes which represents the ID of the transaction with which the specified Request has been sent to the TURN server
     * @throws StunException if anything goes wrong while sending the specified Request
     */
    public byte[] sendRequest(RelayedCandidateConnection relayedConnection, Request request) throws StunException {
        logger.debug("sendRequest: {}", request);
        TransactionID transactionID = TransactionID.createNewTransactionID();
        transactionID.setApplicationData(relayedConnection);
        transactionID = sendRequest(request, false, transactionID);
        return (transactionID == null) ? null : transactionID.getBytes();
    }

}
