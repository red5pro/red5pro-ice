/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

/**
 * The REMOTE-ADDRESS is present in Data Indication of old TURN versions.
 * It specifies the address and port where the data is sent. It is encoded
 * in the same way as MAPPED-ADDRESS.
 *
 * @author Sebastien Vincent
 */
public class RemoteAddressAttribute extends AddressAttribute
{

    /**
     * Constructor.
     */
    RemoteAddressAttribute()
    {
        super(Attribute.Type.REMOTE_ADDRESS);
    }
}
