/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.StunException;

/**
 * The LIFETIME attribute is used to know the lifetime
 * of TURN allocations.
 *
 * @author Sebastien Vincent
 * @author Aakash Garg
 */
public class LifetimeAttribute extends Attribute {

    /**
     * The length of the data contained by this attribute.
     */
    public static final int DATA_LENGTH = 4;

    /**
     * Lifetime value.
     */
    int lifetime = 0;

    /**
     * Constructor.
     */
    LifetimeAttribute() {
        super(Attribute.Type.LIFETIME);
    }

    /**
     * Compares two STUN Attributes. Attributes are considered equal when their
     * type, length, and all data are the same.
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LifetimeAttribute))
            return false;

        if (obj == this)
            return true;

        LifetimeAttribute att = (LifetimeAttribute) obj;
        if (att.getAttributeType() != getAttributeType() || att.getDataLength() != getDataLength()
        /* compare data */
                || att.lifetime != lifetime)
            return false;

        return true;
    }

    /**
     * Returns the length of this attribute's body.
     * @return the length of this attribute's value (8 bytes).
     */
    @Override
    public int getDataLength() {
        return DATA_LENGTH;
    }

    /**
     * Returns a binary representation of this attribute.
     * @return a binary representation of this attribute.
     */
    @Override
    public byte[] encode() {
        byte binValue[] = new byte[HEADER_LENGTH + DATA_LENGTH];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte) (type >> 8);
        binValue[1] = (byte) (type & 0x00FF);
        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);
        //Data
        binValue[4] = (byte) ((lifetime >> 24) & 0xff);
        binValue[5] = (byte) ((lifetime >> 16) & 0xff);
        binValue[6] = (byte) ((lifetime >> 8) & 0xff);
        binValue[7] = (byte) ((lifetime) & 0xff);

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
     * @throws StunException if attrubteValue contains invalid data.
     */
    @Override
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        if (length != 4) {
            throw new StunException("length invalid");
        }

        lifetime = ((attributeValue[offset] << 24) & 0xff000000) + ((attributeValue[offset + 1] << 16) & 0x00ff0000)
                + ((attributeValue[offset + 2] << 8) & 0x0000ff00) + (attributeValue[offset + 3] & 0x000000ff);
    }

    /**
     * Set the lifetime.
     * @param lifetime lifetime
     */
    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * Get the lifetime.
     * @return lifetime
     */
    public int getLifetime() {
        return lifetime;
    }
}
