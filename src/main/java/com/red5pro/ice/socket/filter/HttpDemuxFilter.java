/* See LICENSE.md for license information */
package com.red5pro.ice.socket.filter;

import java.net.DatagramPacket;
import java.nio.charset.Charset;

import com.red5pro.ice.harvest.TurnCandidateHarvester;

/**
 * Implements a {@link DataFilter} which allows demultiplexing HTTP(S).
 * Accepts HTTP, SSL v2, and TLS. Rejects Google TURN SSLTCP.
 *
 * @author Lyubomir Marinov
 */
public class HttpDemuxFilter implements DataFilter {
    /**
     * The US-ASCII {@code byte}s of {@link #REQUEST_METHOD_STRINGS}. Explicitly
     * defined for the purposes of performance.
     */
    private static byte[][] REQUEST_METHOD_BYTES;

    /**
     * The maximum US-ASCII character with which an element of
     * {@link #REQUEST_METHOD_STRINGS} starts.
     */
    private static final char REQUEST_METHOD_MAX_CHAR;

    /**
     * The maximum length in {@code byte}s of a US-ASCII representation of an
     * HTTP request method supported by the class {@code HttpDemuxFilter}.
     */
    public static final int REQUEST_METHOD_MAX_LENGTH;

    /**
     * The minimum US-ASCII character with which an element of
     * {@link #REQUEST_METHOD_STRINGS} starts.
     */
    private static final char REQUEST_METHOD_MIN_CHAR;

    /**
     * The HTTP request methods recognized by the class {@code HttpDemuxFilter}.
     */
    private static String[] REQUEST_METHOD_STRINGS = { "CONNECT", "DELETE", "GET", "HEAD", "MOVE", "OPTIONS", "PATCH", "POST", "PRI",
            "PROXY", "PUT", "TRACE" };

    /**
     * The minimum number of bytes required by the class {@code HttpDemuxFilter}
     * in order to declare that (the data of) a specific {@link DatagramPacket}
     * represents TLS.
     */
    public static final int TLS_MIN_LENGTH = 11;

    static {
        // Gather statistics about the supported HTTP request methods in order
        // to speed up the analysis of DatagramPackets later on.
        char maxChar = 'A';
        int maxLength = Integer.MIN_VALUE;
        char minChar = 'Z';
        Charset ascii = Charset.forName("US-ASCII");

        REQUEST_METHOD_BYTES = new byte[REQUEST_METHOD_STRINGS.length][];
        for (int i = 0; i < REQUEST_METHOD_STRINGS.length; i++) {
            String s = REQUEST_METHOD_STRINGS[i];

            if (s != null && s.length() != 0) {
                char ch = s.charAt(0);
                byte[] bytes = s.getBytes(ascii);
                int length = bytes.length;

                if (maxChar < ch)
                    maxChar = ch;
                if (maxLength < length)
                    maxLength = length;
                if (minChar > ch)
                    minChar = ch;

                REQUEST_METHOD_BYTES[i] = bytes;
            } else {
                REQUEST_METHOD_BYTES[i] = new byte[0];
            }
        }
        REQUEST_METHOD_MAX_CHAR = maxChar;
        REQUEST_METHOD_MAX_LENGTH = maxLength;
        REQUEST_METHOD_MIN_CHAR = minChar;
    }

    /**
     * Determines whether a specific {@link DatagramPacket} looks like the beginning of HTTP(S) client communication.
     * Accepts HTTP, SSL v2, and TLS. Rejects Google TURN SSLTCP.
     *
     * @param buf the bytes to check
     * @return true if the bytes look like the beginning of HTTP(S) client communication, otherwise false
     */
    @Override
    public boolean accept(byte[] buf) {
        boolean accept = false;
        if (buf.length > 0) {
            int off = 0;
            // The first bytes of HTTP, SSL v2, and TCP are different so quickly
            // determine which one of the three is possible and, respectively,
            // which two of the three are impossible.
            int b0 = 0xFF & buf[off];
            boolean http, sslv2, tls;
            if (b0 == 22 /* TLS handshake */) {
                http = false;
                sslv2 = false;
                tls = true;
            } else if (b0 > 0x80 /* SSL v2 client hello */) {
                http = false;
                sslv2 = true;
                tls = false;
            } else {
                http = true;
                sslv2 = false;
                tls = false;
            }

            // HTTP
            if (http) {
                // Request-Line = Method SP Request-URI SP HTTP-Version CRLF
                // HTTP-Version = "HTTP" "/" 1*DIGIT "." 1*DIGIT
                if (b0 >= REQUEST_METHOD_MIN_CHAR && b0 <= REQUEST_METHOD_MAX_CHAR
                        && buf.length >= REQUEST_METHOD_MAX_LENGTH + 1 /* SP */) {
                    // Match a supported HTTP request method.
                    for (byte[] bytes : REQUEST_METHOD_BYTES) {
                        int length = bytes.length;

                        if (buf[off + length] == ' ' /* SP */) {
                            boolean equals = true;

                            for (int i = 1, j = off + 1; i < length; i++, j++) {
                                if (bytes[i] != buf[j]) {
                                    equals = false;
                                    break;
                                }
                            }
                            if (equals) {
                                accept = true;
                                break;
                            }
                        }
                    }
                }
                // Only one of HTTP, SSL v2, and TCP was deemed possible by
                // looking at the first byte. If p didn't look like HTTP here,
                // then it will not look like SSL v2 or TLS.
                return accept;
            }

            // HTTPS
            if (tls) {
                // 1 byte   ContentType type = handshake(22)
                // 2 bytes  ProtocolVersion version = { major(3) , minor<1..3> }
                // 2 bytes  uint16 length
                // 1 byte   HandshakeType msg_type = client_hello(1)
                // 3 bytes  uint24 length
                // 2 bytes  ProtocolVersion client_version
                if (buf.length >= TLS_MIN_LENGTH && /* major */(0xFF & buf[off + 1]) == 3) {
                    int minor = 0xFF & buf[off + 2];

                    if (1 <= minor && minor <= 3 && /* msg_type */(0xFF & buf[off + 5]) == /* client_hello */1
                            && /* major */(0xFF & buf[off + 9]) == 3) {
                        minor = 0xFF & buf[off + 10];
                        if (1 <= minor && minor <= 3)
                            accept = true;
                    }
                }
                // Only one of HTTP, SSL v2, and TCP was deemed possible by
                // looking at the first byte. If p didn't look like TLS here,
                // then it will not look like HTTP or SSL v2.
                return accept;
            }
            if (sslv2) {
                final byte[] googleTurnSslTcp = TurnCandidateHarvester.SSL_CLIENT_HANDSHAKE;

                // 2 bytes  uint15 length
                // 1 byte   uint8 msg_type = 1
                // 2 bytes  Version version
                if (buf.length > 5 && buf.length >= googleTurnSslTcp.length && /* msg_type */(0xFF & buf[off + 2]) == 1
                        && /* major */(0xFF & buf[off + 3]) == 3) {
                    int minor = 0xFF & buf[off + 4];

                    if (1 <= minor && minor <= 3) {
                        // Reject Google TURN SSLTCP.
                        boolean equals = true;

                        for (int i = 0, iEnd = googleTurnSslTcp.length, j = off; i < iEnd; i++, j++) {
                            if (googleTurnSslTcp[i] != buf[j]) {
                                equals = false;
                                break;
                            }
                        }
                        accept = !equals;
                    }
                }
                // Only one of HTTP, SSL v2, and TCP was deemed possible by
                // looking at the first byte. If p didn't look like SSL v2 here,
                // then it will not look like HTTP or TLS.
                return accept;
            }
        }
        return accept;
    }
}
