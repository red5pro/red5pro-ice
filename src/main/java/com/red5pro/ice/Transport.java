/* See LICENSE.md for license information */
package com.red5pro.ice;

/**
 * The Transport enumeration contains all currently known transports
 * that ICE may be interacting with (but not necessarily support).
 *
 * @author Emil Ivov
 */
public enum Transport {

    /**
     * Represents a TCP transport.
     */
    TCP("tcp", (byte) 6, "Tcp"),

    /**
     * Represents a UDP transport.
     */
    UDP("udp", (byte) 17, "Udp"),

    /**
     * Represents a TLS transport.
     */
    TLS("tls", (byte) 0, "Tls"),

    /**
     * Represents a datagram TLS (DTLS) transport.
     */
    DTLS("dtls", (byte) 0, "Dtls"),

    /**
     * Represents an SCTP transport.
     */
    SCTP("sctp", (byte) 0, "Sctp"),

    /**
     * Represents an Google's SSL TCP transport.
     */
    SSLTCP("ssltcp", (byte) 0, "SslTcp");

    /**
     * The name of this Transport.
     */
    private final String transportName;

    private final String classNameTag;

    /**
     * The protocol number; used in messages to differentiate between transports.
     */
    private final byte protocolNumber;

    /**
     * Creates a Transport instance with the specified name.
     *
     * @param transportName the name of the Transport instance we'd like to create.
     */
    private Transport(String transportName, byte protocolNumber, String classNameTag) {
        this.transportName = transportName;
        this.protocolNumber = protocolNumber;
        this.classNameTag = classNameTag;
    }

    public String getTransportName() {
        return transportName;
    }

    public byte getProtocolNumber() {
        return protocolNumber;
    }

    public String getClassTypeTag() {
        return classNameTag;
    }

    /**
     * Returns the name of this Transport (e.g. "udp" or "tcp").
     *
     * @return the name of this Transport (e.g. "udp" or "tcp").
     */
    @Override
    public String toString() {
        return transportName;
    }

    /**
     * Returns a Transport instance corresponding to the specified transportName. For example, for name "udp", this method
     * would return {@link #UDP}.
     *
     * @param transportName the name that we'd like to parse.
     * @return a Transport instance corresponding to the specified transportName.
     *
     * @throws IllegalArgumentException in case transportName is not a valid or currently supported transport.
     */
    public static Transport parse(String transportName) throws IllegalArgumentException {
        if (UDP.toString().equals(transportName)) {
            return UDP;
        }
        if (TCP.toString().equals(transportName)) {
            return TCP;
        }
        if (TLS.toString().equals(transportName)) {
            return TLS;
        }
        if (SCTP.toString().equals(transportName)) {
            return SCTP;
        }
        if (DTLS.toString().equals(transportName)) {
            return DTLS;
        }
        if (SSLTCP.toString().equals(transportName)) {
            return SSLTCP;
        }
        throw new IllegalArgumentException(transportName + " is not a currently supported Transport");
    }
}
