package com.red5pro.ice.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;

/**
 * IceTransport for TCP connections.
 *
 * @author Paul Gregoire
 */
public class IceTcpTransport extends IceTransport {

    /**
     * Socket linger for this instance.
     */
    private int localSoLinger = -1;

    /**
     * Creates the i/o handler and nio acceptor; ports and addresses are bound.
     */
    private IceTcpTransport() {
        logger.info("id: {} shared: {} accept timeout: {}s idle timeout: {}s I/O threads: {}", id, sharedAcceptor, acceptorTimeout, timeout,
                ioThreads);
    }

    /**
     * Returns a static instance of this transport.
     *
     * @param id transport / acceptor identifier
     * @return IceTransport
     */
    public static IceTcpTransport getInstance(String id) {
        IceTcpTransport instance = (IceTcpTransport) transports.get(id);
        // an id of "disconnected" is a special case where the socket is not associated with an IoSession
        if (instance == null || IceSocketWrapper.DISCONNECTED.equals(id)) {
            if (IceTransport.isSharedAcceptor()) {
                // loop through transport and if none are found for TCP, create a new one
                for (Entry<String, IceTransport> entry : transports.entrySet()) {
                    if (entry.getValue() instanceof IceTcpTransport) {
                        instance = (IceTcpTransport) entry.getValue();
                        break;
                    }
                }
                if (instance == null) {
                    instance = new IceTcpTransport();
                }
            } else {
                instance = new IceTcpTransport();
            }
        }
        // create an acceptor if none exists for the instance
        if (instance != null && instance.getAcceptor() == null) {
            instance.createAcceptor();
        }
        //logger.trace("Instance: {}", instance);
        return instance;
    }

    void createAcceptor() {
        if (acceptor == null) {
            // create the nio acceptor
            acceptor = new NioSocketAcceptor(ioThreads);
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
                    //logger.debug("sessionCreated: {}", session);
                    //logger.trace("Acceptor sessions: {}", acceptor.getManagedSessions());
                    session.setAttribute(IceTransport.Ice.UUID, id);
                }

                @Override
                public void sessionClosed(IoSession session) throws Exception {
                    //logger.debug("sessionClosed: {}", session);
                }

                @Override
                public void sessionDestroyed(IoSession session) throws Exception {
                    //logger.debug("sessionDestroyed: {}", session);
                }
            });
            // configure the acceptor
            SocketSessionConfig sessionConf = ((NioSocketAcceptor) acceptor).getSessionConfig();
            sessionConf.setReuseAddress(true);
            sessionConf.setTcpNoDelay(true);
            // set the linger value if its been set locally
            if (localSoLinger > -1) {
                sessionConf.setSoLinger(localSoLinger);
            } else {
                // externalized linger property for configuration
                sessionConf.setSoLinger(soLinger); // 0 = close immediately, -1 = disabled, > 0 = linger for x seconds
            }
            // TODO(paul) externalize keep-alive property for configuration
            //sessionConf.setKeepAlive(true);
            sessionConf.setSendBufferSize(sendBufferSize);
            sessionConf.setReadBufferSize(receiveBufferSize);
            // set an idle time in seconds or disable this via -1
            if (timeout > 0) {
                sessionConf.setIdleTime(IdleStatus.BOTH_IDLE, timeout);
            }
            // QoS
            sessionConf.setTrafficClass(trafficClass);
            // close sessions when the acceptor is stopped
            acceptor.setCloseOnDeactivation(true);
            // requested maximum length of the queue of incoming connections
            ((NioSocketAcceptor) acceptor).setBacklog(64);
            ((NioSocketAcceptor) acceptor).setReuseAddress(true);
            // get the filter chain and add our codec factory
            acceptor.getFilterChain().addLast("protocol", iceCodecFilter);
            // add our handler
            acceptor.setHandler(iceHandler);
            logger.info("Started socket transport");
            if (logger.isTraceEnabled()) {
                logger.trace("Acceptor sizes - send: {} recv: {}", sessionConf.getSendBufferSize(), sessionConf.getReadBufferSize());
            }
            // add ourself to the transports map
            transports.put(id, this);
        }
    }

    /**
     * Adds a socket binding to the acceptor.
     *
     * @param addr
     * @return true if successful and false otherwise
     */
    @Override
    public boolean addBinding(SocketAddress addr) {
        try {
            Future<Boolean> bindFuture = (Future<Boolean>) executor.submit(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    logger.debug("Adding TCP binding: {}", addr);
                    acceptor.bind(addr);
                    // add the port to the bound list
                    if (boundPorts.add(((InetSocketAddress) addr).getPort())) {
                        logger.debug("TCP binding added: {}", addr);
                    }
                    return Boolean.TRUE;
                }

            });
            // wait a maximum of x seconds for this to complete the binding
            return bindFuture.get(acceptorTimeout, TimeUnit.SECONDS);
        } catch (Throwable t) {
            logger.warn("Add binding failed on {}", addr, t);
        }
        return false;
    }

    /** {@inheritDoc} */
    public boolean registerStackAndSocket(StunStack stunStack, IceSocketWrapper iceSocket) {
        logger.debug("registerStackAndSocket - stunStack: {} iceSocket: {} soLinger: {}", stunStack, iceSocket, localSoLinger);
        boolean result = false;
        iceSocket.setId(id);
        // add the stack and wrapper to a map which will hold them until an associated session is opened
        // when opened, the stack and wrapper will be added to the session as attributes
        iceHandler.registerStackAndSocket(stunStack, iceSocket);
        // dont bind if we're already connected
        if (iceSocket.getSession() == null) {
            // get the local address
            TransportAddress localAddress = iceSocket.getTransportAddress();
            // attempt to add a binding to the server
            result = addBinding(localAddress);
        }
        return result;
    }

    /**
     * Sets the socket linger on the current acceptor.
     */
    public void setSoLinger(int soLinger) {
        this.localSoLinger = soLinger;
        // if we've been given a linger property, apply it to the socket; this assumes non-shared acceptors
        ((NioSocketAcceptor) acceptor).getSessionConfig().setSoLinger(localSoLinger);
    }

}
