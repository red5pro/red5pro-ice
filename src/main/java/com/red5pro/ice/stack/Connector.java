/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.core.buffer.IoBuffer;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;

/**
 * The Network Access Point is the most outward part of the stack. It is constructed around socket and sends datagrams
 * to the STUN server specified by the original NetAccessPointDescriptor.
 *
 * @author Emil Ivov
 */
class Connector implements Comparable<Connector> {

    /**
     * The socket object that used by this access point to access the network.
     */
    private final IceSocketWrapper sock;

    /**
     * The address that we are listening to.
     */
    private final TransportAddress listenAddress;

    /**
     * The remote address of the socket of this Connector if it is a TCP socket, or null if it is UDP.
     */
    private TransportAddress remoteAddress;

    /**
     * Whether or not the connector is alive (not yet stopped)
     */
    private final AtomicBoolean alive = new AtomicBoolean(true);

    /**
     * Creates a network access point.
     *
     * @param socket the socket that this access point is supposed to use for communication
     * @param remoteAddress the remote address of the socket of this {@link Connector} if it is a TCP socket, or null if it is UDP
     */
    protected Connector(IceSocketWrapper socket, TransportAddress remoteAddress) {
        this.sock = socket;
        this.remoteAddress = remoteAddress;
        listenAddress = socket.getTransportAddress();
    }

    /**
     * Returns the DatagramSocket that contains the port and address associated with this access point.
     *
     * @return the DatagramSocket associated with this AP.
     */
    protected IceSocketWrapper getSocket() {
        return sock;
    }

    /**
     * Returns alive status.
     *
     * @return true if alive and false if stopped
     */
    public boolean isAlive() {
        return alive.get();
    }

    /**
     * Makes the access point stop listening on its socket.
     */
    protected void stop() {
        if (alive.compareAndSet(true, false)) {
            // stun stack also calls socket close as part of the process
            sock.close();
        }
    }

    /**
     * Sends message through this access point's socket.
     *
     * @param message the bytes to send
     * @param address message destination
     * @throws IOException if an exception occurs while sending the message
     */
    void sendMessage(byte[] message, TransportAddress address) throws IOException {
        // update stun/turn message/byte counters
        sock.updateSTUNWriteCounters((message != null ? message.length : 0));
        // send the message
        sock.send(IoBuffer.wrap(message), address);
    }

    /**
     * Returns the TransportAddress that this access point is bound on.
     *
     * @return the TransportAddress associated with this AP.
     */
    TransportAddress getListenAddress() {
        return listenAddress;
    }

    /**
     * Allow setting remote address for TCP sockets.
     *
     * @param remoteAddress
     */
    void setRemoteAddress(TransportAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /**
     * Returns the remote TransportAddress or null if none is specified.
     *
     * @return the remote TransportAddress or null if none is specified.
     */
    TransportAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public int compareTo(Connector that) {
        return sock.compareTo(that.sock);
    }

    /**
     * Returns a String representation of the object.
     *
     * @return String
     */
    @Override
    public String toString() {
        return "ice4j.Connector@" + listenAddress;
    }

}
