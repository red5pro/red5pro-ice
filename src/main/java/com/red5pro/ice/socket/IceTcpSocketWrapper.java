/* See LICENSE.md for license information */
package com.red5pro.ice.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import com.red5pro.ice.nio.IceDecoder;
import com.red5pro.ice.stack.RawMessage;

import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * TCP implementation of the IceSocketWrapper.
 *
 * @author Paul Gregoire
 */
public class IceTcpSocketWrapper extends IceSocketWrapper {

    /**
     * Use builder to create instance.
     *
     * @throws IOException
     */
    IceTcpSocketWrapper(TransportAddress transportAddress) throws IOException {
        super(transportAddress);
    }

    /** {@inheritDoc} */
    @Override
    public void send(IoBuffer buf, SocketAddress destAddress) throws IOException {
        if (isClosed()) {
            logger.debug("Connection is closed");
            throw new ClosedChannelException();
        } else {
            // exclude .local hosts since they are local to the browser / client
            if ((((InetSocketAddress) destAddress).getHostString()).endsWith(".local")) {
                throw new IOException("Address not supported: .local");
            }
            if (isTrace) {
                logger.trace("send: {} to: {}", buf, destAddress);
            }
            WriteFuture writeFuture = null;
            try {
                // if we're not relaying, proceed with normal flow
                if (relayedCandidateConnection == null || IceDecoder.isTurnMethod(buf.array())) {
                    IoSession sess = getSession();
                    if (sess != null) {
                        // ensure that the destination matches the session remote
                        if (destAddress.equals(sess.getRemoteAddress())) {
                            writeFuture = sess.write(pad(buf));
                            writeFuture.addListener(writeListener);
                        } else {
                            // if the destination doesnt match, this isn't the right ice socket
                            logger.debug("Destination {} doesnt match remote: {}", destAddress, sess.getRemoteAddress());
                            throw new IOException(String.format("Session not available for destination: %s", destAddress.toString()));
                        }
                    } else {
                        logger.debug("No session for: {} to: {}; closing? {}", transportAddress, destAddress, closed);
                    }
                } else {
                    if (isTrace) {
                        logger.trace("Relayed send: {} to: {}", buf, destAddress);
                    }
                    try {
                        relayedCandidateConnection.send(buf, destAddress);
                    } catch (Throwable t) {
                        logger.warn("Exception attempting to relay", t);
                    }
                }
            } finally {
                if (writeFuture != null) {
                    writeFuture.removeListener(writeListener);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void send(DatagramPacket p) throws IOException {
        //if (isTrace) {
        //    logger.trace("send: {}", p);
        //}
        send(IoBuffer.wrap(p.getData(), p.getOffset(), p.getLength()), p.getSocketAddress());
    }

    /** {@inheritDoc} */
    @Override
    public void receive(DatagramPacket p) throws IOException {
        if (rawMessageQueue != null) {
            RawMessage message = rawMessageQueue.poll();
            if (message != null) {
                p.setData(message.getBytes(), 0, message.getMessageLength());
                p.setSocketAddress(message.getRemoteAddress());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public RawMessage read() {
        return rawMessageQueue != null ? rawMessageQueue.poll() : null;
    }

    /**
     * Pad the data for TCP per RFC 4571.
     *
     * @param buf non-padded source data
     * @return padded data with length
     */
    private IoBuffer pad(IoBuffer buf) {
        // pad the buffer for tcp transmission
        int len = buf.limit();
        IoBuffer data = IoBuffer.allocate(len + 2);
        data.put((byte) ((len >> 8) & 0xff));
        data.put((byte) (len & 0xff));
        data.put(buf);
        data.flip();
        return data;
    }

    /** {@inheritDoc} */
    @Override
    public InetAddress getLocalAddress() {
        return transportAddress.getAddress();
    }

    /** {@inheritDoc} */
    @Override
    public int getLocalPort() {
        return transportAddress.getPort();
    }

    /** {@inheritDoc} */
    @Override
    public void setSession(IoSession newSession) {
        super.setSession(newSession);
        // update the remote address with the one from the current session
        if ("dummy".equals(newSession.getTransportMetadata().getName())) {
            remoteTransportAddress = null;
        } else {
            remoteTransportAddress = new TransportAddress((InetSocketAddress) newSession.getRemoteAddress(), Transport.TCP);
        }
    }

    @Override
    public String toString() {
        return "IceTcpSocketWrapper [transportAddress=" + transportAddress + ", session=" + getSession() + "]";
    }

}
