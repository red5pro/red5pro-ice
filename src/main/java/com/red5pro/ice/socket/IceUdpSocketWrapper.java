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

import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.nio.IceDecoder;
import com.red5pro.ice.nio.IceUdpTransport;
import com.red5pro.ice.stack.RawMessage;

/**
 * UDP implementation of the IceSocketWrapper.
 *
 * @author Paul Gregoire
 */
public class IceUdpSocketWrapper extends IceSocketWrapper {

    /**
     * Use builder to create instance.
     *
     * @throws IOException
     */
    IceUdpSocketWrapper(TransportAddress transportAddress) throws IOException {
        super(transportAddress);
        logger.debug("IceUdpSocketWrapper  create");
    }

    /** {@inheritDoc} */
    @SuppressWarnings("static-access")
    @Override
    public void send(IoBuffer buf, SocketAddress destAddress) throws IOException {
        if (isSessionClosed()) {
            logger.debug("Connection is closed");
            throw new ClosedChannelException();
        } else {
            // exclude .local hosts since they are local to the browser / client
            if ((((InetSocketAddress) destAddress).getHostString()).endsWith(".local")) {
                throw new IOException("Address not supported: .local");
            }
            if (isTrace) {
                logger.trace("send: {} bytes to: {}", buf.remaining(), destAddress);
            }
            // write future for ensuring write/send
            WriteFuture writeFuture = null;
            try {
                // if no session is set, we're likely in the negotiation phase
                IoSession sess = getSession();
                if (sess == null) {
                    // attempt to pull the session from the transport
                    IceUdpTransport transport = IceUdpTransport.getInstance(transportId);
                    // look up session matching BOTH local and remote addresses to prevent
                    // cross-interface session reuse on multi-homed hosts (RED5DEV-2052)
                    sess = transport.getSessionByLocalAndRemote(transportAddress, destAddress);
                    // if no exact match exists, create a new session for this local+remote pair
                    if (sess == null) {
                        try {
                            logger.debug("No session for local: {} remote: {}, creating", transportAddress, destAddress);
                            sess = transport.createSession(this, destAddress);
                        } catch (Exception e) {
                            logger.warn("Exception creating session for: {}", transportAddress, e);
                        }
                    }
                }
                // if we're not relaying, proceed with normal flow
                if (relayedCandidateConnection == null || !IceDecoder.isTurnMethod(buf.array())) {
                    // ensure that the destination matches the session remote
                    if (sess != null) {
                        //if (isTrace) {
                        //    logger.trace("Destination match for send: {} -> {}", destAddress, sess.getRemoteAddress());
                        //}
                        if (destAddress.equals(sess.getRemoteAddress())) {
                            writeFuture = sess.write(buf, destAddress);
                            writeFuture.addListener(writeListener);
                        }
                    } else {
                        logger.info("Session established, skipping write to: {}", destAddress);
                    }
                } else {
                    if (isTrace) {
                        logger.trace("Relayed send: {} to: {}", buf, destAddress);
                    }
                    relayedCandidateConnection.send(buf, destAddress);
                }
            } catch (Throwable t) {
                logger.warn("Exception attempting to send", t);
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

    @Override
    public Transport getTransport() {
        return Transport.UDP;
    }

    @Override
    public boolean isTCP() {
        return false;
    }

    @Override
    public boolean isUDP() {
        return true;
    }

    @Override
    public String toString() {
        return "IceUdpSocketWrapper [transportAddress=" + transportAddress + ", session=" + getSession() + "]";
    }
}
