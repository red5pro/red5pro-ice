package com.red5pro.ice.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Agent;
import com.red5pro.ice.IceProcessingState;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.RawMessage;
import com.red5pro.ice.stack.StunStack;

/**
 * Handle routing of messages on the ICE socket.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public class IceHandler extends IoHandlerAdapter implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(IceHandler.class);

    private static final Logger sweeperLogger = LoggerFactory.getLogger("ice.handler.sweeper");

    private static boolean isTrace = logger.isTraceEnabled();

    private static boolean isDebug = logger.isDebugEnabled();

    // temporary holding area for stun stacks awaiting session creation
    private static ConcurrentMap<TransportAddress, StunStack> stunStacks = new ConcurrentHashMap<>();

    private static ConcurrentMap<String, Agent> agents = new ConcurrentHashMap<>();

    // temporary holding area for ice sockets awaiting session creation
    private static ConcurrentMap<TransportAddress, IceSocketWrapper> iceSockets = new ConcurrentHashMap<>();

    private ScheduledExecutorService cleanSweeper = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private long sweepJob = 0;

    protected IceHandler() {
        logger.info("Waking up");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanSweeper.shutdown();
        }));

        cleanSweeper.scheduleWithFixedDelay(this, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Registers a StunStack and IceSocketWrapper to the internal maps to wait for their associated IoSession creation.
     *
     * @param stunStack
     * @param iceSocket
     */
    public void registerStackAndSocket(StunStack stunStack, IceSocketWrapper iceSocket) {
        logger.debug("registerStackAndSocket on {} - stunStack: {} iceSocket: {}", this, stunStack, iceSocket);
        TransportAddress addr = iceSocket.getTransportAddress();
        if (stunStack != null) {
            stunStacks.putIfAbsent(addr, stunStack);
            //logger.debug("after stunStacks");
        } else {
            logger.debug("Stun stack for address: {}", stunStacks.get(addr));
        }
        iceSockets.putIfAbsent(addr, iceSocket);
        //logger.debug("exit registerStackAndSocket");
    }

    public void registerAgent(Agent agent) {
        agents.put(agent.getId(), agent);
    }

    /**
     * Returns an IceSocketWrapper for a given address if it exists and null if it doesn't.
     *
     * @param address
     * @return IceSocketWrapper
     */
    public IceSocketWrapper lookupBinding(TransportAddress address) {
        if (isTrace) {
            logger.trace("lookupBinding for address: {} existing bindings: {}", address, iceSockets);
        }
        return iceSockets.get(address);
    }

    /**
     * Returns an IceSocketWrapper for a given remote address if it exists and null if it doesn't.
     *
     * @param remoteAddress
     * @return IceSocketWrapper
     */
    public IceSocketWrapper lookupBindingByRemote(SocketAddress remoteAddress) {
        if (isTrace) {
            logger.debug("lookupBindingByRemote for address: {} existing bindings: {}", remoteAddress, iceSockets);
        }
        IceSocketWrapper iceSocket = null;
        Optional<IceSocketWrapper> result = iceSockets.values().stream().filter(entry -> {
            IoSession session = entry.getSession();
            if (session != null) {
                InetSocketAddress sessionRemoteAddress = (InetSocketAddress) session.getRemoteAddress();
                if (sessionRemoteAddress != null && remoteAddress != null) {
                    // check the port and then also the host address
                    return (sessionRemoteAddress.getPort() == ((InetSocketAddress) remoteAddress).getPort() && sessionRemoteAddress
                            .getAddress().getHostAddress().equals(((InetSocketAddress) remoteAddress).getAddress().getHostAddress()));
                } else {
                    logger.debug("Skipping Look up {}, Ice Socket wrapper RemoteAddress is null for {} {} {}", remoteAddress,
                            entry.getLocalAddress(), entry.getLocalPort(), entry.getTransport());
                }
            } else {
                logger.debug("Skipping Look up {}, IoSession is null for {} {} {}", remoteAddress, entry.getLocalAddress(),
                        entry.getLocalPort(), entry.getTransport());
            }
            return false;
        }).findFirst();
        if (result.isPresent()) {
            iceSocket = result.get();
        }
        return iceSocket;
    }

    /**
     * Returns an StunStack for a given address if it exists and null if it doesn't.
     *
     * @param address
     * @return StunStack
     */
    public StunStack lookupStunStack(TransportAddress address) {
        return stunStacks.get(address);
    }

    /** {@inheritDoc} */
    @Override
    public void sessionCreated(IoSession session) throws Exception {
        logger.trace("Created (session: {}) local: {} remote: {}", session.getId(), session.getLocalAddress(), session.getRemoteAddress());
        Transport transport = session.getTransportMetadata().isConnectionless() ? Transport.UDP : Transport.TCP;
        // set transport type, making it easier to look-up later
        session.setAttribute(IceTransport.Ice.TRANSPORT, transport);
        // get the local address
        InetSocketAddress inetAddr = (InetSocketAddress) session.getLocalAddress();
        TransportAddress addr = new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), transport);
        session.setAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR, addr);

        // XXX we're not adding the IoSession to an IceSocketWrapper until negotiations are complete
        /*
        IceSocketWrapper iceSocket = iceSockets.get(addr);
        if (iceSocket != null) {
            // set the session
            iceSocket.setSession(session);
            // add the socket to the session if its not there already
            if (!session.containsAttribute(IceTransport.Ice.CONNECTION)) {
                session.setAttribute(IceTransport.Ice.CONNECTION, iceSocket);
            }
        } else {
            logger.debug("No ice socket at create for: {}", addr);
        }
        */
        StunStack stunStack = stunStacks.get(addr);
        if (stunStack != null) {
            session.setAttribute(IceTransport.Ice.STUN_STACK, stunStack);
            session.setAttribute(IceTransport.Ice.AGENT, stunStack.getAgent());
            IceSocketWrapper iceSocket = iceSockets.get(addr);
            if (iceSocket != null) {
                logger.debug("New session on Socket id: {}, session {}, current session {}", iceSocket.getId(), session,
                        iceSocket.getSession());
                // No longer setting socket ID here because ice transport might not have set attribute yet.
                if (!session.containsAttribute(IceTransport.Ice.UUID)) {
                    session.setAttribute(IceTransport.Ice.UUID, iceSocket.getId());
                }
                session.setAttribute(IceTransport.Ice.NEGOTIATING_ICESOCKET, iceSocket);
                // XXX create socket registration
                if (transport == Transport.TCP) {
                    iceSocket.addRef();
                    // get the remote address
                    inetAddr = (InetSocketAddress) session.getRemoteAddress();
                    TransportAddress remoteAddress = new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), transport);
                    logger.debug("Socket {} current remote address: {} Session remote address: {}", iceSocket.getId(),
                            iceSocket.getRemoteTransportAddress(), remoteAddress);
                    session.setAttribute(IceTransport.Ice.NEGOTIATING_TRANSPORT_ADDR, remoteAddress);
                    iceSocket.negotiateRemoteAddress(remoteAddress);
                    stunStack.getNetAccessManager().buildConnectorLink(iceSocket, remoteAddress);

                }
            } else {
                // socket was in most cases recently closed or in-process of being closed / cleaned up, so return and exception
                throw new IOException("Connection already closed for: " + session.toString());
            }
        } else {
            logger.debug("No stun stack at create for: {}", addr);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sessionOpened(IoSession session) throws Exception {
        if (isDebug) {
            logger.debug("Opened (session: {}) local: {} remote: {}", session.getId(), session.getLocalAddress(),
                    session.getRemoteAddress());
        }
    }

    private IceSocketWrapper associateSessionToSocket(IoSession session) {
        Transport type = (Transport) session.getAttribute(IceTransport.Ice.TRANSPORT);
        String transportId = (String) session.getAttribute(IceTransport.Ice.UUID);
        if (transportId != null) {
            IceTransport instance = IceTransport.getInstance(type, transportId);
            if (instance != null && instance.associateSession(session)) {
                logger.debug("Associated session {} to socket  {}", session.getId(), transportId);
                return (IceSocketWrapper) session.getAttribute(IceTransport.Ice.CONNECTION);
            } else if (instance == null) {
                logger.debug("Can not associate socket. No transport");
            }
        } else {
            logger.debug("Can not associate socket without transport id");
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        if (isTrace) {
            logger.trace("Message received (session: {}) local: {} remote: {}\nReceived: {}", session.getId(), session.getLocalAddress(),
                    session.getRemoteAddress(), String.valueOf(message));
        }
        IceSocketWrapper iceSocket = (IceSocketWrapper) session.getAttribute(IceTransport.Ice.CONNECTION);
        if (iceSocket != null) {
            if (message instanceof RawMessage) {
                // non-stun message
                iceSocket.offerMessage((RawMessage) message);
            } else {
                logger.debug("Message type: {}", message.getClass().getName());
            }
        } else {
            IceSocketWrapper retry = null;
            if ((retry = associateSessionToSocket(session)) != null) {
                if (message instanceof RawMessage) {
                    // non-stun message
                    retry.offerMessage((RawMessage) message);
                }
            } else {
                logger.debug("Ice socket was not found in session");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        if (message instanceof IoBuffer) {
            if (isTrace) {
                logger.trace("Message sent (session: {}) local: {} remote: {}\nread: {} write: {}", session.getId(),
                        session.getLocalAddress(), session.getRemoteAddress(), session.getReadBytes(), session.getWrittenBytes());
                //logger.trace("Sent: {}", String.valueOf(message));
                byte[] output = ((IoBuffer) message).array();
                if (IceDecoder.isDtls(output)) {
                    logger.trace("Sent - DTLS sequence number: {}", readUint48(output, 5));
                }
            }

            Optional<Object> socket = Optional.ofNullable(session.getAttribute(IceTransport.Ice.CONNECTION));
            if (socket.isPresent()) {
                // update total message/byte counters
                ((IceSocketWrapper) socket.get()).updateWriteCounters(session.getWrittenBytes());

            } else {
                IceSocketWrapper retry = null;
                if ((retry = associateSessionToSocket(session)) != null) {
                    retry.updateWriteCounters(session.getWrittenBytes());
                } else {
                    logger.debug("No socket present for session {} for write counter update", session.getId());
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        if (isTrace) {
            logger.trace("Idle (session: {}) local: {} remote: {}\nread: {} write: {}", session.getId(), session.getLocalAddress(),
                    session.getRemoteAddress(), session.getReadBytes(), session.getWrittenBytes());
        }
        session.closeNow();
    }

    /** {@inheritDoc} */
    @Override
    public void sessionClosed(IoSession session) throws Exception {
        logger.debug("Session closed: {}", session.getId());

        //Were we negotiating?
        IceSocketWrapper iceSocket = (IceSocketWrapper) session.removeAttribute(IceTransport.Ice.NEGOTIATING_ICESOCKET);
        if (iceSocket != null) {
            iceSocket.releaseRef();
            TransportAddress remoteAddress = (TransportAddress) session.removeAttribute(IceTransport.Ice.NEGOTIATING_TRANSPORT_ADDR);
            if (remoteAddress != null) {
                if (iceSocket.negotiationFinished(remoteAddress)) {
                    logger.debug("Negotiation finished for session {} @ {}", session.getId(), remoteAddress);
                }
            }
        }

        // remove any existing reference to an ice socket
        Optional<Object> socket = Optional.ofNullable(session.removeAttribute(IceTransport.Ice.CONNECTION));
        if (socket.isPresent()) {
            // update total message/byte counters
            ((IceSocketWrapper) socket.get()).close(session);
        } else {
            logger.debug("No socket associated with session: {} at close", session.getId());
        }
        super.sessionClosed(session);
    }

    /** {@inheritDoc} */
    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        // XXX to prevent questions about exceptions in the log, the final dtls message will be filtered if it causes an exception
        String causeMessage = cause.getMessage();
        if (causeMessage != null && causeMessage.contains("Hexdump: 15")) {
            // only log it at trace level if we're debugging
            if (isTrace) {
                logger.warn("Exception on session: {}", session.getId(), cause);
            } else {
                logger.warn("Exception on session: {}", session.getId(), cause.getClass().getSimpleName());
            }
        } else if (isTrace) {
            logger.warn("Exception on session: {}", session.getId(), cause);
        } else {
            logger.warn("Exception on session: {}", session.getId(), cause.getClass().getSimpleName());
        }
        session.setAttribute("exception", cause);
        //Look for application API.
        Agent agent = (Agent) session.getAttribute(IceTransport.Ice.AGENT);
        // determine transport type
        Transport transportType = (session.getAttribute(IceTransport.Ice.TRANSPORT) == Transport.TCP) ? Transport.TCP : Transport.UDP;
        InetSocketAddress inetAddr = (InetSocketAddress) session.getLocalAddress();
        TransportAddress addr = new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), transportType);
        if (agent != null) {
            agent.setException(addr, cause);
            if (agent.isClosing() || !agent.isActive()) {
                logger.warn("Session agent is shutting down ");
                return;
            }
        }

        logger.warn("Session: {} exception on transport: {} connection-less? {} address: {}", session.getId(), transportType,
                session.getTransportMetadata().isConnectionless(), addr);
        // get the transport / acceptor identifier
        String id = (String) session.getAttribute(IceTransport.Ice.UUID);
        // get transport by type
        IceTransport transport = IceTransport.getInstance(transportType, id);
        // remove binding
        transport.removeBinding(addr);
        if (!IceTransport.isSharedAcceptor()) {
            // not-shared, kill it
            transport.stop();
        }
        // remove any map entries
        stunStacks.remove(addr);
        IceSocketWrapper iceSocket = iceSockets.remove(addr);
        if (iceSocket == null && session.containsAttribute(IceTransport.Ice.CONNECTION)) {
            iceSocket = (IceSocketWrapper) session.removeAttribute(IceTransport.Ice.CONNECTION);
        }
        if (iceSocket != null) {
            // Invoked when any exception is thrown by user IoHandler implementation or by MINA. If cause is an
            // instance of IOException, MINA will close the connection automatically.
            // handle closing of the socket
            if (!iceSocket.isClosed()) {
                iceSocket.close(session);
            }
        }
    }

    /**
     * Removes an address entry from this handler. This includes the STUN stack and ICE sockets collections.
     *
     * @param addr
     * @return true if removed from sockets list and false if not
     */
    public boolean remove(SocketAddress addr) {
        if (stunStacks.remove(addr) != null) {
            logger.debug("StunStack removed from handler {}", addr);
        }
        if (iceSockets.remove(addr) != null) {
            logger.debug("SocketAddress removed from handler {}", addr);
            return true;
        }
        return false;
    }

    public boolean unregisterAgent(String id) {
        Agent agent = agents.remove(id);
        if (agent != null) {
            if (!agent.hasEolHandler()) {
                Set<Integer> history = agent.getPortAllocationHistory();
                boolean clean = false;
                try {
                    clean = agent.getEndOfLifeStateLogger().call();
                } catch (Exception e) {
                }
                if (!clean) {
                    logger.warn("Unclean exit for {}. Check if these ports are disposed. {}", agent.getLocalUfrag(), history.toString());
                    forceCleanUp(agent);
                }
                try {
                    agent.getNuker().call();
                } catch (Exception e) {
                }
            }
        }
        return agent != null;
    }

    /* From BC TlsUtils for debugging */

    public static int readUint24(byte[] buf, int offset) {
        int n = (buf[offset] & 0xff) << 16;
        n |= (buf[++offset] & 0xff) << 8;
        n |= (buf[++offset] & 0xff);
        return n;
    }

    public static long readUint48(byte[] buf, int offset) {
        int hi = readUint24(buf, offset);
        int lo = readUint24(buf, offset + 3);
        return ((long) (hi & 0xffffffffL) << 24) | (long) (lo & 0xffffffffL);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("sweeper job-" + sweepJob++);
        sweeperLogger.info("Starting");

        sweeperLogger.info("ICE Transport count: {}", IceTransport.transportCount());

        if (!stunStacks.isEmpty()) {
            sweeperLogger.info("---Stun Stacks---");
            stunStacks.forEach((hood, stunna) -> {
                if (stunna != null) {
                    sweeperLogger.info("Stack age: {}, reg-count: {}, has-agent: {}", stunna.getAge(), stunna.getRegistrationCount(),
                            stunna.hasAgent());
                    sweeperLogger.info(hood.toString());
                } else {
                    sweeperLogger.info("No stack for {}", hood);
                }

            });
        } else {
            sweeperLogger.info("No stun stacks to evaluate");
        }
        if (!iceSockets.isEmpty()) {
            sweeperLogger.info("---ICE Sockets---");
            iceSockets.forEach((addy, socket) -> {
                if (socket != null) {
                    sweeperLogger.info(socket.toSweeperInfo());
                    sweeperLogger.info("Socket age: {}, has-StunStack: {}, has-transport: {}", socket.getAge(),
                            stunStacks.containsKey(addy), IceTransport.transportExists(socket.getId()));
                } else {
                    sweeperLogger.info("No socket for {}", addy);
                }
            });
        } else {
            sweeperLogger.info("No ice sockets to evaluate");
        }
        if (!agents.isEmpty()) {
            sweeperLogger.info("--- Agents ---");

            List<Agent> toRemove = new ArrayList<>();


            agents.forEach((id, agent) -> {

                if (agent.getState() == IceProcessingState.WAITING) {
                    sweeperLogger.info("Wating agent {}, age: {}, allocated ports: {}", agent.getLocalUfrag(), agent.getAge(),
                            agent.getPreAllocatedPorts());
                } else if (agent.getState() == IceProcessingState.RUNNING && !agent.isClosing()) {
                    sweeperLogger.info("Active agent {} ice-state: {}, age: {}, ice-age: {}, allocated ports: {}", agent.getLocalUfrag(),
                            agent.getState(), agent.getAge(), agent.getIceAge(), agent.getPreAllocatedPorts());
                } else if (!agent.isClosing()) {
                    sweeperLogger.info("Active agent {} ice-state: {}, age: {}, ice-duration: {}, allocated ports: {}",
                            agent.getLocalUfrag(), agent.getState(), agent.getAge(), agent.getTotalHarvestingTime(),
                            agent.getPreAllocatedPorts());
                } else if (!agent.isActive()) {
                    sweeperLogger.info("Agent is closed {} ice-state: {}, age: {}, ice-duration: {}, allocated ports: {}",
                            agent.getLocalUfrag(), agent.getState(), agent.getAge(), agent.getTotalHarvestingTime(),
                            agent.getPreAllocatedPorts());
                } else if (agent.isClosing()) {
                    sweeperLogger.info("Agent is closing {} ice-state: {}, age: {}, ice-duration: {}, allocated ports: {}",
                            agent.getLocalUfrag(), agent.getState(), agent.getAge(), agent.getTotalHarvestingTime(),
                            agent.getPreAllocatedPorts());
                } else if (agent.mustBeDead()) {
                    toRemove.add(agent);
                    Throwable ce = agent.getClosingError();
                    if (ce != null) {
                        logger.error("Closing error for frag: {}", agent.getLocalUfrag(), ce);
                    }
                    agent.getUncleanExits().forEach((m, t) -> {
                        sweeperLogger.error("Error in agent {} freeing {}, {}", agent.getLocalUfrag(), m, t);
                        sweeperLogger.error("Failed agent {} allocated ports: {}  ports-tried: {}", agent.getLocalUfrag(),
                                agent.getPreAllocatedPorts(), agent.getPortAllocationHistory());
                    });
                }
            });

            toRemove.forEach(agent -> {
                unregisterAgent(agent.getId());

            });
        } else {
            sweeperLogger.info("No ice agents to evaluate");
        }
        sweeperLogger.info("Exiting");

    }

    public void callEolHandlerFor(Agent agent) {
        Agent.EndOfLifeStateHandler handler = agent.getEolHandler();
        if (handler != null) {
            cleanSweeper.schedule(() -> {
                forceCleanUp(agent);
                handler.agentEndOfLife(agent, agent.getEndOfLifeStateLogger(), agent.getNuker());
            }, 200, TimeUnit.MILLISECONDS);
        }
    }

    private void forceCleanUp(Agent agent) {
        logger.debug("Force cleanup , todo!");
    }
}
