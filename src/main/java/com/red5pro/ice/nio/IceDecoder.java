package com.red5pro.ice.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderAdapter;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.UsernameAttribute;
import com.red5pro.ice.nio.IceTransport.Ice;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.socket.SocketClosedException;
import com.red5pro.ice.stack.RawMessage;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.ice.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * This class handles the ice decoding.
 *
 * @author Paul Gregoire
 */
public class IceDecoder extends ProtocolDecoderAdapter {

    private static final Logger logger = LoggerFactory.getLogger(IceDecoder.class);

    private static final boolean isTrace = logger.isTraceEnabled();

    private static final boolean isDebug = logger.isDebugEnabled();

    /**
     * Length of a DTLS record header.
     */
    private static final int DTLS_RECORD_HEADER_LENGTH = 13;

    /**
     * Holder of incomplete TCP frames.
     */
    class FrameChunk {

        byte temporaryMSB;

        int totalLength;

        IoBuffer chunk;

        FrameChunk(byte msb) {
            logger.debug("Frame chunk msb: {}", msb);
            temporaryMSB = msb;
        }

        FrameChunk(int frameLength) {
            logger.debug("Frame chunk target size: {}", frameLength);
            // keep track of the frame length
            totalLength = frameLength;
            // does not contain the tcp framing length bytes at the start
            chunk = IoBuffer.allocate(frameLength);
        }

        FrameChunk(int frameLength, IoBuffer in) {
            logger.debug("Frame chunk target size: {}", frameLength);
            // keep track of the frame length
            totalLength = frameLength;
            // does not contain the tcp framing length bytes at the start
            chunk = IoBuffer.allocate(frameLength);
            // prevent buffer overflow by trying to get more than frameLength
            int remaining = in.remaining();
            // dont try to get/put 0 bytes
            if (remaining > 0) {
                byte[] b = new byte[Math.min(frameLength, remaining)];
                in.get(b);
                chunk.put(b);
            }
        }

        void setFrameLength(int frameLength) {
            // keep track of the frame length
            totalLength = frameLength;
            // does not contain the tcp framing length bytes at the start
            chunk = IoBuffer.allocate(frameLength);
        }

        // we're complete when no room remains
        boolean isComplete() {
            //logger.trace("Frame chunk has {} remaining", chunk.remaining());
            return chunk != null && !chunk.hasRemaining();
        }

        void reset() {
            totalLength = 0;
            chunk.clear();
        }

    }

    @Override
    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        if (isTrace) {
            logger.trace("Decode start pos: {} session: {} input: {}", in.position(), session.getId(), in);
        } else if (isDebug) {
            logger.debug("Decode session: {}", session);
        }
        IceSocketWrapper iceSocket = null;
        // determine the transport in-use
        Transport transport = (session.getTransportMetadata().isConnectionless() ? Transport.UDP : Transport.TCP);
        //logger.trace("Decoding: {}", in);
        ensureTransportAddressesCached(session, transport);
        // get the incoming bytes
        byte[] buf = null;
        // TCP has a 2b prefix containing its size per RFC4571 formatted frame, UDP is simply the incoming data size so we start with that
        int frameLength = in.remaining();
        //logger.trace("Remaining at start: {}", frameLength);
        // get the socket which may be null if the associated candidate hasn't been nominated yet
        // else if the ice socket is not in the session yet, attempt to pull it from those registered in the handler
        iceSocket = (IceSocketWrapper) Optional.ofNullable(session.getAttribute(Ice.CONNECTION)).orElse(
                IceTransport.getIceHandler().lookupBinding((TransportAddress) session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR)));
        // if the socket is valid for processing input
        if (iceSocket == null || iceSocket.isClosed()) {
            logger.warn("Ice socket missing in session or closed: {}", session);
            throw new SocketClosedException("Socket closed or wrapper unavailable");
        }
        // if we're TCP (not UDP), grab the size and advance the position
        if (transport != Transport.UDP) {
            // set the current socket wrapper on this session if its missing
            if (!session.containsAttribute(Ice.CONNECTION)) {
                session.setAttribute(Ice.CONNECTION, iceSocket);
            }
            // set this session on to the socket if its missing
            if (iceSocket.getSession() == null) {
                iceSocket.setSession(session);
            }
            // loop reading input until no usable bytes remain
            checkFrameComplete:
            do {
                //logger.trace("Remaining at loop start: {}", in.remaining());
                // check for an existing frame chunk first
                FrameChunk frameChunk = (FrameChunk) session.getAttribute(Ice.TCP_BUFFER);
                if (frameChunk != null) {
                    // check for completed
                    if (frameChunk.isComplete()) {
                        // flip for reading
                        frameChunk.chunk.flip();
                        // size buf for reading all the frame chunk data
                        buf = new byte[frameChunk.totalLength];
                        // get the frame
                        frameChunk.chunk.get(buf);
                        // now clear / reset
                        frameChunk.reset();
                        // clear session local
                        session.removeAttribute(Ice.TCP_BUFFER);
                        // if the socket is valid for processing input
                        if (iceSocket != null && !iceSocket.isClosed()) {
                            // send a buffer of bytes for further processing / handling
                            process(session, iceSocket, buf);
                        } else {
                            logger.warn("No ice socket in session, closing: {}", session);
                            throw new SocketClosedException("Socket closed or wrapper unavailable");
                        }
                        // no more frame chunks, so regular processing will proceed
                        continue checkFrameComplete;
                    } else {
                        // check existing frame chunk without an iobuffer, which means its length must be recalculated
                        if (frameChunk.chunk == null) {
                            frameLength = ((frameChunk.temporaryMSB & 0xFF) << 8) | (in.get() & 0xFF);
                            frameChunk.setFrameLength(frameLength);
                            //logger.trace("Updated frame chunk length: {}", frameLength);
                        }
                        // add to an existing incomplete frame
                        int remaining = in.remaining();
                        buf = new byte[Math.min(remaining, frameChunk.chunk.remaining())];
                        in.get(buf);
                        frameChunk.chunk.put(buf);
                        // restart at the top of the loop checking to see if the frame is now complete and proceed appropriately
                        //logger.debug("Existing frame chunk was appended, complete? {} in remaining: {}", frameChunk.isComplete(), in.remaining());
                        // nothing should remain in the input at this point
                        continue checkFrameComplete;
                    }
                } else {
                    // no frame chunks, handle input
                    int remaining = in.remaining();
                    // we need at least 2 bytes for the frame length
                    if (remaining > 1) {
                        // get the frame length
                        frameLength = ((in.get() & 0xFF) << 8) | (in.get() & 0xFF);
                        //logger.trace("Frame length: {}", frameLength);
                        // update remaining (since we just grabbed 2 bytes)
                        remaining -= 2;
                        // if the frame length is greater than the remaining bytes, we'll have to buffer them until we get the full frame
                        if (remaining < frameLength) {
                            if (remaining > 0) {
                                //logger.debug("Creating new frame chunk with data: {}", remaining);
                                session.setAttribute(Ice.TCP_BUFFER, new FrameChunk(frameLength, in));
                                //logger.debug("New frame chunk, complete? {} in remaining: {}", tcpFrameChunk.get().isComplete(), in.remaining());
                                // nothing should remain in the input at this point
                                continue checkFrameComplete;
                            } else {
                                //logger.warn("Creating new frame chunk without data: {}", remaining);
                                session.setAttribute(Ice.TCP_BUFFER, new FrameChunk(frameLength));
                            }
                        } else {
                            //logger.warn("Creating new frame with data: {}", remaining);
                            // read as much as we have
                            buf = new byte[frameLength];
                            // get the bytes into our buffer
                            in.get(buf);
                            // if the socket is valid for processing input
                            if (iceSocket != null && !iceSocket.isClosed()) {
                                // send a buffer of bytes for further processing / handling
                                process(session, iceSocket, buf);
                            } else {
                                logger.warn("No ice socket in session, closing: {}", session);
                                throw new SocketClosedException("Socket closed or wrapper unavailable");
                            }
                        }
                    } else {
                        // special case were we only have a single byte, so not big enough for a length determination
                        //logger.warn("Creating new frame chunk without sizing or data");
                        session.setAttribute(Ice.TCP_BUFFER, new FrameChunk(in.get()));
                    }
                }
            } while (in.hasRemaining());
            if (isTrace) {
                logger.trace("All TCP input data decoded");
            }
        } else {
            // do udp
            //logger.trace("Decode frame length: {} buffer length: {}", frameLength, buf.length);
            // STUN messages are at least 20 bytes and DTLS are 13+
            if (frameLength > DTLS_RECORD_HEADER_LENGTH) {
                // send a buffer of bytes for further processing / handling
                process(session, iceSocket, in, frameLength);
            } else {
                // there was not enough data in the buffer to parse - this should never happen
                logger.warn("Not enough data in the buffer to parse: {} for session: {}", in, session);
                // throw an exception to close the session since its sending invalid data and recovery is unlikely
                throw new IOException("Invalid buffer length, not encough data to parse DTLS or STUN");
            }
        }
        buf = null;
    }

    /**
     * Ensure that local and remote TransportAddresses are cached in the session. Optimize by doing this only once per session.
     *
     * @param session
     * @param transport
     */
    private static void ensureTransportAddressesCached(IoSession session, Transport transport) {
        // SocketAddress from session are InetSocketAddress which fail cast to TransportAddress, so handle there here
        if (session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR) == null) {
            SocketAddress localAddr = session.getLocalAddress();
            if (localAddr instanceof InetSocketAddress) {
                InetSocketAddress inetAddr = (InetSocketAddress) localAddr;
                localAddr = new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), transport);
                session.setAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR, localAddr);
            }
        }
        if (session.getAttribute(IceTransport.Ice.REMOTE_TRANSPORT_ADDR) == null) {
            SocketAddress remoteAddr = session.getRemoteAddress();
            if (remoteAddr instanceof InetSocketAddress) {
                InetSocketAddress inetAddr = (InetSocketAddress) remoteAddr;
                remoteAddr = new TransportAddress(inetAddr.getAddress(), inetAddr.getPort(), transport);
                session.setAttribute(IceTransport.Ice.REMOTE_TRANSPORT_ADDR, remoteAddr);
            }
        }
        if (isTrace) {
            logger.trace("({}) transport addresses local: {} remote: {}", transport,
                    session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR),
                    session.getAttribute(IceTransport.Ice.REMOTE_TRANSPORT_ADDR));
        }
    }

    /**
     * Process the given bytes for handling as STUN, DTLS, or data (usually rtp/rtcp). Incoming webrtc packets in udp contain only one message,
     * in tcp they may come in as a whole, fragments, or any combo of the two as well as multiple messages.
     *
     * @param session
     * @param iceSocket
     * @param in incoming I/O buffer
     * @param frameLength length of the current input frame
     */
    public static void process(IoSession session, IceSocketWrapper iceSocket, IoBuffer in, int frameLength) {
        SocketAddress localAddr = (TransportAddress) session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR);
        SocketAddress remoteAddr = (TransportAddress) session.getAttribute(IceTransport.Ice.REMOTE_TRANSPORT_ADDR);
        // create a buffer to extract data into
        byte[] buf = new byte[frameLength];
        // get the bytes into our buffer
        in.get(buf);
        // if special TURN processing is needed, we'll have to separate it out to be run first since TURN messages are STUN messages
        RawMessage message = null;
        if ((isStun(buf) && isStunMethod(buf)) || (isTurn(buf) && isTurnMethod(buf))) {
            if (isTrace) {
                logger.trace("Dispatching a STUN message");
            }
            StunStack stunStack = (StunStack) session.getAttribute(Ice.STUN_STACK);
            if (stunStack != null) {
                try {
                    // create a message
                    message = RawMessage.build(buf, remoteAddr, localAddr, false);
                    Message stunMessage = Message.decode(message.getBytes(), 0, message.getMessageLength());
                    if (isDebug) {
                        logger.debug("Message: {}", stunMessage);
                        stunMessage.getAttributes().forEach(attr -> {
                            logger.debug("Attribute: {}", attr);
                        });
                    }
                    // handling of stun/turn messages without an icesocket should be allowed to proceed
                    stunStack.handleMessageEvent(new StunMessageEvent(stunStack, message, stunMessage));
                } catch (Exception ex) {
                    logger.warn("Failed to decode a stun message!", ex);
                }
            } else {
                logger.warn("Stun stack was null for session: {}, cannot decode STUN messages", session.getId());
                session.closeNow();
            }
        } else if (isDtls(buf)) {
            int offset = 0;
            do {
                if (isTrace) {
                    short contentType = (short) (buf[offset] & 0xff);
                    if (contentType == DtlsContentType.handshake) {
                        short messageType = (short) (buf[offset + 13] & 0xff);
                        logger.trace("DTLS handshake message type: {}", getMessageType(messageType));
                    }
                }
                // get the length of the dtls record
                int dtlsRecordLength = (buf[offset + 11] & 0xff) << 8 | (buf[offset + 12] & 0xff);
                byte[] record = new byte[dtlsRecordLength + DTLS_RECORD_HEADER_LENGTH];
                System.arraycopy(buf, offset, record, 0, record.length);
                if (isTrace) {
                    String dtlsVersion = getDtlsVersion(buf, 0, buf.length);
                    logger.trace("Queuing DTLS {} length: {} message: {}", dtlsVersion, dtlsRecordLength, Utils.toHexString(record));
                }
                // create a message
                message = RawMessage.build(record, remoteAddr, localAddr, false);
                if (iceSocket.offerMessage(message)) {
                    // increment the offset
                    offset += record.length;
                    logger.trace("Offset: {}", offset);
                } else {
                    message = null;
                }
            } while (offset < (buf.length - DTLS_RECORD_HEADER_LENGTH));
        } else {
            // this should catch anything else not identified as stun or dtls
            message = RawMessage.build(buf, remoteAddr, localAddr, false);
            if (!iceSocket.offerMessage(message)) {
                message = null;
            }
        }
    }

    /**
     * Process the given bytes for handling as STUN, DTLS, or data (usually rtp/rtcp). Incoming webrtc packets in udp contain only one message,
     * in tcp they may come in as a whole, fragments, or any combo of the two as well as multiple messages.
     *
     * @param session
     * @param iceSocket
     * @param buf
     */
    public static void process(IoSession session, IceSocketWrapper iceSocket, byte[] buf) {
        SocketAddress localAddr = (TransportAddress) session.getAttribute(IceTransport.Ice.LOCAL_TRANSPORT_ADDR);
        SocketAddress remoteAddr = (TransportAddress) session.getAttribute(IceTransport.Ice.REMOTE_TRANSPORT_ADDR);
        // if special TURN processing is needed, we'll have to separate it out to be run first since TURN messages are STUN messages
        RawMessage message = null;
        if ((isStun(buf) && isStunMethod(buf)) || (isTurn(buf) && isTurnMethod(buf))) {
            if (isTrace) {
                logger.trace("Dispatching a STUN message");
            }
            StunStack stunStack = (StunStack) session.getAttribute(Ice.STUN_STACK);
            if (stunStack != null) {
                try {
                    // create a message
                    message = RawMessage.build(buf, remoteAddr, localAddr);
                    Message stunMessage = Message.decode(message.getBytes(), 0, message.getMessageLength());
                    if (isDebug) {
                        logger.debug("Message: {}", stunMessage);
                        stunMessage.getAttributes().forEach(attr -> {
                            logger.debug("Attribute: {}", attr);
                        });
                    }
                    // handling of stun/turn messages without an icesocket should be allowed to proceed
                    stunStack.handleMessageEvent(new StunMessageEvent(stunStack, message, stunMessage));
                } catch (Exception ex) {
                    logger.warn("Failed to decode a stun message!", ex);
                }
            } else {
                logger.warn("Stun stack was null for session: {}, cannot decode STUN messages", session.getId());
                session.closeNow();
            }
        } else if (isDtls(buf)) {
            int offset = 0;
            do {
                if (isTrace) {
                    short contentType = (short) (buf[offset] & 0xff);
                    if (contentType == DtlsContentType.handshake) {
                        short messageType = (short) (buf[offset + 13] & 0xff);
                        logger.trace("DTLS handshake message type: {}", getMessageType(messageType));
                    }
                }
                // get the length of the dtls record
                int dtlsRecordLength = (buf[offset + 11] & 0xff) << 8 | (buf[offset + 12] & 0xff);
                byte[] record = new byte[dtlsRecordLength + DTLS_RECORD_HEADER_LENGTH];
                System.arraycopy(buf, offset, record, 0, record.length);
                if (isTrace) {
                    String dtlsVersion = getDtlsVersion(buf, 0, buf.length);
                    logger.trace("Queuing DTLS {} length: {} message: {}", dtlsVersion, dtlsRecordLength, Utils.toHexString(record));
                }
                // create a message
                message = RawMessage.build(record, remoteAddr, localAddr);
                if (iceSocket.offerMessage(message)) {
                    // increment the offset
                    offset += record.length;
                    logger.trace("Offset: {}", offset);
                } else {
                    message = null;
                }
            } while (offset < (buf.length - DTLS_RECORD_HEADER_LENGTH));
        } else {
            // this should catch anything else not identified as stun or dtls
            message = RawMessage.build(buf, remoteAddr, localAddr);
            if (!iceSocket.offerMessage(message)) {
                message = null;
            }
        }
    }

    /**
     * Determines whether data in a byte array represents a STUN message.
     *
     * @param buf the bytes to check
     * @return true if the bytes look like STUN, otherwise false
     */
    public static boolean isStun(byte[] buf) {
        // If this is a STUN packet
        boolean isStunPacket = false;
        // All STUN messages MUST start with a 20-byte header followed by zero or more Attributes
        if (buf.length >= 20) {
            // If the MAGIC COOKIE is present this is a STUN packet (RFC5389 compliant).
            if (buf[4] == Message.MAGIC_COOKIE[0] && buf[5] == Message.MAGIC_COOKIE[1] && buf[6] == Message.MAGIC_COOKIE[2]
                    && buf[7] == Message.MAGIC_COOKIE[3]) {
                isStunPacket = true;
            } else {
                // Else, this packet may be a STUN packet (RFC3489 compliant). To determine this, we must continue the checks.
                // The most significant 2 bits of every STUN message MUST be zeroes.  This can be used to differentiate STUN packets from
                // other protocols when STUN is multiplexed with other protocols on the same port.
                byte b0 = buf[0];
                boolean areFirstTwoBitsValid = ((b0 & 0xC0) == 0);
                // Checks if the length of the data correspond to the length field of the STUN header. The message length field of the
                // STUN header does not include the 20-byte of the STUN header.
                int total_header_length = ((((int) buf[2]) & 0xff) << 8) + (((int) buf[3]) & 0xff) + 20;
                boolean isHeaderLengthValid = (buf.length == total_header_length);
                isStunPacket = areFirstTwoBitsValid && isHeaderLengthValid;
            }
        }
        return isStunPacket;
    }

    /**
     * Ensures that a STUN message is something we'd be interested in.
     */
    public static boolean isStunMethod(byte[] buf) {
        byte b0 = buf[0];
        byte b1 = buf[1];
        // we only accept the method Binding and the reserved methods 0x000 and 0x002/SharedSecret
        int method = (b0 & 0xFE) | (b1 & 0xEF);
        switch (method) {
            case Message.STUN_METHOD_BINDING:
            case Message.STUN_REQUEST:
            case Message.SHARED_SECRET_REQUEST:
                return true;
        }
        return false;
    }

    /**
     * Determines whether data in a byte array represents a TURN message.
     *
     * @param buf the bytes to check
     * @return true if the bytes look like TURN, otherwise false
     */
    public static boolean isTurn(byte[] buf) {
        // If this is a STUN packet
        boolean isStunPacket = false;
        // All STUN messages MUST start with a 20-byte header followed by zero or more Attributes
        if (buf.length >= 20) {
            // If the MAGIC COOKIE is present this is a STUN packet (RFC5389 compliant).
            if (buf[4] == Message.MAGIC_COOKIE[0] && buf[5] == Message.MAGIC_COOKIE[1] && buf[6] == Message.MAGIC_COOKIE[2]
                    && buf[7] == Message.MAGIC_COOKIE[3]) {
                isStunPacket = true;
            }
        }
        return isStunPacket;
    }

    /**
     * Ensures that a TURN message is something we'd be interested in.
     */
    public static boolean isTurnMethod(byte[] buf) {
        byte b0 = buf[0];
        byte b1 = buf[1];
        int method = (b0 & 0xFE) | (b1 & 0xEF);
        switch (method) {
            case Message.TURN_METHOD_ALLOCATE:
            case Message.TURN_METHOD_CHANNELBIND:
            case Message.TURN_METHOD_CREATEPERMISSION:
            case Message.TURN_METHOD_DATA:
            case Message.TURN_METHOD_REFRESH:
            case Message.TURN_METHOD_SEND:
            case 0x0005: /* old TURN DATA indication */
                return true;
        }
        return false;
    }

    /**
     * Determines whether data in a byte array represents a DTLS message.
     *
     * @param buf the bytes to check
     * @return true if the bytes look like DTLS, otherwise false
     */
    public static boolean isDtls(byte[] buf) {
        if (buf.length > 0) {
            int fb = buf[0] & 0xff;
            return 19 < fb && fb < 64;
        }
        return false;
    }

    /**
     * Returns the DTLS version as a string or null if parsing fails.
     *
     * @param buf the bytes to probe
     * @param offset data start position
     * @param length data length
     * @return DTLS version or null
     */
    public static String getDtlsVersion(byte[] buf, int offset, int length) {
        String version = null;
        // DTLS record header length is 13b
        if (length >= DTLS_RECORD_HEADER_LENGTH) {
            short type = (short) (buf[offset] & 0xff);
            switch (type) {
                case DtlsContentType.alert:
                case DtlsContentType.application_data:
                case DtlsContentType.change_cipher_spec:
                case DtlsContentType.handshake:
                    int major = buf[offset + 1] & 0xff;
                    int minor = buf[offset + 2] & 0xff;
                    //logger.trace("Version: {}.{}", major, minor);
                    // DTLS v1.0
                    if (major == 254 && minor == 255) {
                        version = "1.0";
                    }
                    // DTLS v1.2
                    if (version == null && major == 254 && minor == 253) {
                        version = "1.2";
                    }
                    break;
                default:
                    logger.trace("Unhandled content type: {}", type);
                    break;
            }
        }
        //logger.debug("DtlsRecord: {} length: {}", version, length);
        return version;
    }

    /**
     * Tries to parse the bytes in buf at offset off (and length len) as a STUN Binding Request message. If successful,
     * looks for a USERNAME attribute and returns the local username fragment part (see RFC5245 Section 7.1.2.3).
     * In case of any failure returns null.
     *
     * @param buf the bytes.
     * @param off the offset.
     * @param len the length.
     * @return the local ufrag from the USERNAME attribute of the STUN message contained in buf, or null.
     */
    public static String getUfrag(byte[] buf, int off, int len) {
        // RFC5389, Section 6: All STUN messages MUST start with a 20-byte header followed by zero or more Attributes.
        if (buf == null || buf.length < off + len || len < 20) {
            return null;
        }
        // RFC5389, Section 6: The magic cookie field MUST contain the fixed value 0x2112A442 in network byte order.
        if (((buf[off + 4] & 0xFF) == 0x21 && (buf[off + 5] & 0xFF) == 0x12 && (buf[off + 6] & 0xFF) == 0xA4
                && (buf[off + 7] & 0xFF) == 0x42)) {
            try {
                Message stunMessage = Message.decode(buf, off, len);
                if (stunMessage.getMessageType() == Message.BINDING_REQUEST) {
                    UsernameAttribute usernameAttribute = (UsernameAttribute) stunMessage.getAttribute(Attribute.Type.USERNAME);
                    if (isTrace) {
                        logger.trace("UsernameAttribute: {}", usernameAttribute);
                    }
                    if (usernameAttribute != null) {
                        String usernameString = new String(usernameAttribute.getUsername());
                        return usernameString.split(":")[0];
                    }
                }
            } catch (Exception e) {
                // Catch everything. We are going to log, and then drop the packet anyway.
                if (isDebug) {
                    logger.warn("Failed to extract local ufrag", e);
                }
            }
        } else {
            if (isDebug) {
                logger.debug("Not a STUN packet, magic cookie not found.");
            }
        }
        return null;
    }

    static String getMessageType(short msg_type) {
        switch (msg_type) {
            case HandshakeType.hello_request: // 0;
                return "Hello request";
            case HandshakeType.client_hello: // 1;
                return "Client hello";
            case HandshakeType.server_hello: // 2;
                return "Server hello";
            case HandshakeType.hello_verify_request: // 3;
                return "Hello verify request";
            case HandshakeType.session_ticket: // 4;
                return "Session ticket";
            case HandshakeType.certificate: // 11;
                return "Certificate";
            case HandshakeType.server_key_exchange: // 12;
                return "Server key exchange";
            case HandshakeType.certificate_request: // 13;
                return "Certificate request";
            case HandshakeType.server_hello_done: // 14;
                return "Server hello done";
            case HandshakeType.certificate_verify: // 15;
                return "Certificate verify";
            case HandshakeType.client_key_exchange: // 16;
                return "Client key exchange";
            case HandshakeType.finished: // 20;
                return "Finished";
            case HandshakeType.certificate_url: // 21;
                return "Certificate url";
            case HandshakeType.certificate_status: // 22;
                return "Certificate status";
            case HandshakeType.supplemental_data: // 23;
                return "Supplemental data";
        }
        return null;
    }

    /**
     * RFC 2246 6.2.1
     */
    class DtlsContentType {

        public static final short change_cipher_spec = 20; // 14

        public static final short alert = 21; // 15

        public static final short handshake = 22; // 16

        public static final short application_data = 23; // 17

        public static final short heartbeat = 24; // 18
    }

    class HandshakeType {
        public static final short hello_request = 0; // 0;

        public static final short client_hello = 1; // 1;

        public static final short server_hello = 2; // 2;

        public static final short hello_verify_request = 3; // 3;

        public static final short session_ticket = 4; // 4;

        public static final short certificate = 17; // 11;

        public static final short server_key_exchange = 18; // 12;

        public static final short certificate_request = 19; // 13;

        public static final short server_hello_done = 20; // 14;

        public static final short certificate_verify = 21; // 15;

        public static final short client_key_exchange = 22; // 16;

        public static final short finished = 32; // 20;

        public static final short certificate_url = 33; // 21;

        public static final short certificate_status = 34; // 22;

        public static final short supplemental_data = 35; // 23;
    }
}
