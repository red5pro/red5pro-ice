package com.red5pro.ice.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionRecycler;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.nio.IceDatagramAcceptor;

import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.socket.IceUdpSocketWrapper;
import com.red5pro.ice.stack.StunStack;

/**
 * IceTransport for UDP connections.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public class IceUdpTransport extends IceTransport {
    /**
     * Recycler's session map.
     */
    private ConcurrentMap<String, IoSession> sessions = new ConcurrentHashMap<>();

    // track sequence to ensure we don't nack those that are too old
    private ConcurrentLinkedDeque<ExpirableAddressEntry> recentBindingsQueue = new ConcurrentLinkedDeque<>();

    private IoSessionRecycler recycler = new IoSessionRecycler() {

        @Override
        public void put(IoSession session) {
            logger.trace("Try adding session to recycler: {}", session);
            String key = generateKey(session);
            // to allow binding/storage or not
            boolean allowUse = true;
            // check for recent binding removals
            for (ExpirableAddressEntry expEntry : recentBindingsQueue) {
                // remove entries when they've expired as we roll through
                if (expEntry.isExpired()) {
                    // these also won't count as a match
                    recentBindingsQueue.remove(expEntry);
                    // continue on...
                    continue;
                }
                if (key.equals(expEntry.getAddressKey())) {
                    // we've match a recently unbound address, don't allow re-use quite so quickly
                    allowUse = false;
                    break;
                }
            }
            if (allowUse) {
                sessions.put(key, session);
                if (isTrace) {
                    logger.trace("Added session: {} {} to recycler", session.getId(), key);
                }
            } else {
                logger.debug("Failed to add session: {} {} to recycler, too soon", session.getId(), key);
            }
        }

        @Override
        public IoSession recycle(SocketAddress remoteAddress) {
            logger.trace("Recycle remote address: {}", remoteAddress);
            // recycler is locked by NioDatagramAcceptor.newSessionWithoutLock so we'll attempt to prevent deadlocking
            // by using our concurrent map from outside the recycler itself
            return getSessionByRemote(remoteAddress);
        }

        @Override
        public void remove(IoSession session) {
            logger.debug("Removing session from recycler: {}", session);
            String key = generateKey(session);
            // remove by key
            if (sessions.remove(key) != null) {
                // make an entry for the key we're removed
                recentBindingsQueue.add(new ExpirableAddressEntry(key));
                if (isTrace) {
                    logger.trace("Removed session: {} {} from recycler", session.getId(), key);
                }
            }
        }

        public String generateKey(IoSession session) {
            return String.format("%s@%s", session.getLocalAddress(), session.getRemoteAddress().toString());
        }

    };

    /**
     * Creates the i/o handler and nio acceptor; ports and addresses are bound.
     */
    IceUdpTransport() {
        logger.info("Creating Transport. id: {} strategy: {} accept timeout: {}s idle timeout: {}s", id,
                StunStack.getDefaultAcceptorStrategy().toString(), acceptorTimeout, timeout);
        // add ourself to the transports map
        transports.put(id, this);
    }

    /**
     * Returns an instance of this transport.
     *
     * @param id transport / acceptor identifier
     * @return IceTransport
     */
    public static IceUdpTransport getInstance(String id) {
        IceUdpTransport instance = null;
        boolean isShared = false;
        boolean isNew = false;
        if (AcceptorStrategy.isNew(id)) {
            isNew = true;
        } else if (AcceptorStrategy.isShared(id)) {
            isShared = true;
        } else {
            IceTransport it = transports.get(id);
            // We may have been called when the caller did not know the type via IceTransport#getInstance.
            if (thisIsSomethingButNot(it)) {
                return null;//must be UDP
            } else {
                instance = (IceUdpTransport) it;
            }
        }

        // an id of "disconnected" is a special case where the socket is not associated with an IoSession
        if (isNew || isShared) {
            if (isShared) {
                // loop through transport and if none are found for UDP, create a new one
                for (Entry<String, IceTransport> entry : transports.entrySet()) {
                    if (entry.getValue() instanceof IceUdpTransport && entry.getValue().isShared()) {
                        instance = (IceUdpTransport) entry.getValue();
                        break;
                    }
                }
                if (instance == null) {
                    instance = new IceUdpTransport();
                }
            } else {
                instance = new IceUdpTransport();
            }
        }
        // create an acceptor if none exists for the instance
        if (instance != null && instance.getAcceptor() == null) {
            if (isNew || isShared) {
                instance.setAcceptorStrategy(AcceptorStrategy.valueOf(id));
            }
            instance.createAcceptor();
        }
        //logger.trace("Instance: {}", instance);
        return instance;
    }

    private void createAcceptor() {
        if (acceptor == null) {
            // create the nio acceptor
            //acceptor = new NioDatagramAcceptor(); // mina base acceptor
            if (!sharedIoProcessor) {
                acceptor = new IceDatagramAcceptor();
            } else {
                acceptor = new IceDatagramAcceptor(ioExecutor);//Shared cached thread pool
            }
            acceptor.addListener(new IoServiceListener() {

                @Override
                public void serviceActivated(IoService service) throws Exception {
                    //logger.debug("serviceActivated: {}", service);
                }

                @Override
                public void serviceIdle(IoService service, IdleStatus idleStatus) throws Exception {
                    //logger.debug("serviceIdle: {} status: {}", service, idleStatus);
                }

                @Override
                public void serviceDeactivated(IoService service) throws Exception {
                    //logger.debug("serviceDeactivated: {}", service);
                }

                @Override
                public void sessionCreated(IoSession session) throws Exception {
                    logger.debug("Acceptor sessionCreated: {} for ice-udp-transport id: {}", session, id);
                    if (logger.isTraceEnabled()) {
                        logger.debug("acceptor sessions: {}", acceptor.getManagedSessions());
                    }
                    session.setAttribute(IceTransport.Ice.UUID, id);
                }

                @Override
                public void sessionClosed(IoSession session) throws Exception {
                    logger.debug("Acceptor sessionClosed: {}", session);
                    /*
                    if (session.containsAttribute(Ice.CONNECTION)) {
                        IceSocketWrapper wrapper = (IceSocketWrapper) session.getAttribute(Ice.CONNECTION);
                        logger.warn("Wrapper closed: {} id: {}", wrapper.isClosed(), wrapper.getId());
                        if (IceSocketWrapper.DISCONNECTED.equals(wrapper.getId()) && !wrapper.isClosed()) {
                            wrapper.close();
                        }
                    }
                    */
                }

                @Override
                public void sessionDestroyed(IoSession session) throws Exception {
                    logger.debug("sessionDestroyed: {}", session);
                    if (session.containsAttribute(IceTransport.Ice.UUID)) {
                        session.removeAttribute(IceTransport.Ice.UUID);
                    }
                }
            });
            // set the recycler
            ((IceDatagramAcceptor) acceptor).setSessionRecycler(recycler);
            // configure the acceptor
            DatagramSessionConfig sessionConf = ((IceDatagramAcceptor) acceptor).getSessionConfig();
            sessionConf.setReuseAddress(true);
            sessionConf.setSendBufferSize(Math.max(BUFFER_SIZE_DEFAULT, sendBufferSize));
            // buffer size of -1 is not permitted, so ensure its at least 65535
            sessionConf.setReadBufferSize(Math.max(BUFFER_SIZE_DEFAULT, receiveBufferSize));
            sessionConf.setCloseOnPortUnreachable(true);
            // set an idle time in seconds or disable this via -1
            if (timeout > 0) {
                sessionConf.setIdleTime(IdleStatus.BOTH_IDLE, timeout);
            }
            // QoS
            sessionConf.setTrafficClass(trafficClass);
            // in server apps this can cause a memory leak so its off
            sessionConf.setUseReadOperation(false);
            // close sessions when the acceptor is stopped
            acceptor.setCloseOnDeactivation(true);
            // get the filter chain and add our codec factory
            acceptor.getFilterChain().addLast("protocol", iceCodecFilter);
            // add our handler
            acceptor.setHandler(iceHandler);
            logger.debug("Started socket transport id: {}", id);
            if (isTrace) {
                logger.trace("Acceptor sizes - send: {} recv: {}", sessionConf.getSendBufferSize(), sessionConf.getReadBufferSize());
            }

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long addBinding(String socketUUID, InetSocketAddress addr) {
        try {
            acceptorUtilized = true;
            if (myBoundAddresses.add(addr)) {
                logger.debug("Adding UDP binding: {}", addr);
                acceptor.bind(addr);
                logger.debug("UDP Bound: {}", addr);
                // add the port to the bound list
                Long rsvp = cacheBoundAddressInfo(socketUUID, addr, addr.getPort());
                if (rsvp != null) {
                    logger.debug("UDP binding added: {}", addr);
                } else {
                    logger.debug("UDP binding already added: {}", addr);
                }
                // no exceptions? return true for adding the binding
                return rsvp;
            }
        } catch (Throwable t) {
            logger.warn("Add binding failed on {}", addr, t);

        } finally {
            if (!acceptor.getLocalAddresses().contains(addr)) {
                logger.warn("Failed to bind. Removing ref {}", addr);
                myBoundAddresses.remove(addr);
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    public boolean registerStackAndSocket(StunStack stunStack, IceSocketWrapper iceSocket) {
        logger.debug("registerStackAndSocket - stunStack: {} iceSocket: {}", stunStack, iceSocket);

        // Setting ID here because our 'IoServiceListener' session-created may be called AFTER ice handler is called to send resopnse.
        // Cant find any reason we should not set id here.
        iceSocket.setTransportId(id);
        // add the stack and wrapper to a map which will hold them until an associated session is opened
        // when opened, the stack and wrapper will be added to the session as attributes
        iceHandler.registerStackAndSocket(stunStack, iceSocket);
        // get the local address
        TransportAddress localAddress = iceSocket.getTransportAddress();
        // attempt to add a binding to the server
        Long rsvp = addBinding(iceSocket.getId(), localAddress);
        iceSocket.setRsvp(rsvp);
        return rsvp != null;
    }

    /**
     * Create a new IoSession for the given IceSocketWrapper and remote address.
     *
     * @param socketWrapper
     * @param destAddress remote address
     * @return IoSession or null if creation fails
     */
    public IoSession createSession(IceUdpSocketWrapper socketWrapper, SocketAddress destAddress) {
        logger.debug("createSession tid: {} - wrapper: {} remote: {}", id, socketWrapper, destAddress);
        IoSession session = null;
        if (acceptor != null) {
            // get the local address
            TransportAddress transportAddress = socketWrapper.getTransportAddress();
            // newSession calls recycler.recycle(destAddress)
            session = acceptor.newSession(destAddress, transportAddress);
            if (session != null) {
                // set the session directly
                socketWrapper.setSession(session);
            }
        } else {
            logger.debug("No UDP acceptor available");
        }
        return session;
    }

    /**
     * Returns the first session matching the given local address and port.
     *
     * @param localAddress
     * @return IoSession if match is found and null if not found
     */
    public IoSession getSessionByLocal(TransportAddress localAddress) {
        if (isDebug) {
            logger.debug("Session values: {}", sessions.values());
        }
        for (IoSession sess : sessions.values()) {
            if (sess.getLocalAddress().equals(localAddress)) {
                logger.debug("Found match for {} = {}", localAddress, sess);
                return sess;
            }
        }
        return null;
    }

    /**
     * Returns a session for the requested remote address.
     *
     * @param remoteAddress
     * @return IoSession matching remote address or null if its not found
     */
    public IoSession getSessionByRemote(SocketAddress remoteAddress) {
        IoSession sess = null;
        // this is expected to return an existing session for the remote address
        Optional<IoSession> opt = sessions.values().stream().filter(session -> session.getRemoteAddress().equals(remoteAddress))
                .findFirst();
        if (opt.isPresent()) {
            sess = opt.get();
            return sess;
        } else {
            if (isTrace) {
                logger.trace("Session not found in recycler for remote address: {}\n{}", remoteAddress, sessions.keySet());
            }
        }
        return sess;
    }

    @Override
    public Transport getTransport() {
        return Transport.UDP;
    }

    /**
     * Returns true if 'it' is not null and is not an IceUdpTransport
     * @param it extension of IceTransport or null
     * @return boolean true if parameter is not null and is not an IceUdpTransport
     */
    private static boolean thisIsSomethingButNot(IceTransport it) {
        if (it != null) {
            return !IceUdpTransport.class.isInstance(it);
        }
        return false;
    }

    @Override
    public Transport getType() {
        return Transport.UDP;
    }
}
