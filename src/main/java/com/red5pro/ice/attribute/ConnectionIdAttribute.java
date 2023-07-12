/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.StunException;

/**
 * The connection ID attribute defined in RFC 6062:
 * Traversal Using Relays around NAT (TURN) Extensions for TCP Allocations.
 *
 * @author Aakash Garg
 */
public class ConnectionIdAttribute
    extends Attribute
{

    /**
     * The length of the data contained in this attribute.
     */
    public static final int DATA_LENGTH = 4;

    /**
     * The connection Id value.
     */
    private int connectionIdValue;
     
    /**
     * Constructor.
     */
    protected ConnectionIdAttribute() 
    {
	    super(Attribute.Type.CONNECTION_ID);
    }

    /**
     * Returns the length of this attribute body.
     * @return the length of this attribute value (4 bytes).
     */
    @Override
    public int getDataLength() 
    {
	    return DATA_LENGTH;
    }

    /**
     * Compares two TURN Attributes. Attributes are considered equal when their
     * type, length, and all data are the same.
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    @Override
    public boolean equals(Object obj) 
    {
	    if (! (obj instanceof ConnectionIdAttribute))
            return false;

        if (obj == this)
            return true;

        ConnectionIdAttribute att = (ConnectionIdAttribute) obj;
        if (att.getAttributeType() != getAttributeType()
                || att.getDataLength() != getDataLength()
                /* compare data */
                || att.connectionIdValue != this.connectionIdValue
           )
            return false;

        return true;
    }

    /**
    * Returns a binary representation of this attribute.
    * @return a binary representation of this attribute.
    */
    @Override
    public byte[] encode() 
    {
	    byte binValue[] = new byte[HEADER_LENGTH + DATA_LENGTH];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte)(type >> 8);
        binValue[1] = (byte)(type & 0x00FF);
        //Length
        binValue[2] = (byte)(getDataLength() >> 8);
        binValue[3] = (byte)(getDataLength() & 0x00FF);
        //Data
        binValue[4] = (byte) (connectionIdValue >> 24);
        binValue[5] = (byte) ((connectionIdValue & 0x00ff0000) >> 16);
        binValue[6] = (byte) ((connectionIdValue & 0x0000ff00) >> 8);
        binValue[7] = (byte) (connectionIdValue & 0x000000ff);

        return binValue;
    }

   /**
    * Sets this attribute's fields according to attributeValue array.
    * @param attributeValue a binary array containing this attribute's field
    *                       values and NOT containing the attribute header.
    * @param offset the position where attribute values begin (most often
    *          offset is equal to the index of the first byte after
    *          length)
    * @param length the length of the binary array.
    * @throws StunException if attributeValue contains invalid data.
    */
    @Override
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) 
	    throws StunException
    {
	    if(length != DATA_LENGTH)
        {
            throw new StunException("length invalid: " + length);
        }

        connectionIdValue = attributeValue[offset] & 0xff;

        connectionIdValue = connectionIdValue << 8 |
            (attributeValue[offset+1] & 0xff);

        connectionIdValue = connectionIdValue << 8 |
            (attributeValue[offset+2] & 0xff);

        connectionIdValue = connectionIdValue << 8 |
            (attributeValue[offset+3] & 0xff);
    }

    /**
     * Gets the Connection-Id Value
     * @return Connection-Id Value
     */
    public int getConnectionIdValue() 
    {
        return connectionIdValue;
    }
    
    /**
     * Sets the Connection-Id Value
     * @param connectionIdValue the connection Id value.
     */
    public void setConnectionIdValue(int connectionIdValue) 
    {	
	    this.connectionIdValue = connectionIdValue;
    }

}
