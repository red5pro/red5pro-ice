/* See LICENSE.md for license information */
package com.red5pro.ice.ice;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;

import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.AttributeFactory;
import com.red5pro.ice.attribute.ErrorCodeAttribute;
import com.red5pro.ice.attribute.IceControlledAttribute;
import com.red5pro.ice.attribute.IceControllingAttribute;
import com.red5pro.ice.attribute.MessageIntegrityAttribute;
import com.red5pro.ice.attribute.PriorityAttribute;
import com.red5pro.ice.attribute.UsernameAttribute;
import com.red5pro.ice.attribute.XorMappedAddressAttribute;
import com.red5pro.ice.message.Indication;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.ice.stack.TransactionID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.ResponseCollector;
import com.red5pro.ice.StunException;
import com.red5pro.ice.StunResponseEvent;
import com.red5pro.ice.StunTimeoutEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * The class that will be generating our outgoing connectivity checks and that will be handling their responses or lack thereof.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Paul Gregoire
 */
class ConnectivityCheckClient implements ResponseCollector {

    private static final Logger logger = LoggerFactory.getLogger(ConnectivityCheckClient.class);

    // XXX(paul) base timeout for processes within; using STUN timeout for the base to-start.
    private static long checklistTimeout = 3000L;
    
    /**
     * The agent that created us.
     */
    private final Agent parentAgent;

    /**
     * The StunStack that we will use for connectivity checks.
     */
    private final StunStack stunStack;

    /**
     * The {@link PaceMaker}s that are currently running checks in this client.
     */
    private final CopyOnWriteArraySet<Future<?>> paceMakerFutures = new CopyOnWriteArraySet<>();

    /**
     * Timer that is used to let some seconds before a CheckList is considered as FAILED.
     */
    private Map<String, Future<?>> timerFutures = new HashMap<>();

    /**
     * A flag that determines whether we have received a STUN response or not.
     */
    private boolean alive;

    /**
     * Creates a new ConnectivityCheckClient setting parentAgent as the agent that will be used for retrieving
     * information such as user fragments for example.
     *
     * @param parentAgent the Agent that is creating this instance.
     */
    public ConnectivityCheckClient(Agent parentAgent) {
        this.parentAgent = parentAgent;
        stunStack = this.parentAgent.getStunStack();
    }

    /**
     * Returns a boolean value indicating whether we have received a STUN response or not.
     *
     * Note that this should NOT be taken as an indication that the negotiation has succeeded, it merely indicates that we have received ANY STUN
     * response, possibly a BINDING_ERROR_RESPONSE. It is completely unrelated/independent from the ICE spec and it's only meant to be used
     * for debugging purposes.
     *
     * @return a boolean value indicating whether we have received a STUN response or not.
     */
    boolean isAlive() {
        return alive;
    }

    /**
     * Starts client connectivity checks for the first {@link IceMediaStream} in our parent {@link Agent}. This method should only be called by
     * the parent {@link Agent} when connectivity establishment starts for a particular check list.
     */
    public void startChecks() {
        List<IceMediaStream> streamsWithPendingConnectivityEstablishment = parentAgent.getStreamsWithPendingConnectivityEstablishment();
        if (streamsWithPendingConnectivityEstablishment.size() > 0) {
            logger.debug("Start connectivity checks. Local ufrag {}", parentAgent.getLocalUfrag());
            startChecks(streamsWithPendingConnectivityEstablishment.get(0).getCheckList());
        } else {
            logger.info("Not starting any checks, because there are no pending streams");
        }
    }

    /**
     * Starts client connectivity checks for the {@link CandidatePair}s in checkList
     *
     * @param checkList the {@link CheckList} to start client side connectivity checks for.
     */
    public void startChecks(CheckList checkList) {
        paceMakerFutures.add(parentAgent.submit(new PaceMaker(checkList)));
    }

    /**
     * Creates a STUN Binding {@link Indication} to a candidate pair. It is used as a keep-alive.
     *
     * @param candidatePair that {@link CandidatePair} that we'd like to send an indication
     */
    protected void sendBindingIndicationForPair(CandidatePair candidatePair) {
        LocalCandidate localCandidate = candidatePair.getLocalCandidate();
        Indication indication = MessageFactory.createBindingIndication();
        try {
            stunStack.sendIndication(indication, candidatePair.getRemoteCandidate().getTransportAddress(), localCandidate.getBase().getTransportAddress());
            if (logger.isTraceEnabled()) {
                logger.trace("Sending binding indication for pair {}", candidatePair);
            }
        } catch (Exception ex) {
            IceSocketWrapper stunSocket = localCandidate.getStunSocket(null);
            if (stunSocket != null) {
                logger.warn("Failed to send {} through {}", indication, stunSocket.getLocalSocketAddress(), ex);
            }
        }
    }

    /**
     * Creates a STUN {@link Request} containing the necessary PRIORITY and CONTROLLING/CONTROLLED attributes. Also stores a reference to
     * candidatePair in the newly created transactionID so that we could then refer back to it in subsequent response or failure events.
     *
     * @param candidatePair that CandidatePair that we'd like to start a check for
     * @return a reference to the TransactionID used in the connectivity check client transaction or null if sending the check has
     * failed for some reason
     */
    protected TransactionID startCheckForPair(CandidatePair candidatePair) {
        return startCheckForPair(candidatePair, -1, -1, -1);
    }

    /**
     * Creates a STUN {@link Request} containing the necessary PRIORITY and CONTROLLING/CONTROLLED attributes. Also stores a reference to
     * candidatePair in the newly created transactionID so that we could then refer back to it in subsequent response or failure events.
     *
     * @param candidatePair that CandidatePair that we'd like to start a check for.
     * @param originalWaitInterval
     * @param maxWaitInterval
     * @param maxRetransmissions
     * @return a reference to the TransactionID used in the connectivity check client transaction or null if sending the check has
     * failed for some reason.
     */
    protected TransactionID startCheckForPair(CandidatePair candidatePair, int originalWaitInterval, int maxWaitInterval, int maxRetransmissions) {
        logger.debug("startCheckForPair: {} wait: {} max wait: {} max retrans: {}", candidatePair.toShortString(), originalWaitInterval, maxWaitInterval, maxRetransmissions);
        long tieBreaker = parentAgent.getTieBreaker();
        LocalCandidate localCandidate = candidatePair.getLocalCandidate();
        // the priority we'd like the remote party to use for a peer reflexive candidate if one is discovered as a consequence of this check
        long priority = localCandidate.computePriorityForType(CandidateType.PEER_REFLEXIVE_CANDIDATE);
        // we don't need to do a canReach() verification here as it has been already verified during the gathering process.
        Request request = null;
        try {
            request = MessageFactory.createBindingRequest(priority, parentAgent.isControlling(), tieBreaker);
        } catch (StunException e) {
            logger.warn("Exception creating binding request", e);
            request = MessageFactory.createBindingRequest();
        }
        // controlling controlled
        if (parentAgent.isControlling()) {
            if (request.containsNoneAttributes(EnumSet.of(Attribute.Type.ICE_CONTROLLING))) {
                IceControllingAttribute iceControllingAttribute = AttributeFactory.createIceControllingAttribute(tieBreaker);
                request.putAttribute(iceControllingAttribute);
            }
            // if we are the controlling agent then we need to indicate our nominated pairs.
            if (candidatePair.isNominated()) {
                logger.debug("Add USE-CANDIDATE in check for: {}", candidatePair.toShortString());
                request.putAttribute(AttributeFactory.createUseCandidateAttribute());
            }
        } else {
            if (request.containsNoneAttributes(EnumSet.of(Attribute.Type.ICE_CONTROLLED))) {
                IceControlledAttribute iceControlledAttribute = AttributeFactory.createIceControlledAttribute(tieBreaker);
                request.putAttribute(iceControlledAttribute);
            }
        }
        // credentials
        String streamName = candidatePair.getParentComponent().getParentStream().getName();
        String localUserName = parentAgent.generateLocalUserName(streamName);
        if (localUserName == null) {
            return null;
        }
        UsernameAttribute unameAttr = AttributeFactory.createUsernameAttribute(localUserName);
        request.putAttribute(unameAttr);
        // TODO Also implement SASL prepare
        MessageIntegrityAttribute msgIntegrity = AttributeFactory.createMessageIntegrityAttribute(localUserName);
        // when we will encode the MESSAGE-INTEGRITY attribute (thus generate the HMAC-SHA1 authentication), we need to know the
        // remote key of the current stream, that why we pass the media name.
        msgIntegrity.setMedia(streamName); // used to be audio, video, etc...
        request.putAttribute(msgIntegrity);
        TransactionID tran = TransactionID.createNewTransactionID();
        tran.setApplicationData(candidatePair);
        logger.debug("Start stream: {} check for {} tid {}", streamName, candidatePair.toShortString(), tran);
        try {
            RemoteCandidate remoteCandidate = candidatePair.getRemoteCandidate();
            logger.debug("Remote addr: {} reflexive: {}", remoteCandidate.getTransportAddress(), remoteCandidate.getReflexiveAddress());
            // if the candidate is relay, send a permission request ahead of any other request
            CandidateType localType = localCandidate.getType();
            CandidateType remoteType = remoteCandidate.getType();
            switch (localType) {
                case RELAYED_CANDIDATE:
                    if (CandidateType.HOST_CANDIDATE.equals(remoteType)) {
                        logger.debug("Skipping relay -> host pair");
                        candidatePair.setStateFailed();
                    } else {
                        // before we can send to the remote end, a permission must be requested                        
                        RelayedCandidate relayedCandidate = (RelayedCandidate) localCandidate; // cast over to relayed type
                        // get the host candidates address so the lookup wont fail in NetAccessManager
                        TransportAddress localAddress = relayedCandidate.getTurnCandidateHarvest().hostCandidate.getTransportAddress();
                        // request permission for remote
                        TransportAddress remoteAddress = remoteCandidate.getTransportAddress();
                        // destination for STUN/TURN messages in a relay is the TURN server
                        TransportAddress destAddress = relayedCandidate.getTurnCandidateHarvest().harvester.stunServer;
                        logger.debug("Requesting permission for {} to {} through {}", remoteAddress, destAddress, localAddress);
                        // send the permission request instead of the binding request
                        request = MessageFactory.createCreatePermissionRequest(remoteAddress, tran.getBytes());
                        // add the extra attributes required
                        logger.debug("Adding long-term-cred attributes");
                        relayedCandidate.getTurnCandidateHarvest().getLongTermCredentialSession().addAttributes(request);
                        // send the request
                        tran = stunStack.sendRequest(request, destAddress, localAddress, this, tran, originalWaitInterval, maxWaitInterval, maxRetransmissions);
                    }
                    break;
                default:
                    // send the request
                    tran = stunStack.sendRequest(request, remoteCandidate.getTransportAddress(), localCandidate.getBase().getTransportAddress(), this, tran, originalWaitInterval, maxWaitInterval, maxRetransmissions);
                    break;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Transaction {} sent for pair {}", tran, candidatePair);
            }
        } catch (Exception ex) {
            tran = null;
            logger.warn("Failed to start check for pair: {}", candidatePair, ex);
        }
        return tran;
    }

    /**
     * Handles the response as per the procedures described in RFC 5245 or in other words, by either changing the state of the corresponding pair
     * to FAILED, or SUCCEEDED, or rescheduling a check in case of a role conflict.
     *
     * @param ev the StunResponseEvent that contains the newly received response
     */
    public void processResponse(StunResponseEvent ev) {
        alive = true;
        Response response = ev.getResponse();
        Request request = ev.getRequest();
        CandidatePair checkedPair = (CandidatePair) ev.getTransactionID().getApplicationData();
        // make sure that the response came from the right place. disregard if relay type candidate
        if (CandidateType.RELAYED_CANDIDATE.equals(checkedPair.getLocalCandidate().getType())) {
            // set success
            checkedPair.setStateSucceeded();
            // add to validated list, which also sets validated flag
            parentAgent.validatePair(checkedPair);
            // nominate! (only if we're controlling)
            if (parentAgent.isControlling()) {
                parentAgent.nominate(checkedPair);
            }
            // if we're a local relayed candidate, forward the success message
            RelayedCandidate localCandidate = (RelayedCandidate) checkedPair.getLocalCandidate();
            // process success with the relayed connection
            localCandidate.getRelayedCandidateConnection().processSuccess(response, request);
        } else {
            if (!checkSymmetricAddresses(ev)) {
                logger.info("Received a non-symmetric response for pair: {}, Failing", checkedPair.toShortString());
                checkedPair.setStateFailed();
            } else {
                char messageType = response.getMessageType();
                if (messageType == Response.BINDING_ERROR_RESPONSE) {
                    // handle error responses
                    if (response.getAttribute(Attribute.Type.ERROR_CODE) == null) {
                        logger.warn("Received a malformed error response");
                        return; // malformed error response
                    }
                    processErrorResponse(checkedPair, request, response);
                } else if (messageType == Response.BINDING_SUCCESS_RESPONSE || messageType == Response.CREATEPERMISSION_RESPONSE) {
                    // handle success responses
                    processSuccessResponse(checkedPair, request, response);
                } else {
                    logger.warn("Received an unexpected response type: {}", messageType);
                }
            }
        }
        // regardless of whether the check was successful or failed, the completion of the transaction may require updating of check list and timer states.
        updateCheckListAndTimerStates(checkedPair);
    }

    /**
     * Updates all check list and timer states after a check has completed (both if completion was successful or not). The method implements
     * section "7.1.3.3. Check List and Timer State Updates"
     *
     * @param checkedPair the pair whose check has just completed.
     */
    private void updateCheckListAndTimerStates(CandidatePair checkedPair) {
        IceMediaStream stream = checkedPair.getParentComponent().getParentStream();
        if (stream.getParentAgent().getState().isEstablished()) {
            return;
        }
        final CheckList checkList = stream.getCheckList();
        // If all of the pairs in the check list are now either in the Failed or Succeeded state:
        if (checkList.allChecksCompleted()) {
            // If there is not a pair in the valid list for each component of the media stream, the state of the check list is set to Failed.
            if (!stream.validListContainsAllComponents()) {
                final String streamName = stream.getName();
                Future<?> future = timerFutures.get(streamName);
                if (future == null) {
                    logger.debug("CheckList marked as failed in a few seconds if no succeeded checks arrive");
                    timerFutures.put(streamName, parentAgent.submit(() -> {
                        try {
                            logger.debug("Going to sleep for 3s for stream {}", streamName);
                            Thread.sleep(checklistTimeout);
                            if (checkList.getState() != CheckListState.COMPLETED) {
                                logger.warn("CheckList for stream {} FAILED", streamName);
                                checkList.setState(CheckListState.FAILED);
                                parentAgent.checkListStatesUpdated();
                            }
                        } catch (InterruptedException e) {
                        }
                    }));
                }
            }
            // For each frozen check list, the agent groups together all of the pairs with the same foundation, and for each group, sets the
            // state of the pair with the lowest component ID to Waiting.  If there is more than one such pair, the one with the highest
            // priority is used.
            Collection<IceMediaStream> allOtherStreams = parentAgent.getStreams();
            allOtherStreams.remove(stream);
            allOtherStreams.forEach(anotherStream -> {
                CheckList anotherCheckList = anotherStream.getCheckList();
                if (anotherCheckList.isFrozen()) {
                    anotherCheckList.computeInitialCheckListPairStates();
                    startChecks(anotherCheckList);
                }
            });
        }
        parentAgent.checkListStatesUpdated();
    }

    /**
     * Handles STUN success responses as per the rules in RFC 5245.
     * 
     * @param checkedPair
     * @param request
     * @param response
     */
    private void processSuccessResponse(CandidatePair checkedPair, Request request, Response response) {
        TransportAddress mappedAddress = null;
        XorMappedAddressAttribute mappedAddressAttr = (XorMappedAddressAttribute) response.getAttribute(Attribute.Type.XOR_MAPPED_ADDRESS);
        if (mappedAddressAttr == null) {
            logger.warn("Pair failed (no XOR-MAPPED-ADDRESS): {}. Local ufrag {}", checkedPair.toShortString(), parentAgent.getLocalUfrag());
            checkedPair.setStateFailed();
            return;
        }
        mappedAddress = mappedAddressAttr.getAddress(response.getTransactionID());
        // XXX AddressAttribute always returns UDP based TransportAddress
        if (checkedPair.getLocalCandidate().getTransport() == Transport.TCP) {
            mappedAddress = new TransportAddress(mappedAddress.getAddress(), mappedAddress.getPort(), Transport.TCP);
        }
        // In some situations we may have more than one local candidate matching
        // the mapped address. In this case we want to find the that matches
        // the socket we received the response on.
        LocalCandidate base = checkedPair.getLocalCandidate().getBase();
        LocalCandidate validLocalCandidate = parentAgent.findLocalCandidate(mappedAddress, base);
        RemoteCandidate validRemoteCandidate = checkedPair.getRemoteCandidate();
        // RFC 5245: The agent checks the mapped address from the STUN response. If the transport address does not match any of the
        // local candidates that the agent knows about, the mapped address represents a new candidate -- a peer reflexive candidate.
        if (validLocalCandidate == null) {
            //Like other candidates, PEER-REFLEXIVE candidates have a type, base, priority, and foundation.  They are computed as follows:
            //o The type is equal to peer reflexive
            //o The base is the local candidate of the candidate pair from which the STUN check was sent
            //o Its priority is set equal to the value of the PRIORITY attribute in the Binding request
            PriorityAttribute prioAttr = (PriorityAttribute) request.getAttribute(Attribute.Type.PRIORITY);
            long priority = prioAttr.getPriority();
            LocalCandidate peerReflexiveCandidate = new PeerReflexiveCandidate(mappedAddress, checkedPair.getParentComponent(), checkedPair.getLocalCandidate(), priority);
            peerReflexiveCandidate.setBase(checkedPair.getLocalCandidate());
            // peer reflexive candidate is then added to the list of local candidates for the media stream, so that it would be available for updated offers.
            checkedPair.getParentComponent().addLocalCandidate(peerReflexiveCandidate);
            logger.debug("Peer reflexive candiate: {}", peerReflexiveCandidate);
            // However, the peer reflexive candidate is not paired with other remote candidates. This is not necessary; a valid pair will be generated from it momentarily
            validLocalCandidate = peerReflexiveCandidate;
            if (checkedPair.getParentComponent().getSelectedPair() == null) {
                logger.debug("Received a peer-reflexive candidate: {} Local ufrag: {}", peerReflexiveCandidate.getTransportAddress(), parentAgent.getLocalUfrag());
            }
        }
        // check if the resulting valid pair was already in our check lists.
        CandidatePair existingPair = parentAgent.findCandidatePair(validLocalCandidate.getTransportAddress(), validRemoteCandidate.getTransportAddress());
        // RFC 5245: 7.1.3.2.2. The agent constructs a candidate pair whose local candidate equals the mapped address of the response, and whose
        // remote candidate equals the destination address to which the request was sent. This is called a valid pair, since it has been validated
        // by a STUN connectivity check.
        CandidatePair validPair = (existingPair == null) ? parentAgent.createCandidatePair(validLocalCandidate, validRemoteCandidate) : existingPair;
        // we synchronize here because the same pair object can be processed (in another thread) in Agent's triggerCheck. A controlled agent select
        // its pair here if the pair has useCandidateReceived as true (set in triggerCheck) or in triggerCheck if the pair state is succeeded (set
        // here). So be sure that if a binding response and a binding request (for the same check) from other peer come at the very same time, that
        // we will trigger the nominationConfirmed (that will pass the pair as selected if it is the first time).
        //The agent sets the state of the pair that *generated* the check to Succeeded.  Note that, the pair which *generated* the check may be
        //different than the valid pair constructed above
        if (checkedPair.getParentComponent().getSelectedPair() == null) {
            logger.info("Pair succeeded: {} Local ufrag: {}", checkedPair.toShortString(), parentAgent.getLocalUfrag());
        }
        checkedPair.setStateSucceeded();
        if (!validPair.isValid()) {
            if (validPair.getParentComponent().getSelectedPair() == null) {
                logger.debug("Pair validated: {} Local ufrag: {}", validPair.toShortString(), parentAgent.getLocalUfrag());
            }
            parentAgent.validatePair(validPair);
        }
        //The agent changes the states for all other Frozen pairs for the same media stream and same foundation to Waiting.
        IceMediaStream parentStream = checkedPair.getParentComponent().getParentStream();
        for (CandidatePair pair : parentStream.getCheckList()) {
            if (pair.getState() == CandidatePairState.FROZEN && checkedPair.getFoundation().equals(pair.getFoundation())) {
                pair.setStateWaiting();
            }
        }
        // The agent examines the check list for all other streams in turn. If the check list is active, the agent changes the state of all Frozen
        // pairs in that check list whose foundation matches a pair in the valid list under consideration to Waiting.
        Collection<IceMediaStream> allOtherStreams = parentAgent.getStreams();
        allOtherStreams.remove(parentStream);
        for (IceMediaStream stream : allOtherStreams) {
            CheckList checkList = stream.getCheckList();
            boolean wasFrozen = checkList.isFrozen();
            for (CandidatePair pair : checkList) {
                if (parentStream.validListContainsFoundation(pair.getFoundation()) && pair.getState() == CandidatePairState.FROZEN) {
                    pair.setStateWaiting();
                }
            }
            //if the checklList is still frozen after the above operations, the agent groups together all of the pairs with the same
            //foundation, and for each group, sets the state of the pair with the lowest component ID to Waiting.  If there is more than one
            //such pair, the one with the highest priority is used.
            if (checkList.isFrozen()) {
                checkList.computeInitialCheckListPairStates();
            }
            if (wasFrozen) {
                logger.debug("Start checks for checkList of stream {} that was frozen", stream.getName());
                startChecks(checkList);
            }
        }
        // check request for use-candidate attribute
        Attribute attr = request.getAttribute(Attribute.Type.USE_CANDIDATE);
        //if (validPair.getParentComponent().getSelectedPair() == null) {
            logger.info("IsControlling: {} USE-CANDIDATE: {} Local ufrag: {}", parentAgent.isControlling(), (attr != null || checkedPair.useCandidateSent()), parentAgent.getLocalUfrag());
        //}
        //If the agent was a controlling agent, and it had included a USE-CANDIDATE attribute in the Binding request, the valid pair generated
        //from that check has its nominated flag set to true.
        if (parentAgent.isControlling() && attr != null) {
            if (validPair.getParentComponent().getSelectedPair() == null) {
                logger.info("Nomination confirmed for pair: {} Local ufrag: {}", validPair.toShortString(), parentAgent.getLocalUfrag());
                parentAgent.nominationConfirmed(validPair);
            } else {
                logger.trace("Keep alive for pair: {}", validPair.toShortString());
            }
        }
        //If the agent is the controlled agent, the response may be the result of a triggered check that was sent in response to a request that
        //itself had the USE-CANDIDATE attribute.  This case is described in Section 7.2.1.5, and may now result in setting the nominated flag for
        //the pair learned from the original request.
        else if (!parentAgent.isControlling() && checkedPair.useCandidateReceived() && !checkedPair.isNominated()) {
            if (checkedPair.getParentComponent().getSelectedPair() == null) {
                logger.info("Nomination confirmed for pair: {}", validPair.toShortString());
                parentAgent.nominationConfirmed(checkedPair);
            } else {
                logger.trace("Keep alive for pair: {}", validPair.toShortString());
            }
        }
        // Selected pairs get their consent freshness confirmed.
        // XXX Should we also confirm consent freshness for non-selected pairs?
        if (checkedPair.equals(checkedPair.getParentComponent().getSelectedPair())) {
            checkedPair.setConsentFreshness();
        }
    }

    /**
     * Returns true if the {@link Response} in evt had a source or a destination address that match those of the {@link Request},
     * or false otherwise.
     * <br>
     * RFC 5245: The agent MUST check that the source IP address and port of the response equal the destination IP address and port to which the
     * Binding request was sent, and that the destination IP address and port of the response match the source IP address and port from which
     * the Binding request was sent.  In other words, the source and destination transport addresses in the request and responses are
     * symmetric.  If they are not symmetric, the agent sets the state of the pair to Failed.
     *
     * @param evt the StunResponseEvent that contains the Response we need to examine
     * @return true if the Response in evt had a source or a destination address that matched those of the Request, or false otherwise
     */
    private boolean checkSymmetricAddresses(StunResponseEvent evt) {
        CandidatePair pair = ((CandidatePair) evt.getTransactionID().getApplicationData());
        TransportAddress localAddr = pair.getLocalCandidate().getBase().getTransportAddress();
        return localAddr.equals(evt.getLocalAddress()) && pair.getRemoteCandidate().getTransportAddress().equals(evt.getRemoteAddress());
    }

    /**
     * In case of a role conflict, changes the state of the agent and reschedules the check, in all other cases sets the corresponding peer
     * state to FAILED.
     *
     * @param pair
     * @param request
     * @param response
     */
    private void processErrorResponse(CandidatePair pair, Request request, Response response) {
        ErrorCodeAttribute errorAttr = (ErrorCodeAttribute) response.getAttribute(Attribute.Type.ERROR_CODE);
        // GTalk error code is not RFC3489/RFC5389 compliant
        // example: 400 becomes 0x01 0x90 with GTalk
        // RFC3489/RFC5389 gives 0x04 0x00
        int cl = errorAttr.getErrorClass();
        int co = errorAttr.getErrorNumber() & 0xff;
        char errorCode = errorAttr.getErrorCode();
        logger.debug("Received error code {}", (int) errorCode);
        // RESOLVE ROLE_CONFLICTS
        if (errorCode == ErrorCodeAttribute.ROLE_CONFLICT) {
            boolean wasControlling = (request.getAttribute(Attribute.Type.ICE_CONTROLLING) != null);
            logger.debug("Switching to isControlling={}", !wasControlling);
            parentAgent.setControlling(!wasControlling);
            pair.getParentComponent().getParentStream().getCheckList().scheduleTriggeredCheck(pair);
        } else {
            int code = cl * 100 + co;
            logger.info("Error response for pair: " + pair.toShortString() + ", failing.  Code = " + code + "(class=" + cl + "; number=" + co + ")");
            pair.setStateFailed();
        }
    }

    /**
     * Sets the state of the corresponding {@link CandidatePair} to {@link CandidatePairState#FAILED} and updates check list and timer
     * states.
     *
     * @param ev the {@link StunTimeoutEvent} containing the original transaction and hence {@link CandidatePair} that's being checked.
     */
    public void processTimeout(StunTimeoutEvent ev) {
        CandidatePair pair = (CandidatePair) ev.getTransactionID().getApplicationData();
        logger.debug("Timeout for pair: {}, failing", pair.toShortString());
        pair.setStateFailed();
        updateCheckListAndTimerStates(pair);
    }

    /**
     * Stops and removes all PaceMakers.
     */
    public void stop() {
        paceMakerFutures.forEach(paceMakerFuture -> {
            if (!paceMakerFuture.isDone()) {
                paceMakerFuture.cancel(true);
            }
            paceMakerFutures.remove(paceMakerFuture);
        });
        timerFutures.forEach((key, future) -> {
            future.cancel(true);
        });
        timerFutures.clear();
    }

    /**
     * The thread that actually sends the checks for a particular check list in the pace defined in RFC 5245.
     */
    private class PaceMaker implements Runnable {
        /**
         * The {@link CheckList} that this PaceMaker will be running checks for.
         */
        private final CheckList checkList;

        private long checkStartTime;
        
        /**
         * Creates a new {@link PaceMaker} for this ConnectivityCheckClient.
         *
         * @param checkList the {@link CheckList} that we'll be sending checks for
         */
        public PaceMaker(CheckList checkList) {
            this.checkList = checkList;
        }

        /**
         * Returns the number milliseconds to wait before we send the next check.
         *
         * @return the number milliseconds to wait before we send the next check
         */
        private long getNextWaitInterval() {
            int activeCheckLists = parentAgent.getActiveCheckListCount();
            if (activeCheckLists < 1) {
                // don't multiply by 0. even when we no longer have active check lists we may still have nomination checks to
                activeCheckLists = 1;
            }
            // ensure that the maximum returned time for waiting doesn't exceed STUN timeout of 3s
            return Math.min(parentAgent.calculateTa() * activeCheckLists, checklistTimeout);
        }

        /**
         * Sends connectivity checks at the pace determined by the {@link Agent#calculateTa()} method and using either the trigger check queue
         * or the regular check lists.
         */
        @Override
        public void run() {
            Thread.currentThread().setName("ICE PaceMaker: " + parentAgent.getLocalUfrag());
            // time at which checking started
            checkStartTime = System.currentTimeMillis();
            // loop until interrupted or finished
            do {
                try {
                    long waitFor = getNextWaitInterval();
                    if (waitFor > 0) {
                        logger.trace("Going to sleep for {} for ufrag: {}", waitFor, parentAgent.getLocalUfrag());
                        // waitFor will be 0 for the first check since we won't have any active check lists at that point yet
                        Thread.sleep(waitFor);
                    }
                    CandidatePair pairToCheck = checkList.popTriggeredCheck();
                    // if there are no triggered checks, go for an ordinary one
                    if (pairToCheck == null) {
                        pairToCheck = checkList.getNextOrdinaryPairToCheck();
                    }
                    if (pairToCheck != null) {
                        // check for a TCP candidate with a destination port of 9 (masked) and don't attempt to connect to it!
                        RemoteCandidate remoteCandidate = pairToCheck.getRemoteCandidate();
                        if (remoteCandidate.getTcpType() == CandidateTcpType.ACTIVE && remoteCandidate.getTransportAddress().getPort() == 9) {
                            logger.debug("TCP remote candidate is active with masked port, skip attempt to connect directly. Type: {}", remoteCandidate.getType());
                            // we wont mark it failed, but we won't attempt to send, since we cannot connect to it
                            //pairToCheck.setStateFailed();
                            continue;
                        }
                        // Since we suspect that it is possible to startCheckForPair, processSuccessResponse and only then setStateInProgress, no synchronized
                        // since the CandidatePair#setState method is atomically enabled.
                        TransactionID transactionID = startCheckForPair(pairToCheck, 50, 500, 2); //100, 1600, 6
                        if (transactionID == null) {
                            logger.warn("Pair failed: {}", pairToCheck.toShortString());
                            pairToCheck.setStateFailed();
                        } else {
                            pairToCheck.setStateInProgress(transactionID);
                        }
                    } else {
                        // done sending checks for this list; set the final state in processResponse, processTimeout or processFailure method.
                        checkList.fireEndOfOrdinaryChecks();
                    }
                } catch (InterruptedException e) {
                    // message isn't all that important generally
                    logger.trace("PaceMaker got interrupted", e);
                }
                // maximum spec'd time for STUN == 3s
            } while ((System.currentTimeMillis() - checkStartTime) < checklistTimeout && parentAgent.isActive()); // exit when the agent is no longer active
            //logger.trace("exit");
        }
    }

}
