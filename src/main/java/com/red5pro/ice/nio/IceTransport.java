package com.red5pro.ice.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StackProperties;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;

/**
 * IceTransport, the parent transport class.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public abstract class IceTransport {

    /** CachedThreadPool shared by both udp and tcp transports. */
    protected static ExecutorService ioExecutor = Executors.newCachedThreadPool();

    protected static Logger pluginLogger = LoggerFactory.getLogger(IceTransport.class);

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected boolean isTrace = logger.isTraceEnabled(), isDebug = logger.isDebugEnabled();

    protected final static int BUFFER_SIZE_DEFAULT = 65535;

    protected final static ProtocolCodecFilter iceCodecFilter = new ProtocolCodecFilter(new IceEncoder(), new IceDecoder());

    protected final static IceHandler iceHandler = new IceHandler();

    // used for tcp socket linger, it defaults to -1 (disabled)
    // Specify a linger-on-close timeout. This option disables/enables immediate return from a close() of a TCP Socket.
    // Enabling this option with a non-zero Integer timeout means that a close() will block pending the transmission
    // and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon
    // reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout
    // of zero does a forceful close immediately. If the specified timeout value exceeds 65,535 it will be reduced to
    // 65,535. Valid only for TCP: SocketImpl
    // SO_LINGER time is in seconds.
    // more info: https://stackoverflow.com/questions/3757289/when-is-tcp-option-so-linger-0-required#13088864
    protected static int soLinger = StackProperties.getInt("SO_LINGER", -1);

    // used for idle timeout checks, the connection timeout is currently 2s; to disable this its -1
    protected static int timeout = StackProperties.getInt("SO_TIMEOUT", 120);

    // used for binding and unbinding timeout, default 2s
    protected static long acceptorTimeout = StackProperties.getInt("ACCEPTOR_TIMEOUT", 2);

    /** whether or not to use a shared acceptor. */
    protected static boolean sharedAcceptor = StackProperties.getBoolean("NIO_SHARED_MODE", false);//Shared mode disabled.

    /** whether or not TCP Socket acceptors will use a shared IoProcessorPool and executor.*/
    protected final static boolean sharedIoProcessor = StackProperties.getBoolean(StackProperties.NIO_USE_PROCESSOR_POOLS, false);

    /** Number of processors created for the shared IoProceeor pool.*/
    protected static int ioThreads = StackProperties.getInt(StackProperties.NIO_PROCESSOR_POOL_SIZE,
            Runtime.getRuntime().availableProcessors() * 2);

    /**
     * @param ioThreads the ioThreads to set
     */
    public static void setIoThreads(int count) {
        ioThreads = count;
    }

    // whether or not to handle a hung acceptor aggressively
    protected static boolean aggressiveAcceptorReset = StackProperties.getBoolean("ACCEPTOR_RESET", false);
    /**
     * If true, ice-socket closure operations are performed by an internal thread to ensure disposing the acceptor does not risk deadlock from potentially using an IoThread.
     *
     * See {@link org.apache.mina.core.service.IoService#dispose(boolean)}
     */
    public static boolean handOffSocketClosure = true;

    // used to set QoS / traffic class option on the sockets
    public static int trafficClass = StackProperties.getInt("TRAFFIC_CLASS", 24);

    // thread-safe map containing ice transport instances
    protected static Map<String, IceTransport> transports = new ConcurrentHashMap<>();

    // holder of bound ports; used to prevent blocking issues querying acceptors
    private static ConcurrentSkipListSet<ABPEntry> allBoundPorts = new ConcurrentSkipListSet<>();

    /**
     * The acceptor's bound-addresses list will contain an address until it is fully unbound.
     * As a result, multiple threads can enter 'unbind' with the same target.
     * This boundAddresses set prevents multiple threads from entering acceptor.unbind(address).
     */
    protected Set<SocketAddress> myBoundAddresses = new ConcurrentHashSet<>();
    /**
     * Prevents thread contention between calls to unbind and the call to stop.
     */
    private ReentrantLock unbindStopLocker = new ReentrantLock();
    /**
     * Prevents stop from being called twice.
     */
    private AtomicBoolean stopCalled = new AtomicBoolean();
    /**
     * Unique identifier.
     */
    protected final String id = UUID.randomUUID().toString();

    protected int receiveBufferSize = StackProperties.getInt("SO_RCVBUF", BUFFER_SIZE_DEFAULT);

    protected int sendBufferSize = StackProperties.getInt("SO_SNDBUF", BUFFER_SIZE_DEFAULT);

    /**
     * Local / instance socket acceptor; depending upon the transport, this will be NioDatagramAcceptor for UDP or NioSocketAcceptor for TCP.
     */
    protected IoAcceptor acceptor;
    /**
     * Owning agent id, or null if using shared acceptor.
     */
    private String agentId;

    private boolean disposed;

    // constants for the session map or anything else
    public enum Ice {
        TRANSPORT,
        CONNECTION,
        AGENT,
        STUN_STACK,
        DECODER,
        ENCODER,
        DECODER_STATE_KEY,
        CANDIDATE,
        TCP_BUFFER,
        UUID,
        CLOSE_ON_IDLE,
        ACTIVE_SESSION,
        LOCAL_TRANSPORT_ADDR, // local TransportAddress for the connected InetSocketAddress
        REMOTE_TRANSPORT_ADDR, // remote TransportAddress for the connected InetSocketAddress
        NEGOTIATING_TRANSPORT_ADDR,
        NEGOTIATING_ICESOCKET,
        CLOSED,
        CLOSE_FUTURE,
        EXCEPTION, //In case any end-users are reflecting 'exception', name as lower case maintains compatibility with previous versions.
        /** Ice library has removed binding. */
        DISPOSE_ADDRESS;
    }

    static {
        // configure DNS cache ttl
        String ttl = System.getProperty("networkaddress.cache.ttl");
        if (ttl == null) {
            // persist successful lookup forever -1
            // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
            System.setProperty("networkaddress.cache.ttl", "60");
        }

    }

    /**
     * Did the application add bindings either successfully or unsuccessfully? Once tried, this should always reflect true.
     * False if the application never called 'addBinding'
     */
    protected boolean acceptorUtilized;

    /**
     * Creates the i/o handler and nio acceptor; ports and addresses are bound.
     */
    public IceTransport() {
        logger.info("Properties: Socket linger: {} DNS cache ttl: {}", soLinger, System.getProperty("networkaddress.cache.ttl"));

    }

    /**
     * Returns an instance of this transport.
     *
     * @param type the transport type requested, either UDP or TCP. Passing null will look up either type if type is unknown.
     * @param id transport / acceptor identifier. if type is null and id is IceSocketWrapper.DISCONNECTED, it will return a tcp type.
     * @return IceTransport
     */
    public static IceTransport getInstance(Transport type, String id) {

        if (type == Transport.TCP) {
            return IceTcpTransport.getInstance(id);
        } else if (type == null) {
            IceTransport find = null;
            if ((find = IceTcpTransport.getInstance(id)) != null) {
                return find;
            }
        }
        return IceUdpTransport.getInstance(id);
    }

    public static List<IceTransport> getTransportsForAgent(Transport type, String agentId) {
        List<IceTransport> ret = new ArrayList<>();
        transports.forEach((id, t) -> {
            if (!sharedAcceptor) {
                if (agentId.equals(t.agentId) && type.equals(t.getTransport())) {
                    ret.add(t);
                }
            } else {
                if (type.equals(t.getTransport())) {
                    ret.add(t);
                }
            }
        });
        return ret;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the acceptor if it exists and null otherwise.
     *
     * @return acceptor
     */
    public IoAcceptor getAcceptor() {
        return acceptor;
    }

    /**
     * Attempt to bind a port to a local address.
     * @param socketUUID unique identifier for the socket bind-session. This identifier represents the intent to bind, successful or not. Subsequent connected IoSsessions will have their own uuid.
     * @param addr address to bind to.
     * @return bind ID or null if address failed to bind.
     */
    public abstract Long addBinding(String socketUUID, InetSocketAddress addr);

    /**
     * Registers a StunStack and IceSocketWrapper to the internal maps to wait for their associated IoSession creation. This causes a bind on the given local address.
     *
     * @param stunStack
     * @param iceSocket
     * @return true if successful and false otherwise
     */
    public boolean registerStackAndSocket(StunStack stunStack, IceSocketWrapper iceSocket) {
        return false;
    }

    public void forceClose() {
        if (acceptor != null) {
            acceptor.dispose(false);
            acceptor = null;
        }
    }

    public boolean removeBinding(SocketAddress addr) throws Exception {
        return removeBinding(null, addr);
    }

    /**
     * Removes a socket binding from the acceptor.
     * Currently if the acceptor is not shared, there is a one-to-one acceptor-to-socket ratio, and removing binding will call stop.
     *
     * @param rsvp id or null
     * @param addr
     * @return
     * @throws Exception
     */
    public boolean removeBinding(Long rsvp, SocketAddress addr) throws Exception {
        logger.debug("remove binding: {}  {} rsvp: {}", addr, myBoundAddresses.toString(), rsvp);

        //Acceptor bound addresses list will contain an address until it is fully unbound.
        //As a result, multiple threads can enter 'unbind' with the same target.
        //This boundAddresses set prevents multiple threads from entering acceptor.unbind(addr).
        if (myBoundAddresses.remove(addr)) {
            // if the acceptor is null theres nothing to do
            if (acceptor != null) {
                int port = ((InetSocketAddress) addr).getPort();
                // We dont want another thread calling stop while we are un-binding. We may have additional tasks to do afterwards.
                unbindStopLocker.lock();

                boolean didUnbind = false;
                try {
                    // Did another thread call stop while we waited for lock?
                    // Or other thread called stop and is wating for lock.
                    if (stopCalled.get()) {
                        return true;//return from inside 'try' to release lock in finally.
                    }
                    // perform the un-binding, if bound
                    if (acceptor.getLocalAddresses().contains(addr)) {

                        acceptor.unbind(addr); // do this only once, especially for TCP since it can block

                        logger.debug("Binding removed: {}", addr);
                        didUnbind = true;
                        // no exceptions? return true for removing the binding
                        return true;
                    } else {
                        logger.debug("Local address not bound by acceptor: {}", addr);
                    }

                } catch (Throwable t) {
                    // if aggressive acceptor handling is enabled, reset the acceptor
                    if (aggressiveAcceptorReset) {
                        logger.warn("Acceptor will be reset with extreme predudice, due to remove binding failed on {}", addr, t);
                        acceptor.dispose(false);
                        acceptor = null;
                    } else if (isDebug) {
                        // putting on the debug guard to prevent flooding the log
                        logger.warn("Remove binding failed on {}", addr, t);
                    }
                } finally {

                    if (acceptor != null && acceptor.getLocalAddresses().contains(addr)) {
                        logger.warn("unable to remove binding from acceptor.");
                        //add it back
                        myBoundAddresses.add(addr);
                    }

                    unbindStopLocker.unlock();

                    // remove the address from the handler
                    if (didUnbind) {
                        if (iceHandler.remove(addr)) {
                            logger.debug("Removed address: {} from handler", addr);
                        }
                        // remove the port from the list
                        if (removeCachedBoundAddressInfo(rsvp, (InetSocketAddress) addr, port)) {
                            logger.debug("Port {} removed from bound ports listing: {}", port, addr);
                        } else {
                            logger.debug("Port {} already removed from bound ports listing: {}", port, addr);
                        }
                    } else {
                        logger.warn("Did not unbind address: {} from handler. transport-id: {}", id);
                    }

                    if (!sharedAcceptor && myBoundAddresses.isEmpty()) {
                        // Acceptor is not shared and the last binding was removed. kill it
                        stop();
                    }

                }
            } else {
                logger.debug("cant unbind address, Acceptor is null {}  id: {}", addr, id);
            }
        } else {
            logger.debug("address was already unbound {}  id: {}", addr, id);
        }
        return false;
    }

    /**
     * Admin/maintenance method.
     */
    public void unregister() {
        transports.remove(id);
    }

    /**
     *
     * @return true if acceptor is released.
     * @throws Exception,
     */
    public boolean stop() throws Exception {
        //Can only be called once.
        if (stopCalled.compareAndSet(false, true)) {
            logger.info("Stop {}", id);
            if (this.myBoundAddresses.size() > 0) {
                logger.debug("Stopping with bindings {}", myBoundAddresses.toString());
            }

            //Cannot be called while other thread is still calling remove address.
            unbindStopLocker.lock();
            disposed = false;
            Set<SocketAddress> copy = new HashSet<>();
            try {
                if (acceptor != null) {
                    //Normal closure. Check for bound ports.
                    if (!acceptor.getLocalAddresses().isEmpty()) {
                        logger.debug("Acceptor has addresses at 'stop' event. Unbind.");
                        copy.addAll(acceptor.getLocalAddresses());
                        acceptor.unbind();
                    }
                    //Forced closure. Dont wait.
                    acceptor.dispose(true);
                    disposed = true;
                    logger.debug("Disposed acceptor: {} {}", id);
                }
            } catch (Throwable t) {
                logger.warn("Exception stopping transport", t);
                throw t;
            } finally {
                unbindStopLocker.unlock();
                // un-forced normal closure.
                if (disposed) {
                    logger.info("Disposed {}", id);
                    copy.forEach(addy -> {
                        removeCachedBoundAddressInfo(((InetSocketAddress) addy));
                    });
                    transports.remove(id);
                    logger.debug("Unregistered self: {}", id);
                } else {
                    //Thread never made it to or past 'dispose'
                    // Sweeper will catch us
                    logger.debug("Could not unregistered self: {}", id);
                }
            }
        }
        return disposed;
    }

    boolean isUnbound() {
        return disposed;
    }


    public abstract Transport getTransport();

    /**
     * Returns the static ProtocolCodecFilter.
     *
     * @return iceCodecFilter
     */
    public static ProtocolCodecFilter getProtocolcodecfilter() {
        return iceCodecFilter;
    }

    /**
     * @param sendBufferSize the sendBufferSize to set
     */
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    /**
     * @param receiveBufferSize the receiveBufferSize to set
     */
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    /**
     * General purpose timeout value; used for connection and idle timeouts.
     *
     * @return timeout
     */
    public static int getTimeout() {
        return timeout;
    }

    /**
     * Set a timeout value in seconds.
     *
     * @param timeout seconds to elapse before timing out
     */
    public static void setTimeout(int timeout) {
        IceTransport.timeout = timeout;
    }

    /**
     * Returns the IoHandler for ICE connections.
     *
     * @return iceHandler
     */
    public static IceHandler getIceHandler() {
        return iceHandler;
    }

    /**
     * Returns whether or not a shared acceptor is in-use.
     *
     * @return true if shared and false otherwise
     */
    public static boolean isSharedAcceptor() {
        return sharedAcceptor;
    }

    /**
     * Returns the acceptor timeout.
     *
     * @return acceptorTimeout
     */
    public static long getAcceptorTimeout() {
        return acceptorTimeout;
    }

    /**
     * Replaces a connectivity checking session with the actual media session.
     * @param session
     * @return
     */
    protected boolean associateSession(IoSession session) {
        logger.debug("Associate socket with session {}", session);
        TransportAddress address = (TransportAddress) session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR);
        if (address != null) {
            IceSocketWrapper socket = IceTransport.getIceHandler().lookupBinding(address);
            if (socket != null) {
                return socket.setSession(session);
            } else {
                logger.warn("Cannot associate session. Socket address {} not found in transport {}", address, id);
            }
        }
        return false;
    }

    /**
     * Has no effect if acceptor is shared.
     * @param aid
     */
    public void setAgentId(String aid) {
        if (!sharedAcceptor) {
            agentId = aid;
        }
    }

    /**
     * Returns null if acceptor is shared.
     * @return
     */
    public String getAgentId() {
        return agentId;
    }

    public static boolean transportExists(String id) {
        return transports.containsKey(id);
    }

    public static int transportCount() {
        return transports.size();
    }

    protected String getStoppedAndAddresses() {
        Set<SocketAddress> arr = Collections.emptySet();
        if (acceptor != null) {
            arr = acceptor.getLocalAddresses();
        }
        return String.format("stopped %s lck: %b, refs: %s, sox: %s", String.valueOf(stopCalled.get()), unbindStopLocker.isLocked(),
                myBoundAddresses.toString(), arr.toString());
    }

    public boolean isStopped() {
        return stopCalled.get();
    }

    public Set<SocketAddress> getBoundAddresses() {
        return acceptor.getLocalAddresses();
    }

    public int getEstimatedBoundAddressCount() {
        int size = 0;
        try {
            size = acceptor.getLocalAddresses().size();
        } catch (Throwable t) {//ignore null pointer exception.
        }
        return size;
    }

    protected boolean updateReservedPortWithHost(Long rid, InetSocketAddress addy) {
        Predicate<ABPEntry> pred = entry -> entry.port == addy.getPort();
        Optional<ABPEntry> ret = allBoundPorts.stream().filter(pred).findFirst();
        if (ret.isPresent()) {
            ret.get().update(rid, addy);
            return true;
        }

        return false;
    }

    /**
     * Add successfully bound address and port to the lookup index.
     * @param iceSocketUUID IceSocket UUID
     * @param addy address to bind to.
     * @param port port to bind to.
     * @return Long Reservation id
     */
    protected Long cacheBoundAddressInfo(String iceSocketUUID, InetSocketAddress addy, int port) {
        logger.debug("add reservation for port {} with {}  for {}", port, addy, iceSocketUUID);
        boolean results = false;
        long start = System.currentTimeMillis();
        synchronized (allBoundPorts) {
            try {
                Predicate<ABPEntry> pred = entry -> entry.port == port;
                Optional<ABPEntry> ret = allBoundPorts.stream().filter(pred).findFirst();
                if (ret.isPresent()) {
                    int numAddresses = ret.get().binds.incrementAndGet();
                    ReservationEntry rsvp = RE().id(iceSocketUUID).with(addy);
                    results = ret.get().hosts.add(rsvp);
                    Long rid = rsvp.rid;
                    logger.debug("added reservation {}. num binds for port {} = {}", results, port, numAddresses);
                    return results ? rid : null;
                } else {
                    ABPEntry entry = new ABPEntry();
                    entry.port = port;
                    entry.binds.incrementAndGet();
                    ReservationEntry rsvp = RE().id(iceSocketUUID).with(addy);
                    entry.hosts.add(rsvp);
                    Long rid = rsvp.rid;
                    results = allBoundPorts.add(entry);
                    logger.debug("added reservation {}. num binds for port {} = {}", results, port, 1);
                    return results ? rid : null;
                }
            } finally {
                logger.info("addReservedPort dur: {}", System.currentTimeMillis() - start);
            }
        }
    }

    public boolean removeCachedBoundAddressInfo(Long rid, int port) {
        return removeCachedBoundAddressInfo(rid, null, port);
    }

    public boolean removeCachedBoundAddressInfo(InetSocketAddress addy) {
        return removeCachedBoundAddressInfo(null, addy, addy.getPort());
    }

    public boolean removeCachedBoundAddressInfo(Long rid, InetSocketAddress addy, int port) {
        logger.debug("remove reservation. for port {} with rid: {} and tid: {}", port, rid, addy);
        synchronized (allBoundPorts) {
            Predicate<ABPEntry> pred = entry -> entry.port == port;
            Optional<ABPEntry> ret = allBoundPorts.stream().filter(pred).findFirst();
            if (ret.isPresent()) {

                if (ret.get().removeRid(rid) || ret.get().removeTid(addy)) {
                    int numAddresses = ret.get().binds.decrementAndGet();
                    logger.debug("num binds for port {} = {}", port, numAddresses);
                    if (numAddresses == 0) {
                        allBoundPorts.remove(ret.get());
                        logger.debug("cleared reservations {}", port);
                    }
                    ReservationEntry owner = ReservationEntry.reservation.get();
                    if (owner != null) {
                        ReservationEntry.reservation.set(null);
                        iceHandler.notifyReservationRemoved(owner.rid, owner.socketUUID, owner.address);
                    }

                    return true;//rid or tid was removed.
                }
            } else {
                logger.debug("optional not present for entry of port {}", port);
            }
        }
        return false;
    }

    /**
     * Review all ports in-use for a conflict with the given port.
     *
     * @param port
     * @return true if already bound and false otherwise
     */
    public static boolean isBound(int port) {
        Predicate<ABPEntry> pred = entry -> entry.port == port;
        Optional<ABPEntry> ret = allBoundPorts.stream().filter(pred).findFirst();
        return ret.isPresent();
    };

    /** Check if a bind reservation id is still present.
    *
    * @param port
    * @return true if already bound and false otherwise
    */
    public static boolean didBind(Long rsvp, int port) {
        Predicate<ABPEntry> pred = entry -> entry.port == port && entry.hasRsvp(rsvp);
        Optional<ABPEntry> ret = allBoundPorts.stream().filter(pred).findFirst();
        return ret.isPresent();
    }

    public boolean wasAcceptorUtilized() {
        return acceptorUtilized;
    }

    public static Set<ABPEntry> getGlobalListing() {
        return allBoundPorts;
    }

    public abstract Transport getType();

    /**
     * All-Bound-Ports reservation table entry.
     * @author Andy
     *
     */
    public static class ABPEntry implements Comparable<ABPEntry> {

        Integer port;
        AtomicInteger binds = new AtomicInteger();
        Set<ReservationEntry> hosts = new ConcurrentSkipListSet<>();

        /**
         * Hash on the Integer.
         * @return
         */
        public int hashCode() {
            return port.hashCode();
        }

        public boolean hasRsvp(Long rid) {
            //first try look up with value of instance.
            if (hosts.contains(ReservationEntry.valueOf(rid))) {
                return true;
            }
            // Now just iterate and compare with direct instance.
            for (ReservationEntry rsvp : hosts) {
                if (rid == rsvp.rid) {
                    if (ReservationEntry.reservation.get() == null) {
                        ReservationEntry.reservation.set(rsvp);
                    }
                    return true;
                }
            }
            return false;
        }

        public void update(Long rid, InetSocketAddress addy) {
            for (ReservationEntry rsvp : hosts) {
                if (rid == rsvp.rid) {
                    rsvp.address = addy;
                    break;
                }
            }
        }

        public boolean removeTid(InetSocketAddress tid) {
            if (tid == null) {
                return false;
            }
            ReservationEntry found = null;
            for (ReservationEntry rsvp : hosts) {
                if (tid.equals(rsvp.address)) {
                    found = rsvp;
                    break;
                }
            }
            if (found != null) {
                ReservationEntry.reservation.set(found);
                return hosts.remove(found);
            }

            return false;
        }

        public boolean removeRid(Long rid) {
            if (rid == null) {
                return false;
            }
            //lookup object will have null socektUUID and helps determine which entry to return in 'compare' or 'equals'
            ReservationEntry lookup = ReservationEntry.valueOf(rid);
            try {
                if (hosts.remove(lookup)) {
                    return true;
                }
            } finally {
                lookup = null;
            }
            return false;
        }

        /**
         * Compare by Integer.
         */
        @Override
        public int compareTo(ABPEntry o) {
            if (o == null) {
                throw new NullPointerException();
            }

            return Integer.compare(port, o.port);
        }

        @Override
        public boolean equals(Object what) {
            if (ABPEntry.class.isInstance(what)) {
                ABPEntry that = (ABPEntry) what;
                return port.equals(that.port);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("{ port:%d, address count:%d }", port, binds.get());
        }

    }

    public static ReservationEntry RE() {
        return new ReservationEntry();
    }

    public static class ReservationEntry implements Comparable<ReservationEntry> {
        public static ThreadLocal<ReservationEntry> reservation = new ThreadLocal<>();
        private static long ridCount = 0;
        private final long rid;
        public String socketUUID = null;
        public InetSocketAddress address;

        private ReservationEntry(Long rsvp) {
            rid = rsvp;
        }

        private ReservationEntry() {
            rid = ++ridCount;
        }

        public ReservationEntry id(String socketUUID) {
            this.socketUUID = socketUUID;
            return this;
        }

        public ReservationEntry with(InetSocketAddress address) {
            this.address = address;
            return this;
        }

        public boolean equals(Object o) {

            if (ReservationEntry.class.isInstance(o)) {
                ReservationEntry that = (ReservationEntry) o;
                if (this.rid == that.rid) {
                    if (reservation.get() == null) {
                        if (this.socketUUID == null && that.socketUUID != null) {
                            reservation.set(that);
                        } else if (that.socketUUID == null && this.socketUUID != null) {
                            reservation.set(this);
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public int compareTo(ReservationEntry o) {
            boolean equal = (this.rid == o.rid);
            if (equal) {
                if (reservation.get() == null) {
                    if (this.socketUUID == null && o.socketUUID != null) {
                        reservation.set(o);
                    } else if (o.socketUUID == null && this.socketUUID != null) {
                        reservation.set(this);
                    }
                }
            }
            return (int) (this.rid - o.rid);
        }

        static ReservationEntry valueOf(Long rsvp) {
            return new ReservationEntry(rsvp);
        }
    }

    /**
     * For each Transport type utilized(TCP and UDP), this enum represents the three basic ways to manage IceTransports for users.
     * Create an IceTransport for every IceSocketWrapper. Create a single IceTransport for each end user.
     * Create one IceTransport for all users.
     * @author Andy
     *
     */
    public static enum AcceptorStrategy {
        /** One acceptor per Socket Wrapper*/
        DiscretePerSocket,
        /**One acceptor per user session*/
        DiscretePerSession,
        /** Shared Acceptor, sharedAcceptor*/
        Shared;

        private static AcceptorStrategy[] cachedvalues = values();

        public static AcceptorStrategy valueOf(int ordinal) {
            if (ordinal < 0 || ordinal >= cachedvalues.length) {
                return DiscretePerSocket;
            }
            return cachedvalues[ordinal];
        }
    }

    /**
     * Tells the system to use shared acceptors.
     */
    public static void setSharedAcceptor() {
        sharedAcceptor = true;
    };

}
