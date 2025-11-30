/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

import com.red5pro.ice.message.ChannelData;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.socket.IceSocketWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StunException;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * Manages Connectors and MessageProcessor pooling. This class serves as a layer that masks network primitives and provides equivalent STUN
 * abstractions. Instances that operate with the NetAccessManager are only supposed to understand STUN talk and shouldn't be aware of datagrams sockets, etc.
 *
 * @author Emil Ivov
 * @author Aakash Garg
 * @author Boris Grozev
 * @author Paul Gregoire
 */
public class NetAccessManager {

    private static final Logger logger = LoggerFactory.getLogger(NetAccessManager.class);

    private final static boolean isTrace = logger.isTraceEnabled(), isDebug = logger.isDebugEnabled();

    /**
     * Set of Connectors for this access manager.
     */
    private final ConcurrentSkipListSet<Connector> connectors = new ConcurrentSkipListSet<>();

    /**
     * The StunStack which has created this instance, is its owner and is the handler that incoming message requests should be passed to.
     */
    private final StunStack stunStack;

    /**
     * Constructs a NetAccessManager.
     *
     * @param stunStack the StunStack which is creating the new instance, is going to be its owner and is the handler that incoming
     * message requests should be passed to
     */
    NetAccessManager(StunStack stunStack) {
        this.stunStack = stunStack;
    }

    /**
     * Gets the StunStack which has created this instance and is its owner.
     *
     * @return the StunStack which has created this instance and is its owner
     */
    StunStack getStunStack() {
        return stunStack;
    }

    /**
     * Creates and starts a new access point based on the specified socket. If the specified access point has already been installed the method
     * has no effect.
     *
     * @param socket the socket that the access point should use.
     */
    public void buildConnectorLink(IceSocketWrapper socket) {
        logger.debug("addSocket: {}", socket);
        // UDP connections will normally have null remote transport addresses
        buildConnectorLink(socket, socket.getRemoteTransportAddress());
    }

    /**
     * Creates and starts a new access point based on the specified socket. If the specified access point already exists the method has no effect.
     *
     * @param socket the socket that the access point should use
     * @param remoteAddress the remote address the {@link Connector} if its TCP or null if its UDP
     */
    public void buildConnectorLink(IceSocketWrapper socket, TransportAddress remoteAddress) {
        logger.debug("addSocket: {} remote address: {}", socket, remoteAddress);
        if (isDebug) {
            logger.debug("Existing connectors (pre-add): {}", connectors);
        }
        // get local address
        TransportAddress localAddress = socket.getTransportAddress();
        Optional<Connector> connector = connectors.stream().filter(c -> c.getSocket().equals(socket)).findFirst();
        if (connector.isPresent()) {
            logger.info("Not creating a new Connector, due to existing entry for: {} -> {}", localAddress, connector.get().toString());
            // determine if TCP and set remote if not set
            if (socket.isTCP() && connector.get().getRemoteAddress() == null) {
                connector.get().setRemoteAddress(remoteAddress);
            }
        } else {
            if (connectors.add(new Connector(socket, remoteAddress))) {
                logger.info("New connector added");
            }
        }
        if (isDebug) {
            logger.debug("Existing connectors (post-mod): {}", connectors);
        }
    }

    /**
     * Stops and deletes the specified access point.
     *
     * @param localAddress the local address of the connector to remove.
     * @param remoteAddress the remote address of the connector to remote. Use null to match the Connector with no specified remote address.
     */
    public Connector removeConnectorLink(TransportAddress localAddress, TransportAddress remoteAddress) {
        logger.debug("removeSocket: {} remote address: {}", localAddress, remoteAddress);
        if (isDebug) {
            logger.debug("Existing connectors (pre-remove): {}", connectors);
        }
        final boolean isUdp = (Transport.UDP == localAddress.getTransport());
        AtomicReference<Connector> connector = new AtomicReference<>();
        connectors.removeIf(c -> {
            if (c.getListenAddress().equals(localAddress)
                    && (isUdp || (remoteAddress == null || remoteAddress.equals(c.getRemoteAddress())))) {
                connector.set(c);
                return true;
            }
            return false;
        });
        if (isDebug) {
            logger.debug("Existing connectors (post-remove): {}", connectors);
        }
        return connector.get();
    }

    /**
     * Stops NetAccessManager and all of its MessageProcessor.
     */
    public void stop() {
        logger.debug("stop");
        // stop all the connectors
        connectors.forEach(connector -> {
            connector.stop();
        });
        connectors.clear();
    }

    /**
     * Returns the Connector responsible for a particular source address and a particular destination address.
     *
     * @param localAddress the source address.
     * @param remoteAddress the destination address.
     * @return Connector responsible for a given source and destination address otherwise null
     */
    private Connector getConnector(TransportAddress localAddress, TransportAddress remoteAddress) {
        if (isTrace) {
            logger.trace("getConnector - local: {} remote: {}", localAddress, remoteAddress);
        }
        Connector connector = null;
        final boolean isUdp = (Transport.UDP == localAddress.getTransport());
        Optional<Connector> cnOpt = connectors.stream().filter(c -> {
            if (c.getListenAddress().equals(localAddress)) {
                // when both are UDP there is no need to check the remote address
                if (isUdp && (Transport.UDP == c.getListenAddress().getTransport())) {
                    return true;

                } else if (!isUdp && remoteAddress != null) { // tcp and remote address is not null
                    // if the tcp connector has a matching remote address
                    if (remoteAddress.equals(c.getRemoteAddress())) {
                        return true;
                    } else if (c.canNegotiate(remoteAddress)) {
                        c.setRemoteAddress(remoteAddress);
                        logger.debug("Found Connector  with negotiating address   {}", c.toString());
                        return true;
                    } else {
                        // if the tcp connector has no remote address set yet
                        if (c.getRemoteAddress() == null) {
                            // set the remote address since we landed here on incoming data
                            c.setRemoteAddress(remoteAddress);
                            return true;
                        }
                    }
                }
            }
            return false;
        }).findFirst();
        if (cnOpt.isPresent()) {
            connector = cnOpt.get();
        }
        if (isDebug) {
            if (connector != null) {
                logger.debug("Returning connector - local: {} remote: {}", connector.getListenAddress(), connector.getRemoteAddress());
            } else {
                logger.debug("Returning null connector for {}; current entries: {}", localAddress, connectors);
            }
        }
        return connector;
    }

    //--------------- SENDING MESSAGES -----------------------------------------
    /**
     * Sends the specified stun message through the specified access point.
     *
     * @param stunMessage the message to send
     * @param srcAddr the access point to use to send the message
     * @param remoteAddr the destination of the message
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     */
    void sendMessage(Message stunMessage, TransportAddress srcAddr, TransportAddress remoteAddr)
            throws IllegalArgumentException, IOException {
        sendMessage(stunMessage.encode(stunStack), srcAddr, remoteAddr);
    }

    /**
     * Sends the specified stun message through the specified access point.
     *
     * @param channelData the message to send
     * @param srcAddr the access point to use to send the message
     * @param remoteAddr the destination of the message
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     * @throws StunException
     */
    void sendMessage(ChannelData channelData, TransportAddress srcAddr, TransportAddress remoteAddr)
            throws IllegalArgumentException, IOException, StunException {
        boolean pad = srcAddr.getTransport() == Transport.TCP || srcAddr.getTransport() == Transport.TLS;
        sendMessage(channelData.encode(pad), srcAddr, remoteAddr);
    }

    /**
     * Sends the specified bytes through the specified access point.
     *
     * @param bytes the bytes to send
     * @param localAddress the access point to use to send the bytes
     * @param remoteAddress the destination of the message
     * @throws IllegalArgumentException if the descriptor references an access point that had not been installed
     * @throws IOException if an error occurs while sending message bytes through the network socket
     */
    void sendMessage(byte[] bytes, TransportAddress localAddress, TransportAddress remoteAddress)
            throws IllegalArgumentException, IOException {
        Connector connector = getConnector(localAddress, remoteAddress);
        if (connector == null) {
            throw new IllegalArgumentException("No connector for " + localAddress + "->" + remoteAddress);
        }
        if (connector != null) {
            logger.debug("Send message to: {}", remoteAddress);
            connector.sendMessage(bytes, remoteAddress);
        }
    }

}
