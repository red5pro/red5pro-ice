/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Arrays;

import com.red5pro.ice.StunException;

/**
 * The NONCE attribute is used for authentication.
 *
 * @author Sebastien Vincent
 */
public class NonceAttribute extends Attribute {

    /**
     * Nonce value.
     */
    private byte nonce[] = null;

    /**
     * Constructor.
     */
    NonceAttribute() {
        super(Attribute.Type.NONCE);
    }

    /**
     * Copies the value of the nonce attribute from the specified
     * attributeValue.
     * @param attributeValue a binary array containing this attribute's
     *   field values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *   offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     * @throws StunException if attributeValue contains invalid data.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        nonce = new byte[length];
        System.arraycopy(attributeValue, offset, nonce, 0, length);
    }

    /**
     * Returns a binary representation of this attribute.
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        byte binValue[] = new byte[HEADER_LENGTH + getDataLength() + (getDataLength() % 4)];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte) (type >> 8);
        binValue[1] = (byte) (type & 0x00FF);

        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);

        /* nonce */
        System.arraycopy(nonce, 0, binValue, 4, (int) getDataLength());

        return binValue;
    }

    /**
     * Returns the length of this attribute's body.
     * @return the length of this attribute's value.
     */
    public int getDataLength() {
        return nonce.length;
    }

    /**
     * Returns a (cloned) byte array containing the data value of the nonce
     * attribute.
     * @return the binary array containing the nonce.
     */
    public byte[] getNonce() {
        return (nonce == null) ? null : nonce.clone();
    }

    /**
     * Copies the specified binary array into the the data value of the nonce
     * attribute.
     * @param nonce the binary array containing the nonce.
     */
    public void setNonce(byte[] nonce) {
        this.nonce = (nonce == null) ? null : nonce.clone();
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when they
     * have the same type length and value.
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof NonceAttribute))
            return false;

        NonceAttribute att = (NonceAttribute) obj;

        return (att.getAttributeType() == getAttributeType() && att.getDataLength() == getDataLength() && Arrays.equals(att.nonce, nonce));
    }
}
