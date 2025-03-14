package com.red5pro.ice.nio;

import java.net.InetSocketAddress;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;

/**
 * IceTransport for TCP connections.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public class IceTcpTransport extends IceTransport {

    private static AtomicReference<IoProcessor<NioSession>> processingPool = new AtomicReference<IoProcessor<NioSession>>(null);

    {//Non static
        if (sharedIoProcessor && processingPool.get() == null) {
            processingPool.compareAndSet(null, new SimpleIoProcessorPool<NioSession>(NioProcessor.class, ioThreads));
        }
    }
    /**
     * Socket linger for this instance.
     */
    private int localSoLinger = -1;


    /**
     * Creates the i/o handler and nio acceptor; ports and addresses are bound.
     */
    IceTcpTransport() {
        logger.info("Creating Transport. id: {} policy: {} accept timeout: {}s idle timeout: {}s I/O threads: {}", id,
                StunStack.getDefaultAcceptorStrategy().toString(), acceptorTimeout, timeout, ioThreads);
        // add ourself to the transports map
        transports.put(id, this);
    }

    /**
     * Returns an instance of this transport.
     *
     * @param id transport / acceptor identifier
     * @return IceTransport
     */
    public static IceTcpTransport getInstance(String id) {
        IceTcpTransport instance = null;
        boolean isShared = false;
        boolean isNew = false;
        // IceTransport creation strategy
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
                instance = (IceTcpTransport) it;
            }
        }

        if (isNew || isShared) {
            if (isShared) {
                // loop through transport and if none are found for TCP, create a new one
                for (Entry<String, IceTransport> entry : transports.entrySet()) {
                    if (entry.getValue() instanceof IceTcpTransport && entry.getValue().isShared()) {
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
        // Assume this transport is new and create an acceptor if none exists for the instance
        if (instance != null && instance.getAcceptor() == null) {
            if (isNew || isShared) {// AcceptorStrategy as string id
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
            if (!sharedIoProcessor) {
                acceptor = new NioSocketAcceptor(ioThreads);
            } else {
                acceptor = new NioSocketAcceptor(ioExecutor, processingPool.get());
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
                    logger.debug("Acceptor sessionCreated: {} for ice-tcp-transport id: {}", session, id);
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
                Future<Long> bindFuture = (Future<Long>) ioExecutor.submit(new Callable<Long>() {

                    @Override
                    public Long call() throws Exception {
                        logger.debug("Adding TCP binding: {}", addr);
                        acceptor.bind(addr);
                        logger.debug("TCP Bound: {}", addr);
                        Long rsvp = cacheBoundAddressInfo(socketUUID, (InetSocketAddress) addr, ((InetSocketAddress) addr).getPort());
                        logger.debug("TCP binding added: {}", addr);
                        return rsvp;
                    }

                });
                // wait a maximum of x seconds for this to complete the binding
                return bindFuture.get(acceptorTimeout, TimeUnit.SECONDS);
            }
        } catch (Throwable t) {
            logger.warn("Add binding failed on {}", addr, t);
        } finally {
            if (!acceptor.getLocalAddresses().contains(addr)) {
                logger.warn("Removing ref {}", addr);
                myBoundAddresses.remove(addr);
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    public boolean registerStackAndSocket(StunStack stunStack, IceSocketWrapper iceSocket) {
        logger.debug("registerStackAndSocket - stunStack: {} iceSocket: {} soLinger: {}", stunStack, iceSocket, localSoLinger);
        boolean result = false;
        iceSocket.setTransportId(id);
        // add the stack and wrapper to a map which will hold them until an associated session is opened
        // when opened, the stack and wrapper will be added to the session as attributes
        iceHandler.registerStackAndSocket(stunStack, iceSocket);
        // dont bind if we're already connected
        if (iceSocket.getSession() == null) {
            // get the local address
            TransportAddress localAddress = iceSocket.getTransportAddress();
            // attempt to add a binding to the server
            Long rsvp = addBinding(iceSocket.getId(), localAddress);
            result = rsvp != null;
            iceSocket.setRsvp(rsvp);
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

    @Override
    public Transport getTransport() {
        return Transport.TCP;
    }

    /**
     * Returns true if 'it' is not null and is not an IceTcpTransport
     * @param it extension of IceTransport or null
     * @return boolean true if parameter is not null and is not an IceTcpTransport
     */
    private static boolean thisIsSomethingButNot(IceTransport it) {
        if (it != null) {
            return !IceTcpTransport.class.isInstance(it);
        }
        return false;
    }

    @Override
    public Transport getType() {
        return Transport.TCP;
    }
}
