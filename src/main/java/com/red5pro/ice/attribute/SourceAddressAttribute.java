/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.TransportAddress;

/**
 * The SOURCE-ADDRESS attribute is present in Binding Responses.  It
 * indicates the source IP address and port that the server is sending
 * the response from.  Its syntax is identical to that of MAPPED-ADDRESS.
 *
 * @author Emil Ivov
 */
public class SourceAddressAttribute extends AddressAttribute {

    /**
     * Creates a SOURCE-ADDRESS attribute
     */
    SourceAddressAttribute() {
        super(Attribute.Type.SOURCE_ADDRESS);
    }

    public SourceAddressAttribute(TransportAddress address) {
        super(Attribute.Type.SOURCE_ADDRESS, address);
    }
}
