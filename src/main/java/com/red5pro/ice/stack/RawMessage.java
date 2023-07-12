/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.net.SocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import com.red5pro.ice.TransportAddress;

/**
 * The class represents a binary STUN message as well as the address and port of the host that sent it and the address 
 * and port where it was received (locally).
 *
 * @author Emil Ivov
 */
public class RawMessage {
    /**
     * The message itself.
     */
    private final byte[] messageBytes;

    /**
     * The address and port where the message was sent from.
     */
    private final TransportAddress remoteAddress;

    /**
     * The address that this message was received on.
     */
    private final TransportAddress localAddress;

    /**
     * Constructs a raw message with the specified field values. All parameters are cloned before being assigned to class members.
     *
     * @param messageBytes the message itself
     * @param remoteAddress the address where the message came from
     * @param localAddress the TransportAddress that the message was received on
     *
     * @throws NullPointerException if one or more of the parameters were null.
     */
    private RawMessage(byte[] messageBytes, TransportAddress remoteAddress, TransportAddress localAddress) {
        this.messageBytes = messageBytes;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    /**
     * Returns the message itself.
     *
     * @return a binary array containing the message data.
     */
    public byte[] getBytes() {
        return messageBytes;
    }

    /**
     * Returns the message length.
     *
     * @return a the length of the message.
     */
    public int getMessageLength() {
        return messageBytes != null ? messageBytes.length : 0;
    }

    /**
     * Returns the address and port of the host that sent the message
     *
     * @return the [address]:[port] pair that sent the message.
     */
    public TransportAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    /**
     * Returns the address that this message was received on.
     *
     * @return the address that this message was received on.
     */
    public TransportAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Returns an IoBuffer wrapping the message bytes. The message bytes are not copied, so any changes will be reflected in both objects.
     * 
     * @return IoBuffer
     */
    public IoBuffer toIoBuffer() {
        return IoBuffer.wrap(messageBytes, 0, messageBytes.length);
    }

    @Override
    public String toString() {
        return "RawMessage [localAddress=" + localAddress + ", remoteAddress=" + remoteAddress + ", length=" + messageBytes.length + "]";
    }

    /**
     * Use builder pattern to allow creation of immutable RawMessage instances, from outside the current package.
     *
     * @param messageBytes the message itself
     * @param remoteAddress the address where the message came from
     * @param localAddress the TransportAddress that the message was received on
     * @param copy to copy or not to copy the bytes coming in
     * @return RawMessage instance
     */
    public static RawMessage build(byte[] messageBytes, TransportAddress remoteAddress, TransportAddress localAddress, boolean copy) {
        // make a copy of the message bytes for the new raw message
        if (copy) {
            int bufLength = messageBytes.length;
            byte[] buf = new byte[bufLength];
            System.arraycopy(messageBytes, 0, buf, 0, bufLength);
            return new RawMessage(buf, remoteAddress, localAddress);
        } else {
            return new RawMessage(messageBytes, remoteAddress, localAddress);
        }
    }

    /**
     * Use builder pattern to allow creation of immutable RawMessage instances, from outside the current package.
     *
     * @param messageBytes the message itself
     * @param remoteAddress the SocketAddress where the message came from
     * @param localAddress the SocketAddress that the message was received on
     * @return RawMessage instance
     */
    public static RawMessage build(byte[] messageBytes, SocketAddress remoteAddress, SocketAddress localAddress) {
        return RawMessage.build(messageBytes, (TransportAddress) remoteAddress, (TransportAddress) localAddress, true);
    }
    
    /**
     * Use builder pattern to allow creation of immutable RawMessage instances, from outside the current package.
     *
     * @param messageBytes the message itself
     * @param remoteAddress the SocketAddress where the message came from
     * @param localAddress the SocketAddress that the message was received on
     * @param copy to copy or not to copy the bytes coming in
     * @return RawMessage instance
     */
    public static RawMessage build(byte[] messageBytes, SocketAddress remoteAddress, SocketAddress localAddress, boolean copy) {
        return RawMessage.build(messageBytes, (TransportAddress) remoteAddress, (TransportAddress) localAddress, copy);
    }

}
