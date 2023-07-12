/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.TransportAddress;

/**
 * The DESTINATION-ADDRESS is present in Send Requests of old TURN versions.
 * It specifies the address and port where the data is to be sent. It is encoded
 * in the same way as MAPPED-ADDRESS.
 *
 * @author Sebastien Vincent
 */
public class DestinationAddressAttribute extends AddressAttribute {

    /**
     * Constructor.
     */
    DestinationAddressAttribute() {
        super(Attribute.Type.DESTINATION_ADDRESS);
    }

    public DestinationAddressAttribute(TransportAddress address) {
        super(Attribute.Type.DESTINATION_ADDRESS, address);
    }
}
