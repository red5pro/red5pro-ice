/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

/**
 * The ALTERNATE-SERVER attribute indicates the IP address and
 * port of an alternate server the client could use. For example,
 * alternate servers may contains special capabilities.
 *
 * It consists of an eight bit address family, and a sixteen bit
 * port, followed by a fixed length value representing the IP address.
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |x x x x x x x x|    Family     |           Port                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             Address                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The port is a network byte ordered representation of the mapped port.
 * The address family is always 0x01, corresponding to IPv4.  The first
 * 8 bits of the ALTERNATE-SERVER are ignored, for the purposes of
 * aligning parameters on natural boundaries.  The IPv4 address is 32
 * bits.
 *
 * @author Sebastien Vincent
 */
public class AlternateServerAttribute extends AddressAttribute {

    /**
     * Constructor.
     */
    AlternateServerAttribute() {
        super(Attribute.Type.ALTERNATE_SERVER);
    }
}

