package com.red5pro.ice.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.util.CopyOnWriteMap;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StackProperties;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * IceTransport, the parent transport class.
 *
 * @author Paul Gregoire
 */
public abstract class IceTransport {

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

    // whether or not to use a shared acceptor for all transports (disable via -DNIO_SHARED_MODE=false)
    protected final static boolean sharedAcceptor = StackProperties.getBoolean("NIO_SHARED_MODE", true);

    // whether or not to handle a hung acceptor aggressively
    protected static boolean aggressiveAcceptorReset = StackProperties.getBoolean("ACCEPTOR_RESET", false);

    // used to set QoS / traffic class option on the sockets
    public static int trafficClass = StackProperties.getInt("TRAFFIC_CLASS", 24);

    // thread-safe map containing ice transport instances
    protected static Map<String, IceTransport> transports = new CopyOnWriteMap<>(1);

    // holder of bound ports; used to prevent blocking issues querying acceptors
    protected static ConcurrentSkipListSet<Integer> boundPorts = new ConcurrentSkipListSet<>();

    /**
     * Unique identifier.
     */
    protected final String id = UUID.randomUUID().toString();

    protected int receiveBufferSize = StackProperties.getInt("SO_RCVBUF", BUFFER_SIZE_DEFAULT);

    protected int sendBufferSize = StackProperties.getInt("SO_SNDBUF", BUFFER_SIZE_DEFAULT);

    protected int ioThreads = StackProperties.getInt("NIO_WORKERS", Runtime.getRuntime().availableProcessors() * 2);

    /**
     * Local / instance socket acceptor; depending upon the transport, this will be NioDatagramAcceptor for UDP or NioSocketAcceptor for TCP.
     */
    protected IoAcceptor acceptor;

    protected ExecutorService executor = Executors.newCachedThreadPool();

    // constants for the session map or anything else
    public enum Ice {
        TRANSPORT,
        CONNECTION,
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
        CLOSE_FUTURE;
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
     * Creates the i/o handler and nio acceptor; ports and addresses are bound.
     */
    public IceTransport() {
        logger.info("Properties: Socket linger: {} DNS cache ttl: {}", soLinger, System.getProperty("networkaddress.cache.ttl"));
    }

    /**
     * Returns a static instance of this transport.
     *
     * @param type the transport type requested, either UDP or TCP. null will search both.
     * @param id transport / acceptor identifier
     * @return IceTransport
     */
    public static IceTransport getInstance(Transport type, String id) {
        //logger.trace("getInstance - type: {} id: {}", type, id);
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

    /**
     * Returns the acceptor if it exists and null otherwise.
     *
     * @return acceptor
     */
    public IoAcceptor getAcceptor() {
        return acceptor;
    }

    /**
     * Adds a socket binding to the acceptor.
     *
     * @param addr
     * @return true if successful and false otherwise
     */
    public boolean addBinding(SocketAddress addr) {
        return false;
    }

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

    /**
     * Removes a socket binding from the acceptor by port.
     *
     * @param port
     * @return true if successful and false otherwise
     */
    public boolean removeBinding(int port) {
        if (acceptor != null) {
            for (SocketAddress addr : acceptor.getLocalAddresses()) {
                if (((InetSocketAddress) addr).getPort() == port) {
                    return removeBinding(addr);
                }
            }
        } else {
            logger.warn("Acceptor is null, cannot remove binding for port: {}", port);
        }
        return false;
    }

    /**
     * Removes a socket binding from the acceptor.
     *
     * @param addr
     * @return true if successful and false otherwise
     */
    public boolean removeBinding(SocketAddress addr) {
        // if the acceptor is null theres nothing to do
        if (acceptor != null) {
            int port = ((InetSocketAddress) addr).getPort();
            try {
                // perform the unbinding, if bound
                if (acceptor.getLocalAddresses().contains(addr)) {
                    acceptor.unbind(addr); // do this only once, especially for TCP since it can block
                    logger.debug("Binding removed: {}", addr);
                    // no exceptions? return true for removing the binding
                    return true;
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
                // remove the address from the handler
                if (iceHandler.remove(addr)) {
                    logger.debug("Removed address: {} from handler", addr);
                }
                // remove the port from the list
                if (boundPorts.remove(port)) {
                    logger.debug("Port removed from bound ports: {}, now removing binding: {}", port, addr);
                } else {
                    logger.debug("Port already removed from bound ports: {}, now removing binding: {}", port, addr);
                }
                // not-shared, kill it
                if (!sharedAcceptor) {
                    try {
                        stop();
                    } catch (Exception e) {
                        logger.warn("Exception stopping transport", e);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Ports and addresses are unbound (stop listening).
     */
    public void stop() throws Exception {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (acceptor != null) {
            acceptor.unbind();
            acceptor.dispose(true);
            logger.info("Disposed acceptor: {} {}", id);
        }
    }

    /**
     * Review all ports in-use for a conflict with the given port.
     *
     * @param port
     * @return true if already bound and false otherwise
     */
    public static boolean isBound(int port) {
        //logger.info("isBound: {}", port);
        return boundPorts.contains(port);
    }

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
     * @param ioThreads the ioThreads to set
     */
    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
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

}
