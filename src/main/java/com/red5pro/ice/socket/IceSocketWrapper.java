/* See LICENSE.md for license information */
package com.red5pro.ice.socket;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Agent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.nio.IceTransport;
import com.red5pro.ice.nio.IceTransport.Ice;
import com.red5pro.ice.nio.IceUdpTransport;
import com.red5pro.ice.stack.RawMessage;
import com.red5pro.ice.stack.StunStack;

/**
 * Parent socket wrapper that define a socket that could be UDP, TCP...
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public abstract class IceSocketWrapper implements Comparable<IceSocketWrapper> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final boolean isTrace = logger.isTraceEnabled();

    protected final boolean isDebug = logger.isDebugEnabled();

    public final static IoSession NULL_SESSION = new DummySession();
    /**
     * Mapping of sockets by socket UUID.
     */
    protected static ConcurrentMap<String, IceSocketWrapper> iceSockets = new ConcurrentHashMap<>();

    protected final String id = UUID.randomUUID().toString();

    // acceptor id for this socket
    protected String transportId = StunStack.getDefaultAcceptorStrategy().toString();

    // whether or not we've been closed
    public AtomicBoolean closed = new AtomicBoolean(false);

    protected final TransportAddress transportAddress;

    private AtomicReference<TransportAddress> remoteTransportAddress = new AtomicReference<>();

    protected CopyOnWriteArrayList<TransportAddress> negotiatingRemoteAddresses = new CopyOnWriteArrayList<>();

    // whether or not we're a relay
    protected RelayedCandidateConnection relayedCandidateConnection;

    /**
     * IoSession for this socket / connection; will be one of type NioDatagramSession for UDP or NioSocketSession for TCP.
     */
    protected AtomicReference<IoSession> session = new AtomicReference<>(NULL_SESSION);

    /**
     * Socket timeout (default 1s).
     */
    protected int soTimeout = 1000;

    /**
     * Written message counter.
     */
    protected long writtenMessages;

    /**
     * Written byte counter.
     */
    protected long writtenBytes;

    /**
     * Written STUN/TURN message counter.
     */
    protected long writtenStunMessages;

    /**
     * Written STUN/TURN byte counter.
     */
    protected long writtenStunBytes;

    /**
     * The message queue is where incoming messages are added that were not otherwise processed (ie. DTLS etc..).
     */
    protected SizeTrackedLinkedTransferQueue<RawMessage> rawMessageQueue = new SizeTrackedLinkedTransferQueue<>();

    /**
     * Reusable IoFutureListener for connect.
     */
    protected final IoFutureListener<ConnectFuture> connectListener = new IoFutureListener<ConnectFuture>() {

        @Override
        public void operationComplete(ConnectFuture future) {
            if (future.isConnected()) {
                logger.debug("Setting session from future");
                setSession(future.getSession());
            } else {
                if (remoteTransportAddress == null) {
                    logger.warn("Connect failed from: {}", transportAddress);
                } else {
                    logger.warn("Connect failed from: {} to: {}", transportAddress, remoteTransportAddress);
                }
            }
        }

    };

    /**
     * Reusable IoFutureListener for writes.
     */
    protected final IoFutureListener<WriteFuture> writeListener = new IoFutureListener<WriteFuture>() {

        @Override
        public void operationComplete(WriteFuture future) {
            if (!future.isWritten()) {
                if (isDebug) {
                    IoSession sess = future.getSession();
                    if (sess != null) {
                        logger.debug("Write failed from: {} to: {}", sess.getLocalAddress(), sess.getRemoteAddress());
                    } else {
                        logger.debug("Write failed from: {} to: {}", transportAddress, remoteTransportAddress);
                    }
                }
            }
        }

    };
    /**
     * Once set, do not null out. May be referenced by IceHandler's cleaning sweeper job.
     */
    protected Agent localAgent = null;

    private long creationTime;

    protected Long rsvp;

    private Long closingTime;

    protected WeakReference<IceTransport> transportRef;

    IceSocketWrapper() throws IOException {
        throw new IOException("Invalid constructor, use IceSocketWrapper(TransportAddress) instead");
    }

    /**
     * Constructor.
     *
     * @param address TransportAddress
     * @throws IOException
     */
    protected IceSocketWrapper(TransportAddress address) throws IOException {
        logger.debug("New wrapper for {}", address);
        localAgent = Agent.localAgent.get();
        this.transportId = localAgent.getStunStack().getSessionAcceptorStrategy().toString();
        transportAddress = address;
        creationTime = System.currentTimeMillis();
        logger.debug("my uuid {}", id);
        iceSockets.put(id, this);
        logger.warn("Current iceSocket count {}", iceSockets.size());
        localAgent.addSocketUUID(id);
    }

    /**
     * Sends an IoBuffer from this socket. It is a utility method to provide a common way to send for both UDP and TCP socket.
     *
     * @param buf IoBuffer to send
     * @param destAddress destination SocketAddress to send to
     * @throws IOException if something goes wrong
     */
    public abstract void send(IoBuffer buf, SocketAddress destAddress) throws IOException;

    /**
     * Sends a DatagramPacket from this socket. It is a utility method to provide a common way to send for both UDP and TCP socket.
     *
     * @param p DatagramPacket to send
     * @throws IOException if something goes wrong
     */
    public abstract void send(DatagramPacket p) throws IOException;

    /**
     * Receives a DatagramPacket from this instance. Essentially it reads from already queued data, if the queue is empty, the datagram will be empty.
     *
     * @param p DatagramPacket to receive
     */
    public abstract void receive(DatagramPacket p) throws IOException;

    /**
     * Reads one message from the head of the queue or null if the queue is empty.
     *
     * @return RawMessage
     */
    public abstract RawMessage read();

    /**
     * Returns true if socket or session is closed.
     *
     * @return true = not open, false = not closed
     */
    public boolean isSessionClosed() {

        if (!closed.get()) {
            IoSession sess = session.get();
            if (!sess.equals(NULL_SESSION) && sess.isClosing()) {
                return true;//Do not flip the atomic boolean. because it prevents the socket from closing when someone calls close().
            }
        }
        return closed.get();
    }

    /**
     * Returns true if 'close' has been called
     * @return true if close method was invoked.
     */
    public boolean isSocketClosed() {
        return closed.get();
    }

    /**
     * Closes the connected session as well as the acceptor, if its non-shared.
     * @throws Exception
     */
    public boolean close() {
        return close(getSession());
    }

    /**
     * Closes the connected session as well as the acceptor, if its non-shared.
     *
     * @param sess IoSession being closed
     */
    public boolean close(IoSession sess) {
        if (IceTransport.handOffSocketClosure) {
            Callable<Boolean> closeMe = () -> closeInternal(sess);
            try {
                return IceTransport.getIceHandler().runSocketCloseJob(closeMe).get(5000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.debug("", e);
            }
            return false;
        } else {
            return closeInternal(sess);
        }
    }

    /**
     * Closes the connected session as well as the acceptor, if its non-shared.
     *
     * @param sess IoSession being closed
     */
    private boolean closeInternal(IoSession sess) {

        if (!closed.get()) {
            logger.debug("Close: {}", this);
        }
        logger.debug("{}   {}", session.get(), sess);
        if (!session.get().equals(NULL_SESSION) && sess != null && !sess.equals(NULL_SESSION) && !session.get().equals(sess)) {
            logger.warn("Closing socket with wrong session  {}", sess);
            return false;
        }

        // flip to closed if not set already
        if (closed.compareAndSet(false, true)) {
            closingTime = System.currentTimeMillis();
            if (sess != null) {
                // additional clean up steps
                logger.debug("Close session: {}  socket references: {}", sess.getId());
                try {
                    // if the session isn't already closed or disconnected
                    if (!sess.isClosing()) {
                        // close the session if we've not arrived here due to IOException
                        Throwable cause = (Throwable) sess.getAttribute(IceTransport.Ice.EXCEPTION.name().toLowerCase(), null);
                        if (cause == null || !(cause instanceof IOException)) {
                            // force close, but only if we're not already closing and not due to IOException
                            sess.closeNow();
                        } else {
                            logger.debug("Session close skipped due to IOException: {}", sess.getId(), cause);
                            sess.removeAttribute(IceTransport.Ice.EXCEPTION.name().toLowerCase());
                        }
                    }
                    sess.removeAttribute(IceTransport.Ice.STUN_STACK);
                } catch (Throwable t) {
                    logger.warn("Fail on close", t);
                } finally {
                    // clear the session ref
                    session.set(NULL_SESSION);
                }
            } else {
                logger.debug("Session null at close");
            }

            // get the stun stack
            StunStack stunStack = IceTransport.getIceHandler().lookupStunStack(transportAddress);
            if (stunStack == null) {
                logger.warn("While closing, StunStack not found for transport: {}", transportAddress);
            }
            // removal from the net access manager via stun stack
            if (stunStack != null) {
                // part of the removal process in stunstack closes the connector which closes this
                stunStack.removeSocket(transportId, transportAddress, remoteTransportAddress.get());
            }
            // unbinds and closes any non-shared acceptor
            IceTransport transport = IceTransport.getInstance(transportAddress.getTransport(), transportId);
            if (transport != null) { // remove the binding from the transport
                // Might be shared, so don't kill it, just remove binding
                try {
                    if (transport.removeBinding(rsvp, transportAddress)) {
                        logger.debug("removed binding: {}", transportAddress);
                    } else {
                        logger.warn("While closing, failed to remove binding: {}", transportAddress);
                    }
                } catch (Exception e) {
                    logger.warn("", e);
                }
            } else {
                logger.warn("While closing, no transport for: {} found with id: {}", transportAddress, transportId);
            }
            // for GC
            relayedCandidateConnection = null;
            logger.trace("Exit close: {} closed: {}", this, closed);
            //this.id = null;


            negotiatingRemoteAddresses.clear();

            // clear out raw messages lingering around at close
            try {
                if (rawMessageQueue != null) {
                    rawMessageQueue.clear();
                    rawMessageQueue = null;
                }
            } catch (Throwable t) {
                logger.warn("Exception clearing queue", t);
            }
            return true;
        } else {
            logger.debug("Already Closed: {}", this);
        }
        return false;
    }

    /**
     * Milliseconds between creation and closed. If not closed, then milliseconds between created and now.
     * @return
     */
    public long getTimeAlive() {
        if (closingTime == null) {
            return System.currentTimeMillis() - creationTime;
        }
        return closingTime - creationTime;
    }

    /**
     * Updates the written bytes / message counters.
     *
     * @param bytesLength
     */
    public void updateWriteCounters(long bytesLength) {
        // incoming length is the total from the IoSession
        writtenBytes = bytesLength;
        writtenMessages++;
        //logger.trace("updateWriteCounters - writtenBytes: {} writtenMessages: {}", writtenBytes, writtenMessages);
    }

    /**
     * Updates the STUN/TURN written bytes / message counters.
     *
     * @param bytesLength
     */
    public void updateSTUNWriteCounters(int bytesLength) {
        // incoming length is the message bytes length
        writtenStunBytes += bytesLength;
        writtenStunMessages++;
        //logger.trace("updateSTUNWriteCounters - writtenBytes: {} writtenMessages: {}", writtenStunBytes, writtenStunMessages);
    }

    /**
     * Returns the written byte count excluding STUN/TURN bytes.
     *
     * @return byte count minus STUN/TURN bytes
     */
    public long getWrittenBytes() {
        long written = 0L;
        if (writtenBytes > 0) {
            written = writtenBytes - writtenStunBytes;
        }
        return written;
    }

    /**
     * Returns the written message count excluding STUN/TURN messages.
     *
     * @return message count minus STUN/TURN messages
     */
    public long getWrittenMessages() {
        long written = 0L;
        if (writtenMessages > 0) {
            written = writtenMessages - writtenStunMessages;
        }
        return written;
    }

    /**
     * Returns the unique identifier for the associated acceptor.
     *
     * @return UUID string for the associated IceTransport instance or "disconnected" if not set on the session or not connected
     */
    public String getTransportId() {
        if (this.transportId != null) {
            return this.transportId;
        }

        IoSession sess = session.get();
        if (transportId == null && !sess.equals(NULL_SESSION) && sess.containsAttribute(IceTransport.Ice.UUID)) {
            this.transportId = (String) sess.getAttribute(IceTransport.Ice.UUID);
            return this.transportId;
        }
        return null;
    }

    /**
     * Get local address.
     *
     * @return local address
     */
    public abstract InetAddress getLocalAddress();

    /**
     * Get local port.
     *
     * @return local port
     */
    public abstract int getLocalPort();

    /**
     * Get socket address.
     *
     * @return socket address
     */
    public SocketAddress getLocalSocketAddress() {
        IoSession sess = session.get();
        if (sess.equals(NULL_SESSION)) {
            return transportAddress;
        }
        return sess.getLocalAddress();
    }

    /**
     * Sets the IoSession for this socket wrapper.
     *
     * @param newSession
     */
    public boolean setSession(IoSession newSession) {

        logger.debug("setSession - addr: {} session: {} previous: {}", transportAddress, newSession, session.get());
        if (newSession == null || newSession.equals(NULL_SESSION)) {
            //If there was an old session, are we are nulling out?
            IoSession oldSession = session.getAndSet(NULL_SESSION);
            if (oldSession != null && !oldSession.equals(NULL_SESSION)) {
                //Probably not going to happen.
                oldSession.removeAttribute(Ice.CONNECTION);
                oldSession.setAttribute(Ice.ACTIVE_SESSION, Boolean.FALSE);
                @SuppressWarnings("unchecked")
                IoFutureListener<CloseFuture> oldCloseFuture = (IoFutureListener<CloseFuture>) oldSession.getAttribute(Ice.CLOSE_FUTURE);
                if (oldCloseFuture != null) {
                    oldSession.getCloseFuture().removeListener(oldCloseFuture);
                }
            }
            return true;

        } else if (session.compareAndSet(NULL_SESSION, newSession)) {
            if (!newSession.getRemoteAddress().equals(remoteTransportAddress.get())) {
                InetSocketAddress inetAddr = (InetSocketAddress) newSession.getRemoteAddress();
                remoteTransportAddress.set(new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), getTransport()));
            }
            // set the connection attribute
            newSession.setAttribute(Ice.CONNECTION, this);
            // flag the session as selected / active!
            newSession.setAttribute(Ice.ACTIVE_SESSION, Boolean.TRUE);
            IoFutureListener<CloseFuture> newCloseFuture = new IoFutureListener<CloseFuture>() {
                @Override
                public void operationComplete(CloseFuture future) {
                    logger.info("CloseFuture done: {} closed? {}", future.getSession().getId(), future.isClosed());

                    try {
                        notifySessionClosed(future.getSession(), IceTransport.Ice.CLOSE_FUTURE);
                    } catch (Exception e) {
                        logger.warn("", e);
                    }
                }
            };
            newSession.setAttribute(Ice.CLOSE_FUTURE, newCloseFuture);
            // attach the close listener and log the result
            newSession.getCloseFuture().addListener(newCloseFuture);


            return true;
        } else if (session.get().getId() == newSession.getId()) {
            logger.debug("Same session already set: {}", session.get());
            return true;
        } else {
            logger.warn("Session already set: {}, connected: {}. incoming: {}", session.get(), session.get().isConnected(), newSession);
            return false;
        }
    }

    public boolean hasSession() {
        return !NULL_SESSION.equals(session.get());
    }


    /**
     * Returns an IoSession or null.
     *
     * @return IoSession if one exists or null otherwise
     */
    public IoSession getSession() {
        return session.get().equals(NULL_SESSION) ? null : session.get();
    }

    /**
     * Returns the Transport for this socket wrapper.
     *
     * @return transport
     */
    public abstract Transport getTransport();

    /**
     * Returns TransportAddress for the wrapped socket implementation.
     *
     * @return transport address
     */
    public TransportAddress getTransportAddress() {
        if (isTrace) {
            logger.trace("getTransportAddress: {} session: {}", transportAddress, getSession());
        }
        return transportAddress;
    }

    /**
     * Sets the TransportAddress of the remote end-point.
     *
     * @param remoteAddress address
     */
    public void setRemoteTransportAddress(TransportAddress remoteAddress) {
        // only set remote address for TCP
        if (this.isTCP()) {
            remoteTransportAddress.set(remoteAddress);
        } else {
            // get the transport
            IceUdpTransport transport = IceUdpTransport.getInstance(transportId);
            // get session matching the remote address
            IoSession sess = transport.getSessionByRemote(remoteAddress);
            // set the selected session on the wrapper
            setSession(sess);
        }
        if (rawMessageQueue != null) {
            // clear the queue of any messages not meant for the remote address being set
            rawMessageQueue.forEach(message -> {
                TransportAddress messageRemoteAddress = message.getRemoteAddress();
                if (!messageRemoteAddress.equals(remoteAddress)) {
                    logger.warn("Ejecting message from {}", messageRemoteAddress);
                    rawMessageQueue.remove(message);
                }
            });
        } else {
            logger.warn("Queue is not available");
        }
    }

    public TransportAddress getRemoteTransportAddress() {
        return remoteTransportAddress.get();
    }

    public boolean negotiateRemoteAddress(TransportAddress address) {

        if (this.isTCP()) {
            remoteTransportAddress.compareAndSet(null, address);
        }
        return negotiatingRemoteAddresses.add(address);
    }

    /**
     * Removes failed connectivity remote address.
     * @param address
     * @return
     */
    public boolean negotiationFinished(TransportAddress address) {
        return negotiatingRemoteAddresses.remove(address);
    }

    public boolean canNegotiate(TransportAddress address) {
        return negotiatingRemoteAddresses.contains(address);
    }

    public String getNegotiations() {
        return negotiatingRemoteAddresses.toString();
    }

    /**
     * Sets the relay connection used for channel data in TURN.
     *
     * @param relayedCandidateConnection
     */
    public void setRelayedConnection(RelayedCandidateConnection relayedCandidateConnection) {
        this.relayedCandidateConnection = relayedCandidateConnection;
    }

    public RelayedCandidateConnection getRelayedCandidateConnection() {
        return relayedCandidateConnection;
    }

    /**
     * Sets the socket timeout.
     *
     * @param timeout
     */
    public void setSoTimeout(int timeout) throws SocketException {
        soTimeout = timeout;
    }

    /**
     * Sets the traffic class.
     *
     * @param trafficClass
     */
    public void setTrafficClass(int trafficClass) {
        IoSession sess = session.get();
        if (!sess.equals(NULL_SESSION)) {
            IoSessionConfig config = sess.getConfig();
            if (config != null) {
                if (sess instanceof DatagramSessionConfig) {
                    DatagramSessionConfig dsConfig = (DatagramSessionConfig) config;
                    int currentTrafficClass = dsConfig.getTrafficClass();
                    if (isDebug) {
                        logger.debug("Datagram trafficClass: {} incoming: {}", currentTrafficClass, trafficClass);
                    }
                    if (currentTrafficClass != trafficClass) {
                        dsConfig.setTrafficClass(trafficClass);
                    }
                } else if (sess instanceof SocketSessionConfig) {
                    SocketSessionConfig ssConfig = (SocketSessionConfig) config;
                    int currentTrafficClass = ssConfig.getTrafficClass();
                    if (isDebug) {
                        logger.debug("Socket trafficClass: {} incoming: {}", currentTrafficClass, trafficClass);
                    }
                    if (currentTrafficClass != trafficClass) {
                        ssConfig.setTrafficClass(trafficClass);
                    }
                }
            }
        }
    }

    /**
     * Returns the raw message queue, which shouldn't contain any STUN/TURN messages.
     *
     * @return rawMessageQueue
     */
    public LinkedTransferQueue<RawMessage> getRawMessageQueue() {
        return rawMessageQueue;
    }

    /**
     * Accepts or rejects an offered message based on our closed state.
     *
     * @param message
     * @return true if accepted and false otherwise
     */
    public boolean offerMessage(RawMessage message) {
        //logger.trace("offered message: {} local: {} remote: {}", message, transportAddress, remoteTransportAddress);
        if (rawMessageQueue != null) {
            // while the queue type is unbounded, this will always return true
            return rawMessageQueue.offer(message);
        }
        logger.debug("Message rejected, socket ({}) is closed or queue is not available", remoteTransportAddress);
        return false;
    }

    /**
     * Returns whether or not this is a TCP wrapper, based on the instance type.
     *
     * @return true if TCP and false otherwise
     */
    public abstract boolean isTCP();

    /**
     * Returns whether or not this is a UDP wrapper, based on the instance type.
     *
     * @return true if UDP and false otherwise
     */
    public abstract boolean isUDP();

    /**
     * Sets the id of acceptor, so we can lookup the transport.
     *
     * @param id
     */
    public void setTransportId(String id) {
        logger.debug("Setting id {}", id);
        this.transportId = id;
    }

    public void setIceTransportRef(IceTransport trans) {
        transportRef = new WeakReference<IceTransport>(trans);
    }

    public IceTransport getIceTransportRef() {
        if (transportRef != null) {
            return transportRef.get();
        }
        return null;
    }

    @Override
    public int compareTo(IceSocketWrapper that) {
        int ret = 0;
        if (that != null) {
            // compare the transports UDP > TCP
            ret = this.getTransport().ordinal() - that.getTransport().ordinal();
            // break it apart into transport and address
            TransportAddress thatAddress = that.getTransportAddress();
            ret += Arrays.compare(transportAddress.getAddressBytes(), thatAddress.getAddressBytes());
            // when and if the address is the same, compare the ports
            if (ret == 0) {
                ret += Integer.compare(transportAddress.getPort(), thatAddress.getPort());
            }
        }
        return ret;
    }

    @Override
    public void finalize() {
        try {
            session.set(null);
            session = null;
        } catch (Exception e) {
            // ...
        }
    }

    /**
     * Builder for immutable IceSocketWrapper instance. If the IoSession is connection-less, an IceUdpSocketWrapper is returned; otherwise
     * an IceTcpSocketWrapper is returned.
     *
     * @param session IoSession for the socket
     * @return IceSocketWrapper for the given session type
     * @throws IOException
     */
    @Deprecated
    public final static IceSocketWrapper build(IoSession session) throws IOException {
        InetSocketAddress inetAddr = (InetSocketAddress) session.getLocalAddress();
        IceSocketWrapper iceSocket = null;
        if (session.getTransportMetadata().isConnectionless()) {
            iceSocket = new IceUdpSocketWrapper(new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), Transport.UDP));
            iceSocket.setSession(session);
        } else {
            iceSocket = new IceTcpSocketWrapper(new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), Transport.TCP));
            iceSocket.setSession(session);
            // set remote address (only sticks if its TCP)
            inetAddr = (InetSocketAddress) session.getRemoteAddress();
            iceSocket.setRemoteTransportAddress(new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), Transport.TCP));
        }
        return iceSocket;
    }

    /**
     * Builder for immutable IceSocketWrapper instance. If the localAddress is udp, an IceUdpSocketWrapper is returned; otherwise
     * an IceTcpSocketWrapper is returned.
     *
     * @param localAddress local address
     * @param remoteAddress destination address
     * @return IceSocketWrapper for the given address type
     * @throws IOException
     */
    public final static IceSocketWrapper build(TransportAddress localAddress, TransportAddress remoteAddress) throws IOException {
        IceSocketWrapper iceSocket = null;
        if (localAddress.getTransport() == Transport.UDP) {
            iceSocket = new IceUdpSocketWrapper(localAddress);
        } else {
            iceSocket = new IceTcpSocketWrapper(localAddress);
            if (remoteAddress != null) {
                // set remote address (only sticks if its TCP)
                iceSocket.setRemoteTransportAddress(
                        new TransportAddress(remoteAddress.getAddress(), remoteAddress.getPort(), Transport.TCP));
            }
        }
        return iceSocket;
    }

    /**
     * Builder for immutable IceSocketWrapper instance. If the localAddress is udp, an IceUdpSocketWrapper is returned; otherwise
     * an IceTcpSocketWrapper is returned.
     *
     * @param relayedCandidateConnection relay connection (TURN channel)
     * @return IceSocketWrapper for the address session type
     * @throws IOException
     */
    public final static IceSocketWrapper build(RelayedCandidateConnection relayedCandidateConnection) throws IOException {
        // use the host address
        TransportAddress localAddress = (TransportAddress) relayedCandidateConnection.getTurnCandidateHarvest().hostCandidate
                .getTransportAddress();
        // look for an existing ice socket before creating a new one with the same local address
        IceSocketWrapper iceSocket = IceTransport.getIceHandler().lookupBinding(localAddress);
        if (iceSocket == null) {
            TransportAddress remoteAddress = relayedCandidateConnection.getTurnCandidateHarvest().harvester.stunServer;
            if (localAddress.getTransport() == Transport.UDP) {
                iceSocket = new IceUdpSocketWrapper(localAddress);
            } else {
                iceSocket = new IceTcpSocketWrapper(localAddress);
                // set remote address (only sticks if its TCP)
                iceSocket.setRemoteTransportAddress(
                        new TransportAddress(remoteAddress.getAddress(), remoteAddress.getPort(), Transport.TCP));
            }
        }
        // attach the relay connection
        iceSocket.setRelayedConnection(relayedCandidateConnection);
        return iceSocket;
    }

    public String toSweeperInfo() {
        StringBuilder b = new StringBuilder();
        try {
            b.append(this.getClass().getSimpleName());
            b.append("[ id: ").append(getTransportId()).append(", link: ").append(this.transportAddress);
            b.append(" => ").append(this.remoteTransportAddress.get());
            b.append(", closed: ").append(isSocketClosed());
            b.append(", session:[ ");
            IoSession sess = getSession();
            if (sess != null) {
                b.append(sess.getId());
                b.append(", connected: ").append(sess.isConnected());
            } else {
                b.append("null");
            }
            b.append("]");
            b.append("]");
        } catch (Throwable t) {
            logger.error("", t);
            b.append("Error reading socket: ").append(t);
        }

        return b.toString();
    }

    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    public Agent getAgent() {
        return localAgent;
    }

    public void notifySessionClosed(IoSession session, Ice context) {
        logger.debug("notifySessionClosed");

        if (localAgent != null) {
            IceTransport.getIceHandler().submitTask(() -> {
                localAgent.notifySessionChanged(this, session, context);
            });
        }
    }

    /**
     * The unique identifier for this socket wrapper information.
     * @return this socket's UUID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the binding id on this socket. Called by IceTransport when this socket binds to an address. IceHandler may set this to null when the binding is released.
     * @param rsvp
     */
    public void setRsvp(Long rsvp) {
        this.rsvp = rsvp;
    }

    /**
     * Returns the binding id or null. When this socket binds to an address, the rsvp value is set. The IceHandler may set it to null when it is un-bound.
     * @return
     */
    public Long getRsvp() {
        return rsvp;
    }

    public static IceSocketWrapper getInstance(String uuid) {
        return iceSockets.get(uuid);
    }

    /**
     * Agent calls this to remove its sockets from uuid mapping when freed.
     * @param socketIds list of socket UUIDs
     */
    public static void removeSocketsFromMap(List<String> socketIds) {
        socketIds.forEach(socket -> {
            IceSocketWrapper wrapper = iceSockets.remove(socket);
            if (wrapper != null) {
                wrapper.logger.debug("Removed. {},  time alive: {}s seconds", wrapper.transportAddress, (wrapper.getTimeAlive()) / 1000.0);
            }
        });
    }

    public static int getSocketCount() {
        return iceSockets.size();
    }
}
