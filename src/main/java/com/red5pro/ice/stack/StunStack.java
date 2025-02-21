/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Agent;
import com.red5pro.ice.ResponseCollector;
import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunException;
import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.ErrorCodeAttribute;
import com.red5pro.ice.attribute.MessageIntegrityAttribute;
import com.red5pro.ice.attribute.OptionalAttribute;
import com.red5pro.ice.attribute.UsernameAttribute;
import com.red5pro.ice.message.Indication;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.nio.AcceptorStrategy;
import com.red5pro.ice.nio.IceHandler;
import com.red5pro.ice.nio.IceTransport;
import com.red5pro.ice.security.CredentialsManager;
import com.red5pro.ice.security.LongTermCredential;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.util.Utils;

/**
 * The entry point to the Stun4J stack. The class is used to start, stop and configure the stack.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Aakash Garg
 * @author Paul Gregoire
 */
public class StunStack implements MessageEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(StunStack.class);

    /**
     * HMAC_SHA1 instance via <pre>Mac.getInstance(MessageIntegrityAttribute.HMAC_SHA1_ALGORITHM)</pre>
     *
     * @see #StunStack()
     */
    @SuppressWarnings("unused")
    private static Mac mac;

    /**
     * The Default system AcceptorStrategy. For each Transport type utilized(TCP and UDP), AcceptorStrategy represents the three basic ways to manage IceTransports for users.
     */
    private static AcceptorStrategy acceptorStrategy;

    /**
     * Our network gateway.
     */
    private final NetAccessManager netAccessManager;

    /**
     * The {@link CredentialsManager} that we are using for retrieving passwords.
     */
    private final CredentialsManager credentialsManager = new CredentialsManager();

    /**
     * Stores active client transactions mapped against transaction id's.
     */
    private final ConcurrentMap<TransactionID, StunClientTransaction> clientTransactions = new ConcurrentHashMap<>();

    /**
     * Currently open server transactions. Contains transaction id's for transactions corresponding to all non-answered received requests.
     */
    private final ConcurrentMap<TransactionID, StunServerTransaction> serverTransactions = new ConcurrentHashMap<>();

    /**
     * A dispatcher for incoming requests event;
     */
    private final EventDispatcher eventDispatcher = new EventDispatcher();

    /**
     * Whether or not to prevent the use of IPv6 addresses.
     */
    private boolean useIPv6;

    /**
     * Whether or not to use all available IP versions.
     */
    private boolean useAllBinding;

    private static AtomicBoolean sweeper = new AtomicBoolean();

    private AcceptorStrategy sessionAcceptorStrategy = AcceptorStrategy.DiscretePerSocket;

    private ConcurrentHashSet<TransportAddress> registrations = new ConcurrentHashSet<TransportAddress>();

    /**
     * Executor for all threads and tasks needed in this stacks agent.
     */
    private static ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(String.format("StunStack@%d", System.currentTimeMillis()));
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    logger.warn("Uncaught exception on {}", t.getName(), e);
                }

            });
            return t;
        }
    });

    private long creationTime;

    /**
     * Used when strategy is DiscretePerSession.
     * This allows StunStack to look up the same IceTransport for each of the user's candidates
     */
    private String udpTransportSessionId;

    /**
     * Used when strategy is DiscretePerSession.
     * This allows StunStack to look up the same IceTransport for each of the user's candidates
     */
    private String tcpTransportSessionId;

    /**
     * Owning agent.
     */
    private WeakReference<Agent> agent;
    /**
     * True if Stack property overides.
     */
    private boolean overrides = false;

    static {
        // The Mac instantiation used in MessageIntegrityAttribute could take several hundred milliseconds so we don't
        // want it instantiated only after we get a response because the delay may cause the transaction to fail.
        try {
            mac = Mac.getInstance(MessageIntegrityAttribute.HMAC_SHA1_ALGORITHM);
        } catch (NoSuchAlgorithmException nsaex) {
            nsaex.printStackTrace();
        }

        int ordinal = StackProperties.getInt(StackProperties.ACCEPTOR_STRATEGY, AcceptorStrategy.DiscretePerSession.ordinal());
        acceptorStrategy = AcceptorStrategy.valueOf(ordinal);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // stop the executor
                if (executor != null) {
                    try {
                        List.of(executor.shutdownNow()).forEach(r -> {
                            logger.warn("Task at shutdown: {}", r);
                        });
                    } catch (Exception e) {
                        logger.warn("Exception during shutdown", e);
                    } finally {
                        executor = null;
                    }
                }
            }
        });
    }

    public StunStack() {
        logger.trace("ctor: {}", this);
        //set default on session.
        sessionAcceptorStrategy = acceptorStrategy;
        StackPropertyOverrides propOverrides = StackPropertyOverrides.getOverrides();
        if (propOverrides != null) {
            this.overrides = true;
            if (propOverrides.useAllBinding != null) {
                useAllBinding = propOverrides.useAllBinding;
            }
            if (propOverrides.useIPv6 != null) {
                useIPv6 = propOverrides.useIPv6;
            }
            if (propOverrides.strategy != null) {
                sessionAcceptorStrategy = propOverrides.strategy;
            }
        }
        // create a new network access manager
        netAccessManager = new NetAccessManager(this);
        creationTime = System.currentTimeMillis();
    }

    /**
     * Creates and starts a Network Access Point (Connector) based on the specified socket and the specified remote address.
     * Synchronized to prevent racing when using {@code AcceptorStrategy.DiscretePerSession} where we need to store the transport id for subsequent sockets added.
     *
     * @param iceSocket the socket wrapper that the new access point should represent
     * @param remoteAddress of the Connector to be created if it is a TCP socket or null if it is UDP
     * @param doBind perform bind on the wrappers local address if true and not if false
     */
    synchronized public boolean addSocket(IceSocketWrapper iceSocket, TransportAddress remoteAddress, boolean doBind) {
        logger.debug("addSocket: {} remote address: {} bind? {}", iceSocket, remoteAddress, doBind);
        boolean added = false;
        InetAddress addr = iceSocket.getLocalAddress();
        boolean isIPv6Address = addr.getHostAddress().contains(":");
        logger.info("Use IPv6: {} Use all binding: {} Is IPv6 address: {}", useIPv6, useAllBinding, isIPv6Address);
        if (!useAllBinding && (useIPv6 && !isIPv6Address)) {
            logger.debug("Skipping IPv4 address: {}", addr);
        } else if (!useAllBinding && (!useIPv6 && isIPv6Address)) {
            logger.debug("Skipping IPv6 address: {}", addr);
        } else {
            // add the wrapper for binding
            if (doBind) {
                Transport type = iceSocket.getTransport();
                IceTransport transport = null;
                if (Transport.UDP.equals(type)) {
                    //If discrete per session, we create one transport for all UDP sockets for this user.
                    if (sessionAcceptorStrategy == AcceptorStrategy.DiscretePerSession) {
                        if (udpTransportSessionId == null) {//Save the first transport ID to use for subsequent udp sockets.
                            transport = IceTransport.getInstance(type, sessionAcceptorStrategy.toString());
                            udpTransportSessionId = transport.getId();
                        } else {
                            transport = IceTransport.getInstance(type, udpTransportSessionId);
                        }
                    } else {
                        transport = IceTransport.getInstance(type, sessionAcceptorStrategy.toString());
                    }
                } else if (Transport.TCP.equals(type)) {
                    //If discrete per session, we create one transport for all TCP sockets for this user.
                    if (sessionAcceptorStrategy == AcceptorStrategy.DiscretePerSession) {
                        if (tcpTransportSessionId == null) {//Save the first transport ID to use for susequent tcp sockets.
                            transport = IceTransport.getInstance(type, sessionAcceptorStrategy.toString());
                            tcpTransportSessionId = transport.getId();
                        } else {
                            transport = IceTransport.getInstance(type, tcpTransportSessionId);
                        }
                    } else {
                        transport = IceTransport.getInstance(type, sessionAcceptorStrategy.toString());
                    }
                }
                if (transport != null) {
                    iceSocket.setIceTransportRef(transport);
                    transport.registerStackAndSocket(this, iceSocket);
                }
            } else {
                // add directly to the ice handler to prevent any unwanted binding
                IceTransport.getIceHandler().registerStackAndSocket(this, iceSocket);
            }
            // add the socket to the net access manager
            netAccessManager.buildConnectorLink(iceSocket, remoteAddress);
            added = true;
            registeredWith(iceSocket.getTransportAddress());
        }
        return added;
    }

    /**
     * Stops and deletes the connector listening on the specified local address.
     * Note this removes connectors with UDP sockets only, use {@link #removeSocket(com.red5pro.ice.TransportAddress, com.red5pro.ice.TransportAddress)}
     * with the appropriate remote address for TCP.
     *
     * @param id transport / acceptor identifier
     * @param localAddr the local address of the socket to remove
     */
    public void removeSocket(String id, TransportAddress localAddr) {
        logger.debug("removeSocket: {}", localAddr);
        removeSocket(id, localAddr, null);
    }

    /**
     * Stops and deletes the connector listening on the specified local address and remote address.
     *
     * @param id transport / acceptor identifier
     * @param localAddr the local address of the socket to remove
     * @param remoteAddr the remote address of the socket to remove; use null for UDP
     */
    public void removeSocket(String id, TransportAddress localAddr, TransportAddress remoteAddr) {
        logger.debug("removeSocket - id: {} local: {} remote: {}", id, localAddr, remoteAddr);
        // first cancel all transactions using this address
        cancelTransactionsForAddress(localAddr, remoteAddr);
        Connector connector = netAccessManager.removeConnectorLink(localAddr, remoteAddr);
        if (connector != null) {
            connector.stop();
        }
        removeRegistration(localAddr);
    }

    /**
     * Returns the transaction with the specified transactionID or null if no such transaction exists.
     *
     * @param transactionID the ID of the transaction we are looking for
     * @return the {@link StunClientTransaction} we are looking for
     */
    protected StunClientTransaction getClientTransaction(TransactionID transactionID) {
        StunClientTransaction clientTransaction = clientTransactions.get(transactionID);
        return clientTransaction;
    }

    /**
     * Returns the transaction with the specified transactionID or null if no such transaction exists.
     *
     * @param transactionID the ID of the transaction we are looking for
     * @return the {@link StunServerTransaction} we are looking for
     */
    protected StunServerTransaction getServerTransaction(TransactionID transactionID) {
        StunServerTransaction serverTransaction = serverTransactions.get(transactionID);
        // If a StunServerTransaction is expired, do not return it. It will be removed from serverTransactions soon.
        if (serverTransaction != null && serverTransaction.isExpired()) {
            // remove it from the list
            if (serverTransactions.remove(transactionID) != null) {
                logger.debug("Removing expired server transaction: {}", serverTransaction.getTransactionID());
            }
            serverTransaction = null;
        }
        return serverTransaction;
    }

    /**
     * Cancels the {@link StunClientTransaction} with the specified transactionID. Cancellation means that the stack will not
     * retransmit the request, will not treat the lack of response to be a failure, but will wait the duration of the transaction timeout for a
     * response.
     *
     * @param transactionID the {@link TransactionID} of the {@link StunClientTransaction} to cancel
     */
    public void cancelTransaction(TransactionID transactionID) {
        StunClientTransaction clientTransaction = clientTransactions.get(transactionID);
        if (clientTransaction != null) {
            if (clientTransactions.remove(transactionID) != null) {
                logger.debug("Cancelling client transaction: {}", clientTransaction.getTransactionID());
            }
            clientTransaction.cancel();
        }
    }

    /**
     * Stops all transactions for the specified localAddr so that they won't send messages through any longer and so that we could remove the
     * associated socket.
     *
     * @param localAddr the TransportAddress that we'd like to remove transactions for.
     * @param remoteAddr the remote TransportAddress that we'd like to remove transactions for. If null, then it will not be taken
     * into account (that is, all transactions with for localAddr will be cancelled).
     */
    @SuppressWarnings("unlikely-arg-type")
    private void cancelTransactionsForAddress(TransportAddress localAddr, TransportAddress remoteAddr) {
        clientTransactions.values().forEach(tran -> {
            if (tran.getLocalAddress().equals(localAddr) && (remoteAddr == null || remoteAddr.equals(tran.getRemoteAddress()))) {
                if (clientTransactions.remove(tran) != null) {
                    logger.debug("Cancelling client transaction: {}", tran.getTransactionID());
                }
                tran.cancel();
            }
        });
        serverTransactions.values().forEach(tran -> {
            TransportAddress listenAddr = tran.getLocalListeningAddress();
            TransportAddress sendingAddr = tran.getSendingAddress();
            if (listenAddr.equals(localAddr) || (sendingAddr != null && sendingAddr.equals(localAddr))) {
                if (remoteAddr == null || remoteAddr.equals(tran.getRequestSourceAddress())) {
                    if (serverTransactions.remove(tran) != null) {
                        logger.debug("Cancelling server transaction: {}", tran.getTransactionID());
                    }
                    tran.expire();
                }
            }
        });
    }

    /**
     * Returns the currently active instance of NetAccessManager.
     * @return NetAccessManager
     */
    public NetAccessManager getNetAccessManager() {
        return netAccessManager;
    }

    /**
     * Sends a specific STUN Indication to a specific destination TransportAddress through a socket registered with this
     * StunStack using a specific TransportAddress.
     *
     * @param channelData the STUN Indication to be sent to the specified destination TransportAddress through the socket with
     * the specified TransportAddress
     * @param sendTo the TransportAddress of the destination to which the specified indication is to be sent
     * @param sendThrough the TransportAddress of the socket registered with this StunStack through which the specified
     * indication is to be sent
     * @throws StunException if anything goes wrong while sending the specified indication to the destination sendTo through the socket
     * identified by sendThrough
     */
    //    public void sendChannelData(ChannelData channelData, TransportAddress sendTo, TransportAddress sendThrough) throws StunException {
    //        try {
    //            getNetAccessManager().sendMessage(channelData, sendThrough, sendTo);
    //        } catch (StunException stex) {
    //            throw stex;
    //        } catch (IllegalArgumentException iaex) {
    //            throw new StunException(StunException.ILLEGAL_ARGUMENT, "Failed to send STUN indication: " + channelData, iaex);
    //        } catch (IOException ioex) {
    //            throw new StunException(StunException.NETWORK_ERROR, "Failed to send STUN indication: " + channelData, ioex);
    //        }
    //    }

    /**
     * Sends a specific STUN Indication to a specific destination TransportAddress through a socket registered with this
     * StunStack using a specific TransportAddress.
     *
     * @param indication the STUN Indication to be sent to the specified destination TransportAddress through the socket with
     * the specified TransportAddress
     * @param sendTo the TransportAddress of the destination to which the specified indication is to be sent
     * @param sendThrough the TransportAddress of the socket registered with this StunStack through which the specified
     * indication is to be sent
     * @throws StunException if anything goes wrong while sending the specified indication to the destination sendTo through the socket
     * identified by sendThrough
     */
    public void sendIndication(Indication indication, TransportAddress sendTo, TransportAddress sendThrough) throws StunException {
        logger.debug("sendIndication {} sendTo: {} sendThrough: {}", indication, sendTo, sendThrough);
        if (indication.getTransactionID() == null) {
            indication.setTransactionID(TransactionID.createNewTransactionID().getBytes());
        }
        if (logger.isDebugEnabled()) {
            indication.getAttributes().forEach(attr -> {
                logger.debug("Attribute: {}", attr);
            });
        }
        try {
            getNetAccessManager().sendMessage(indication, sendThrough, sendTo);
        } catch (IllegalArgumentException iaex) {
            throw new StunException(StunException.ILLEGAL_ARGUMENT, "Failed to send STUN indication: " + indication, iaex);
        } catch (IOException ioex) {
            throw new StunException(StunException.NETWORK_ERROR, "Failed to send STUN indication: " + indication, ioex);
        }
    }

    /**
     * Sends the specified request through the specified access point, and registers the specified ResponseCollector for later notification.
     * @param  request     the request to send
     * @param  sendTo      the destination address of the request
     * @param  sendThrough the local address to use when sending the request
     * @param  collector   the instance to notify when a response arrives or the transaction timeouts
     * @return the TransactionID of the StunClientTransaction that we used in order to send the request
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     */
    public TransactionID sendRequest(Request request, TransportAddress sendTo, TransportAddress sendThrough, ResponseCollector collector)
            throws IOException, IllegalArgumentException {
        return sendRequest(request, sendTo, sendThrough, collector, TransactionID.createNewTransactionID());
    }

    /**
     * Sends the specified request through the specified access point, and registers the specified ResponseCollector for later notification.
     * @param  request     the request to send
     * @param  sendTo      the destination address of the request
     * @param  sendThrough the local address to use when sending the request
     * @param  collector   the instance to notify when a response arrives or the transaction timeouts
     * @param transactionID the ID that we'd like the new transaction to use in case the application created it in order to use it for application
     * data correlation
     * @return the TransactionID of the StunClientTransactionthat we used in order to send the request
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     */
    public TransactionID sendRequest(Request request, TransportAddress sendTo, TransportAddress sendThrough, ResponseCollector collector,
            TransactionID transactionID) throws IllegalArgumentException, IOException {
        return sendRequest(request, sendTo, sendThrough, collector, transactionID, -1, -1, -1);
    }

    /**
     * Sends the specified request through the specified access point, and registers the specified ResponseCollector for later notification.
     *
     * @param  request     the request to send
     * @param  sendTo      the destination address of the request
     * @param  sendThrough the local address to use when sending the request
     * @param  collector   the instance to notify when a response arrives or the transaction timeouts
     * @param transactionID the ID that we'd like the new transaction to use in case the application created it in order to use it for application
     * data correlation
     * @param originalWaitInterval The number of milliseconds to wait before the first retransmission of the request
     * @param maxWaitInterval The maximum wait interval. Once this interval is reached we should stop doubling its value
     * @param maxRetransmissions Maximum number of retransmissions. Once this number is reached and if no response is received after maxWaitInterval
     * milliseconds the request is considered unanswered
     * @return the TransactionID of the StunClientTransaction that we used in order to send the request
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     */
    public TransactionID sendRequest(Request request, TransportAddress sendTo, TransportAddress sendThrough, ResponseCollector collector,
            TransactionID transactionID, int originalWaitInterval, int maxWaitInterval, int maxRetransmissions)
            throws IllegalArgumentException, IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("sendRequest: {} to: {} thru: {}", request, sendTo, sendThrough);
            request.getAttributes().forEach(attr -> {
                logger.debug("Attribute: {}", attr);
            });
        }
        StunClientTransaction clientTransaction = new StunClientTransaction(this, request, sendTo, sendThrough, collector, transactionID);
        if (originalWaitInterval > 0) {
            clientTransaction.originalWaitInterval = originalWaitInterval;
        }
        if (maxWaitInterval > 0) {
            clientTransaction.maxWaitInterval = maxWaitInterval;
        }
        if (maxRetransmissions >= 0) {
            clientTransaction.maxRetransmissions = maxRetransmissions;
        }
        clientTransactions.put(clientTransaction.getTransactionID(), clientTransaction);
        clientTransaction.sendRequest();
        return clientTransaction.getTransactionID();
    }

    /**
     * Sends the specified response message through the specified access point.
     *
     * @param transactionID the id of the transaction to use when sending the response. Actually we are getting kind of redundant here as we already
     * have the id in the response object, but I am bringing out as an extra parameter as the user might otherwise forget to explicitly set it.
     * @param response      the message to send
     * @param sendThrough   the local address to use when sending the message
     * @param sendTo        the destination of the message
     * @throws IOException  if an error occurs while sending message bytes through the network socket.
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed,
     * @throws StunException if message encoding fails
     */
    public void sendResponse(byte[] transactionID, Response response, TransportAddress sendThrough, TransportAddress sendTo)
            throws StunException, IOException, IllegalArgumentException {
        TransactionID tid = TransactionID.createTransactionID(this, transactionID);
        StunServerTransaction sTran = getServerTransaction(tid);
        if (sTran == null) {
            throw new StunException(StunException.TRANSACTION_DOES_NOT_EXIST,
                    "The transaction specified in the response (tid=" + tid.toString() + ") object does not exist.");
        } else if (sTran.isRetransmitting()) {
            throw new StunException(StunException.TRANSACTION_ALREADY_ANSWERED, "The transaction specified in the response (tid="
                    + tid.toString() + ") has already seen a previous response. Response was:\n" + sTran.getResponse());
        } else {
            sTran.sendResponse(response, sendThrough, sendTo);
        }
    }

    /**
     * Adds a new MessageEventHandler which is to be notified about STUN indications received at a specific local TransportAddress.
     *
     * @param localAddr the TransportAddress of the local socket for which received STUN indications are to be reported to the specified
     * MessageEventHandler
     * @param indicationListener the MessageEventHandler which is to be registered for notifications about STUN indications received at the
     * specified local TransportAddress
     */
    public void addIndicationListener(TransportAddress localAddr, MessageEventHandler indicationListener) {
        logger.info("addIndicationListener - {} listener: {}", localAddr, indicationListener);
        eventDispatcher.addIndicationListener(localAddr, indicationListener);
    }

    /**
     * Adds a new MessageEventHandler which is to be notified about old indications received at a specific local TransportAddress.
     *
     * @param localAddr the TransportAddress of the local socket for which received STUN indications are to be reported to the specified
     * MessageEventHandler
     * @param indicationListener the MessageEventHandler which is to be registered for notifications about old indications received at the
     * specified local TransportAddress
     */
    public void addOldIndicationListener(TransportAddress localAddr, MessageEventHandler indicationListener) {
        logger.info("addOldIndicationListener - {} listener: {}", localAddr, indicationListener);
        eventDispatcher.addOldIndicationListener(localAddr, indicationListener);
    }

    /**
     * Sets the listener that should be notified when a new Request is received.
     *
     * @param requestListener the listener interested in incoming requests
     */
    public void addRequestListener(RequestListener requestListener) {
        eventDispatcher.addRequestListener(requestListener);
    }

    /**
     * Removes an existing MessageEventHandler to no longer be notified about STUN indications received at a specific local TransportAddress.
     *
     * @param localAddr the TransportAddress of the local socket for which received STUN indications are to no longer be reported to the
     * specified MessageEventHandler
     * @param indicationListener the MessageEventHandler which is to be unregistered for notifications about STUN indications received at the
     * specified local TransportAddress
     */
    public void removeIndicationListener(TransportAddress localAddr, MessageEventHandler indicationListener) {
        logger.info("removeIndicationListener - {} listener: {}", localAddr, indicationListener);
    }

    /**
     * Removes the specified listener from the local listener list. (If any instances of this listener have been registered for a particular
     * access point, they will not be removed).
     * @param listener the RequestListener listener to unregister
     */
    public void removeRequestListener(RequestListener listener) {
        eventDispatcher.removeRequestListener(listener);
    }

    /**
     * Add a RequestListener for requests coming from a specific NetAccessPoint. The listener will be invoked only when a request event is received on
     * that specific property.
     *
     * @param localAddress The local TransportAddress that we would like to listen on
     * @param listener The ConfigurationChangeListener to be added
     */
    public void addRequestListener(TransportAddress localAddress, RequestListener listener) {
        eventDispatcher.addRequestListener(localAddress, listener);
    }

    /**
     * Removes a client transaction from this providers client transactions list. The method is used by StunClientTransactions themselves
     * when a timeout occurs.
     *
     * @param tran the transaction to remove
     */
    void removeClientTransaction(StunClientTransaction tran) {
        clientTransactions.remove(tran.getTransactionID());
    }

    /**
     * Removes a server transaction from this provider's server transactions list. Method is used by StunServerTransaction-s themselves when they expire.
     * @param tran the transaction to remove
     */
    void removeServerTransaction(StunServerTransaction tran) {
        serverTransactions.remove(tran.getTransactionID());
    }

    /**
     * Called to notify this provider for an incoming message.
     *
     * @param ev the event object that contains the new message.
     */
    @Override
    public void handleMessageEvent(StunMessageEvent ev) {
        Message msg = ev.getMessage();
        if (logger.isTraceEnabled()) {
            logger.trace("Received a message on {} of type: {}", ev.getLocalAddress(), msg.getName());
        }
        if (msg instanceof Request) {
            logger.trace("Parsing request");
            // skip badly sized requests
            UsernameAttribute ua = (UsernameAttribute) msg.getAttribute(Attribute.Type.USERNAME);
            if (ua != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Username: {} length: {} data length: {}", ua.getUsername(), ua.getUsername().length, ua.getDataLength());
                }
                byte[] username = ua.getUsername();
                if (username[username.length - 1] == 0) {
                    logger.warn("Invalid username (null terminated), rejecting request");
                    return;
                }
                if (username.length != ua.getDataLength()) {
                    logger.warn("Invalid username size, rejecting request");
                    return;
                }
            } else {
                logger.debug("Username was null");
            }
            TransactionID serverTid = ev.getTransactionID();
            logger.debug("Event server transaction id: {}", serverTid.toString());
            StunServerTransaction sTran = getServerTransaction(serverTid);
            if (sTran != null) {
                //logger.warn("Stored server transaction id: {}", sTran.getTransactionID().toString());
                //requests from this transaction have already been seen retransmit the response if there was any
                logger.trace("Found an existing transaction");
                try {
                    sTran.retransmitResponse();
                    logger.debug("Response retransmitted");
                } catch (Exception ex) {
                    //we couldn't really do anything here .. apart from logging
                    logger.warn("Failed to retransmit a stun response", ex);
                }
                if (!StackProperties.getBoolean(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, false)) {
                    return;
                }
            } else {
                logger.trace("Creating new server transaction for: {}. Local: {} remote: {}", serverTid, ev.getLocalAddress(),
                        ev.getRemoteAddress());
                sTran = new StunServerTransaction(this, serverTid, ev.getLocalAddress(), ev.getRemoteAddress());
                // if there is an OOM error here, it will lead to NetAccessManager.handleFatalError that will stop the
                // MessageProcessor thread and restart it that will lead again to an OOM error and so on... So stop here right now
                try {
                    sTran.start();
                } catch (OutOfMemoryError t) {
                    logger.warn("STUN transaction thread start failed", t);
                    return;
                }
                serverTransactions.put(serverTid, sTran);
                maybeStartServerTransactionExpireThread();
            }
            // validate attributes that need validation
            try {
                validateRequestAttributes(ev);
            } catch (Exception exc) {
                // validation failed
                logger.warn("Failed to validate msg: {}", ev, exc);
                // remove failed transaction to account for Edge
                removeServerTransaction(sTran);
                return;
            }
            try {
                eventDispatcher.fireMessageEvent(ev);
            } catch (Throwable t) {
                logger.warn("Received an invalid request", t);
                Throwable cause = t.getCause();
                if (((t instanceof StunException) && ((StunException) t).getID() == StunException.TRANSACTION_ALREADY_ANSWERED)
                        || ((cause instanceof StunException)
                                && ((StunException) cause).getID() == StunException.TRANSACTION_ALREADY_ANSWERED)) {
                    // do not try to send an error response since we will
                    // get another TRANSACTION_ALREADY_ANSWERED
                    return;
                }
                Response error;
                if (t instanceof IllegalArgumentException) {
                    error = createCorrespondingErrorResponse(msg.getMessageType(), ErrorCodeAttribute.BAD_REQUEST, t.getMessage());
                } else {
                    error = createCorrespondingErrorResponse(msg.getMessageType(), ErrorCodeAttribute.SERVER_ERROR,
                            "Oops! Something went wrong on our side :(");
                }
                try {
                    sendResponse(serverTid.getBytes(), error, ev.getLocalAddress(), ev.getRemoteAddress());
                } catch (Exception exc) {
                    logger.warn("Couldn't send a server error response", exc);
                }
            }
        } else if (msg instanceof Response) {
            logger.trace("Parsing response");
            TransactionID tid = ev.getTransactionID();
            // skip badly sized requests
            UsernameAttribute ua = (UsernameAttribute) msg.getAttribute(Attribute.Type.USERNAME);
            if (ua != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Username: {}", ua.getUsername());
                }
            }
            StunClientTransaction tran = clientTransactions.remove(tid);
            if (tran != null) {
                tran.handleResponse(ev);
            } else {
                //do nothing - just drop the phantom response.
                logger.debug("Dropped response - no matching client tran found for tid {}\nall tids in stock were {}", tid,
                        clientTransactions.keySet());
            }
        } else if (msg instanceof Indication) {
            eventDispatcher.fireMessageEvent(ev);
        }
    }

    /**
     * Used to set preference for IPv6 addresses over IPv4.
     *
     * @param useIPv6 if true IPv6 addresses will be preferred over IPv4
     */
    public void useIPv6Binding(boolean useIPv6) {
        this.useIPv6 = useIPv6;
    }

    /**
     * Used to ensure both IP versions are allowed.
     *
     * @param useAllBinding if true both IPv6 and IPv4 addresses will be allowed
     */
    public void useAllBinding(Boolean useAllBinding) {
        this.useAllBinding = useAllBinding;
    }

    /**
     * Returns the {@link CredentialsManager} that this stack is using for verification of {@link MessageIntegrityAttribute}s.
     *
     * @return the CredentialsManager that this stack is using for verification of MessageIntegrityAttributes
     */
    public CredentialsManager getCredentialsManager() {
        return credentialsManager;
    }

    /**
     * Cancels all running transactions and prepares for garbage collection.
     */
    public void shutDown() {
        logger.debug("Shutting down");
        // remove all listeners
        eventDispatcher.removeAllListeners();
        // clientTransactions
        clientTransactions.keySet().forEach(id -> {
            StunClientTransaction tran = clientTransactions.remove(id);
            if (tran != null) {
                tran.cancel();
            }
        });
        // serverTransactions
        serverTransactions.keySet().forEach(id -> {
            StunServerTransaction tran = serverTransactions.remove(id);
            if (tran != null) {
                tran.expire();
            }
        });

        // clear the collections
        clientTransactions.clear();
        serverTransactions.clear();
        netAccessManager.stop();
    }

    /**
     * Executes actions related specific attributes like asserting proper checksums or verifying the validity of user names.
     *
     * @param evt the {@link StunMessageEvent} that contains the {@link Request} that we need to validate.
     *
     * @throws IllegalArgumentException if there's something in the attribute that caused us to discard the whole message (e.g. an
     * invalid checksum or username)
     * @throws StunException if we fail while sending an error response
     * @throws IOException if we fail while sending an error response
     */
    private void validateRequestAttributes(StunMessageEvent evt) throws IllegalArgumentException, StunException, IOException {
        Message request = evt.getMessage();
        //assert valid username
        UsernameAttribute unameAttr = (UsernameAttribute) request.getAttribute(Attribute.Type.USERNAME);
        String username = null;
        if (unameAttr != null) {
            username = LongTermCredential.toString(unameAttr.getUsername());
            if (!validateUsername(username)) {
                Response error = createCorrespondingErrorResponse(request.getMessageType(), ErrorCodeAttribute.UNAUTHORIZED,
                        "unknown user " + username);
                sendResponse(request.getTransactionID(), error, evt.getLocalAddress(), evt.getRemoteAddress());
                throw new IllegalArgumentException("Non-recognized username: " + username);
            }
        }
        boolean messageIntegrityRequired = StackProperties.getBoolean(StackProperties.REQUIRE_MESSAGE_INTEGRITY, false);
        //assert Message Integrity
        MessageIntegrityAttribute msgIntAttr = (MessageIntegrityAttribute) request.getAttribute(Attribute.Type.MESSAGE_INTEGRITY);
        if (msgIntAttr != null) {
            //we should complain if we have msg integrity and no username.
            if (unameAttr == null) {
                Response error = createCorrespondingErrorResponse(request.getMessageType(), ErrorCodeAttribute.BAD_REQUEST,
                        "missing username");
                sendResponse(request.getTransactionID(), error, evt.getLocalAddress(), evt.getRemoteAddress());
                throw new IllegalArgumentException("Missing USERNAME in the presence of MESSAGE-INTEGRITY: ");
            }
            if (!validateMessageIntegrity(msgIntAttr, username, true, evt.getRawMessage())) {
                Response error = createCorrespondingErrorResponse(request.getMessageType(), ErrorCodeAttribute.UNAUTHORIZED,
                        "Wrong MESSAGE-INTEGRITY value");
                sendResponse(request.getTransactionID(), error, evt.getLocalAddress(), evt.getRemoteAddress());
                throw new IllegalArgumentException("Wrong MESSAGE-INTEGRITY value");
            }
        } else if (messageIntegrityRequired) {
            // no message integrity
            Response error = createCorrespondingErrorResponse(request.getMessageType(), ErrorCodeAttribute.UNAUTHORIZED,
                    "Missing MESSAGE-INTEGRITY.");
            sendResponse(request.getTransactionID(), error, evt.getLocalAddress(), evt.getRemoteAddress());
            throw new IllegalArgumentException("Missing MESSAGE-INTEGRITY");
        }
        //look for unknown attributes.
        List<Attribute> allAttributes = request.getAttributes();
        StringBuilder sBuff = new StringBuilder();
        for (Attribute attr : allAttributes) {
            if (attr instanceof OptionalAttribute
                    && attr.getAttributeType().getType() < Attribute.Type.UNKNOWN_OPTIONAL_ATTRIBUTE.getType()) {
                sBuff.append(attr.getAttributeType());
            }
        }
        if (sBuff.length() > 0) {
            Response error = createCorrespondingErrorResponse(request.getMessageType(), ErrorCodeAttribute.UNKNOWN_ATTRIBUTE,
                    "unknown attribute ", sBuff.toString().toCharArray());
            sendResponse(request.getTransactionID(), error, evt.getLocalAddress(), evt.getRemoteAddress());
            throw new IllegalArgumentException("Unknown attribute(s)");
        }
    }

    /**
     * Recalculates the HMAC-SHA1 signature of the message array so that we could compare it with the value brought by the
     * {@link MessageIntegrityAttribute}.
     *
     * @param msgInt the attribute that we need to validate
     * @param username the user name that the message integrity checksum is supposed to have been built for
     * @param shortTermCredentialMechanism true if msgInt is to be validated as part of the STUN short-term credential mechanism or
     * false for the STUN long-term credential mechanism
     * @param message the message whose SHA1 checksum we'd need to recalculate
     * @return true if msgInt contains a valid SHA1 value and false otherwise
     */
    public boolean validateMessageIntegrity(MessageIntegrityAttribute msgInt, String username, boolean shortTermCredentialMechanism,
            RawMessage message) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    "validateMessageIntegrity username: {} short term: {}\nMI attr data length: {} hmac content: {}\nRawMessage: {}\n{}",
                    username, shortTermCredentialMechanism, msgInt.getDataLength(), Utils.toHexString(msgInt.getHmacSha1Content()),
                    message.getMessageLength(), Utils.toHexString(message.getBytes()));
        }
        if (username == null || username.length() < 1 || (shortTermCredentialMechanism && !username.contains(":"))) {
            logger.debug("Received a message with an improperly formatted username");
            return false;
        }
        String[] usernameParts = username.split(":");
        if (shortTermCredentialMechanism) {
            username = usernameParts[0]; // lfrag
        }
        byte[] key = getCredentialsManager().getLocalKey(username);
        if (key == null) {
            logger.warn("Local key was not found for {}", username);
            return false;
        }
        if (logger.isTraceEnabled() && shortTermCredentialMechanism) {
            // no username[1] with long term creds
            logger.trace("Local key: {} remote key: {}", Utils.toHexString(key),
                    Utils.toHexString(getCredentialsManager().getRemoteKey(usernameParts[1], "media-0")));
        }
        /*
         * Now check whether the SHA1 matches. Using MessageIntegrityAttribute.calculateHmacSha1 on the bytes of the RawMessage will be incorrect if there are other Attributes
         * after the MessageIntegrityAttribute because the value of the MessageIntegrityAttribute is calculated on a STUN "Message Length" up to and including the MESSAGE-INTEGRITY
         * and excluding any Attributes after it.
         */
        byte[] binMsg = new byte[msgInt.getLocationInMessage()];
        System.arraycopy(message.getBytes(), 0, binMsg, 0, binMsg.length);
        int messageLength = (binMsg.length + Attribute.HEADER_LENGTH + msgInt.getDataLength() - Message.HEADER_LENGTH);
        binMsg[2] = (byte) (messageLength >> 8);
        binMsg[3] = (byte) (messageLength & 0xFF);
        byte[] expectedMsgIntHmacSha1Content;
        try {
            expectedMsgIntHmacSha1Content = MessageIntegrityAttribute.calculateHmacSha1(binMsg, 0, binMsg.length, key);
        } catch (IllegalArgumentException iaex) {
            expectedMsgIntHmacSha1Content = null;
        }
        byte[] msgIntHmacSha1Content = msgInt.getHmacSha1Content();
        if (!Arrays.equals(expectedMsgIntHmacSha1Content, msgIntHmacSha1Content)) {
            logger.warn("Received a message with a wrong MESSAGE-INTEGRITY signature expected:\n{}\nreceived:\n{}",
                    Utils.toHexString(expectedMsgIntHmacSha1Content), Utils.toHexString(msgIntHmacSha1Content));
            return false;
        }
        logger.trace("Successfully verified msg integrity");
        return true;
    }

    /**
     * Asserts the validity of a specific username (e.g. which we've received in a USERNAME attribute).
     *
     * @param username the username to be validated
     * @return true if username contains a valid username; false, otherwise
     */
    private boolean validateUsername(String username) {
        int colon = username.indexOf(":");
        if (username.length() < 1 || colon < 1) {
            if (logger.isDebugEnabled()) {
                logger.debug("Received a message with an improperly formatted username");
            }
            return false;
        }
        String lfrag = username.substring(0, colon);
        return getCredentialsManager().checkLocalUserName(lfrag);
    }

    /**
     * Initializes and starts {@link #serverTransactionExpireThread} if necessary.
     */
    private static void maybeStartServerTransactionExpireThread() {

        if (sweeper.compareAndSet(false, true)) {
            if (!executor.isShutdown()) {
                executor.submit(() -> {
                    // Expires the StunServerTransactions of this StunStack and removes them from {@link #serverTransactions}
                    final String oldName = Thread.currentThread().getName();
                    Thread.currentThread().setName("StunStack.txExpireThread");
                    logger.info("Stun stack sweeper starting.");
                    try {
                        do {
                            Map<TransportAddress, StunStack> stacks = IceHandler.getStunStacks();
                            try {
                                logger.debug("Going to sleep for {}s before cleaning up server txns", stacks.size(),
                                        (StunServerTransaction.LIFETIME / 1000L));
                                Thread.sleep(StunServerTransaction.LIFETIME);
                            } catch (InterruptedException ie) {
                                logger.debug("Interrupted while waiting for server txns to expire", ie);
                                break;
                            }
                            if (stacks.isEmpty()) {
                                break;
                            }
                            stacks.forEach((address, stack) -> {
                                Thread.currentThread().setName("StunStack.txExpire:" + address.getTransport().toString() + "-"
                                        + String.valueOf(address.getPort()));
                                logger.debug("Sweeping");
                                stack.runCleanup();
                            });

                        } while (!executor.isShutdown());

                    } finally {
                        sweeper.set(false);
                        logger.info("Stun stack sweeper exiting.");
                        Thread.currentThread().setName(oldName);
                    }
                });
            }
        }
    }

    private void runCleanup() {
        serverTransactions.values().forEach(serverTransaction -> {
            if (serverTransaction.isExpired()) {
                StunServerTransaction tx = serverTransactions.remove(serverTransaction.getTransactionID());
                if (tx != null) {
                    logger.debug("Expired server transaction: {}", tx.getTransactionID());
                }
            }
        });
    }

    /**
     * Returns the Error Response object with specified errorCode and reasonPhrase corresponding to input type.
     *
     * @param requestType the message type of Request
     * @param errorCode the errorCode for Error Response object
     * @param reasonPhrase the reasonPhrase for the Error Response object
     * @param unknownAttributes char[] array containing the ids of one or more attributes that had not been recognized
     * @return corresponding Error Response object
     */
    public Response createCorrespondingErrorResponse(char requestType, char errorCode, String reasonPhrase, char... unknownAttributes) {
        if (requestType == Message.BINDING_REQUEST) {
            if (unknownAttributes != null) {
                return MessageFactory.createBindingErrorResponse(errorCode, reasonPhrase, unknownAttributes);
            } else {
                return MessageFactory.createBindingErrorResponse(errorCode, reasonPhrase);
            }
        } else {
            return null;
        }
    }

    /**
     * Submit a task to the internal executor.
     *
     * @param task
     * @return Future
     */
    public Future<?> submit(Runnable task) {
        if (executor != null && !executor.isTerminated()) {
            logger.debug("Submitting task: {}", task);
            return executor.submit(task);
        }
        logger.warn("Submission rejected, executor is terminated");
        return null;
    }

    public String toString() {

        StringBuilder b = new StringBuilder();

        long age = System.currentTimeMillis() - creationTime;
        b.append("StunStack ");
        b.append("age: ").append(age);

        registrations.forEach(addy -> {
            AtomicBoolean progress = new AtomicBoolean();
            b.append(", socket:[ ").append(addy.toString());
            try {
                IceSocketWrapper socket = IceTransport.getIceHandler().lookupBinding(addy);
                if (socket != null) {
                    b.append(" id: ").append(socket.getTransportId());
                    b.append(", session:[ ");
                    progress.set(true);
                    IoSession sess = socket.getSession();
                    if (sess != null) {
                        b.append(sess.getId());
                        b.append(", connected: ").append(sess.isConnected());
                        b.append(", read-bps: ").append(sess.getReadBytesThroughput() * 8);
                        b.append(", write-bps: ").append(sess.getWrittenBytesThroughput() * 8);
                    } else {
                        b.append("null");
                    }

                }

            } catch (Throwable t) {
                logger.error("", t);
                b.append(" Error reading stunstack: ");
                b.append(t);
            } finally {
                if (progress.get()) {
                    b.append("]");
                }
                b.append("]");
            }
        });


        return b.toString();
    }

    private void registeredWith(TransportAddress addr) {
        if (registrations.add(addr)) {
            logger.debug("Registered with {} ", addr);
        } else {
            logger.debug("Already registered with {} ", addr);
        }
    }

    private boolean removeRegistration(TransportAddress addr) {
        return registrations.remove(addr);
    }

    public int getRegistrationCount() {
        return registrations.size();
    }

    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Returns agent id or null if agent was garbage collected.
     * @return
     */
    public String getAgentId() {
        Agent ref = agent.get();
        return ref == null ? null : ref.getId();
    }

    public AcceptorStrategy getSessionAcceptorStrategy() {
        return sessionAcceptorStrategy;
    }

    public void setSessionAcceptorStrategy(AcceptorStrategy strategy) {
        this.sessionAcceptorStrategy = strategy;
    }

    public static AcceptorStrategy getDefaultAcceptorStrategy() {
        return acceptorStrategy;
    }

    public static void setDefaultAcceptorStrategy(AcceptorStrategy strategy) {
        acceptorStrategy = strategy;
    }

    public void setAgent(Agent agent) {
        this.agent = new WeakReference<Agent>(agent);
    }

    /**
     * True if using stack property overrides.
     * @return
     */
    public boolean hasOverrides() {
        return overrides;
    }
}
