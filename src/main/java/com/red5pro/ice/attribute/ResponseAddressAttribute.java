/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.TransportAddress;

/**
 * The RESPONSE-ADDRESS attribute indicates where the response to a
 * Binding Request should be sent.  Its syntax is identical to MAPPED-ADDRESS.
 *
 * @author Emil Ivov
 */
public class ResponseAddressAttribute extends AddressAttribute {

    /**
     * Creates a RESPONSE_ADDRESS attribute
     */
    public ResponseAddressAttribute() {
        super(Attribute.Type.RESPONSE_ADDRESS);
    }

    public ResponseAddressAttribute(TransportAddress address) {
        super(Attribute.Type.RESPONSE_ADDRESS, address);
    }
}
