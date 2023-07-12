/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

/**
 * The XOR-RELAYED-ADDRESS attribute is given by a TURN server to indicates the client its relayed address.
 *
 * It has the same format as XOR-MAPPED-ADDRESS.
 *
 * @author Sebastien Vincent
 */
public class XorRelayedAddressAttribute extends XorMappedAddressAttribute {

    /**
     * Constructor.
     */
    XorRelayedAddressAttribute() {
        super(Attribute.Type.XOR_RELAYED_ADDRESS);
    }

}
