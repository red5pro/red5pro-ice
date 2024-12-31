/* See LICENSE.md for license information */
package com.red5pro.ice.harvest;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.UsernameAttribute;
import com.red5pro.ice.nio.IceHandler;
import com.red5pro.ice.nio.IceTransport;
import com.red5pro.ice.nio.IceUdpTransport;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.socket.IceUdpSocketWrapper;
import com.red5pro.ice.stack.RequestListener;
import com.red5pro.ice.stack.StunStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * A class which holds a {@link DatagramSocket} and runs a thread ({@link #thread}) which perpetually reads from it.
 *
 * When a datagram from an unknown source is received, it is parsed as a STUN Binding Request, and if it has a USERNAME attribute, its ufrag is extracted.
 * At this point, an implementing class may choose to create a mapping for the remote address of the datagram, which will be used for further packets
 * from this address.
 *
 * @author Boris Grozev
 * @author Paul Gregoire
 */
public abstract class AbstractUdpListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUdpListener.class);

    /**
     * Returns the list of {@link TransportAddress}es, one for each allowed IP address found on each allowed network interface, with the given port.
     *
     * @param port the UDP port number.
     * @return the list of allowed transport addresses.
     */
    public static List<TransportAddress> getAllowedAddresses(int port) {
        List<TransportAddress> addresses = new LinkedList<>();
        for (InetAddress address : HostCandidateHarvester.getAllAllowedAddresses()) {
            addresses.add(new TransportAddress(address, port, Transport.UDP));
        }
        return addresses;
    }

    /**
     * The map which keeps the known remote addresses and their associated candidateSockets.
     * {@link #thread} is the only thread which adds new entries, while other threads remove entries when candidates are freed.
     */
    protected final Map<SocketAddress, IceUdpSocketWrapper> sockets = new ConcurrentHashMap<>();

    /**
     * The local address that this harvester is bound to.
     */
    protected final TransportAddress localAddress;

    /**
     * Initializes a new SinglePortUdpHarvester instance which is to bind on the specified local address.
     *
     * @param localAddress the address to bind to
     * @throws IOException if initialization fails
     */
    protected AbstractUdpListener(TransportAddress localAddress) throws IOException {
        boolean bindWildcard = !StackProperties.getBoolean(StackProperties.BIND_WILDCARD, false);
        if (bindWildcard) {
            this.localAddress = new TransportAddress((InetAddress) null, localAddress.getPort(), localAddress.getTransport());
        } else {
            this.localAddress = localAddress;
        }
        IceHandler iceHandler = IceTransport.getIceHandler();
        // look for existing socket with the local address
        IceSocketWrapper lookedUpSocket = iceHandler.lookupBinding(this.localAddress);
        // create a stun stack and unconnected udp socket wrapper, then add them to the udp transport
        final IceSocketWrapper iceSocket = (lookedUpSocket != null) ? lookedUpSocket : IceSocketWrapper.build(this.localAddress, null);
        // look for existing stun stack with the local address
        StunStack stunStack = iceHandler.lookupStunStack(this.localAddress);
        if (stunStack == null) {
            stunStack = new StunStack();
        }
        stunStack.addRequestListener(this.localAddress, new RequestListener() {

            @Override
            public void processRequest(StunMessageEvent evt) throws IllegalArgumentException {
                TransportAddress remoteAddress = evt.getRemoteAddress();
                sockets.put(remoteAddress, (IceUdpSocketWrapper) iceSocket);
                UsernameAttribute ua = (UsernameAttribute) evt.getMessage().getAttribute(Attribute.Type.USERNAME);
                if (ua != null) {
                    logger.debug("Username length: {} data length: {}", ua.getUsername().length, ua.getDataLength());
                    String ufrag = new String(ua.getUsername()).split(":")[0];
                    updateCandidate(iceSocket, remoteAddress, ufrag);
                }
            }

        });
        IceUdpTransport.getInstance(iceSocket.getTransportId()).registerStackAndSocket(stunStack, iceSocket);
    }

    /**
     * Looks for a registered ICE candidate, which has a local ufrag of {@code ufrag}, and if one is found it accepts the new socket and adds it to the candidate.
     *
     * @param iceSocket
     * @param remoteAddress
     * @param ufrag
     */
    protected abstract void updateCandidate(IceSocketWrapper iceSocket, InetSocketAddress remoteAddress, String ufrag);

}
