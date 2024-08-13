/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.StunException;

/**
 *
 * @author Emil Ivov
 */
public abstract class IceControlAttribute extends Attribute {
    /**
     * The length of the data contained in this attribute
     */
    static final int DATA_LENGTH_ICE_CONTROL = 8;

    /**
     * The tie-breaker value stored in this attribute
     */
    long tieBreaker;

    /**
     * Indicates whether this is an ICE-CONTROLLING or an
     * ICE-CONTROLLED attribute.
     */
    boolean isControlling;

    /**
     * Constructs an ICE-CONTROLLING or an ICE-CONTROLLED attribute depending
     * on the value of isControlling.
     *
     * @param isControlling indicates the kind of attribute we are trying to
     * create
     */
    IceControlAttribute(boolean isControlling) {
        super(isControlling ? Attribute.Type.ICE_CONTROLLING : Attribute.Type.ICE_CONTROLLED);
        this.isControlling = isControlling;
    }

    /**
     * Sets this attribute's fields according to attributeValue array.
     *
     * @param attributeValue a binary array containing this attribute's field
     *                       values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *                  offset is equal to the index of the first byte after
     *                  length)
     * @param length the length of the attribute data.
     *
     * @throws StunException if attrubteValue contains invalid data.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        // array used to hold the intermediate long values reconstructed from the attributeValue array

        // Reading in the network byte order (Big-Endian)
        tieBreaker = ((attributeValue[offset++] & 0xffL) << 56) | ((attributeValue[offset++] & 0xffL) << 48)
                | ((attributeValue[offset++] & 0xffL) << 40) | ((attributeValue[offset++] & 0xffL) << 32)
                | ((attributeValue[offset++] & 0xffL) << 24) | ((attributeValue[offset++] & 0xffL) << 16)
                | ((attributeValue[offset++] & 0xffL) << 8) | (attributeValue[offset] & 0xffL);
    }

    /**
     * Returns a binary representation of this attribute.
     *
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        byte[] binValue = new byte[HEADER_LENGTH + getDataLength()];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte) (type >> 8);
        binValue[1] = (byte) (type & 0x00FF);

        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);

        //Tie-Breaker
        binValue[4] = (byte) ((tieBreaker & 0xFF00000000000000L) >> 56);
        binValue[5] = (byte) ((tieBreaker & 0x00FF000000000000L) >> 48);
        binValue[6] = (byte) ((tieBreaker & 0x0000FF0000000000L) >> 40);
        binValue[7] = (byte) ((tieBreaker & 0x000000FF00000000L) >> 32);
        binValue[8] = (byte) ((tieBreaker & 0x00000000FF000000L) >> 24);
        binValue[9] = (byte) ((tieBreaker & 0x0000000000FF0000L) >> 16);
        binValue[10] = (byte) ((tieBreaker & 0x000000000000FF00L) >> 8);
        binValue[11] = (byte) (tieBreaker & 0x00000000000000FFL);

        return binValue;
    }

    /**
     * Compares two STUN Attributes. Attributes are considered equal when their
     * type, length, and all data are the same.
     *
     * @param obj the object to compare this attribute with.
     *
     * @return true if the attributes are equal and false otherwise.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof IceControlAttribute))
            return false;

        if (obj == this)
            return true;

        IceControlAttribute iceControlAtt = (IceControlAttribute) obj;
        if (iceControlAtt.getAttributeType() != getAttributeType() || iceControlAtt.isControlling != isControlling
                || iceControlAtt.getDataLength() != DATA_LENGTH_ICE_CONTROL || getTieBreaker() != iceControlAtt.getTieBreaker()) {
            return false;
        }

        return true;
    }

    /**
     * Returns the data length of this attribute
     *
     * @return    the data length of this attribute
     */
    public int getDataLength() {
        return DATA_LENGTH_ICE_CONTROL;
    }

    /**
     * Sets the tie-breaker value.
     *
     * @param tieBreaker the the tie-breaker value
     */
    public void setTieBreaker(long tieBreaker) {
        this.tieBreaker = tieBreaker;
    }

    /**
     * Returns the value of the tie-breaker.
     *
     * @return the value of the tie-breaker.
     */
    public long getTieBreaker() {
        return tieBreaker;
    }

    /**
     * Returns the controlling property.
     *
     * @return true if controlling and false if controlled
     */
    public boolean isControlling() {
        return isControlling;
    }

    @Override
    public String toString() {
        return "IceControlAttribute [tieBreaker=" + tieBreaker + ", isControlling=" + isControlling + "]";
    }
}
