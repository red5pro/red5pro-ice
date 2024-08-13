/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Arrays;

import com.red5pro.ice.StunException;

/**
 * The DATA attribute contains the data the client wants to relay to the TURN
 * server or the TURN server to forward the response data.
 *
 * The value of DATA is variable length.  Its length MUST be a
 * multiple of 4 (measured in bytes) in order to guarantee alignment of
 * attributes on word boundaries.
 *
 * @author Sebastien Vincent
 */
public class DataAttribute extends Attribute {

    /**
     * Data value.
     */
    private byte data[];

    /**
     * Add padding.
     *
     * Some dialect does not add (and support) padding (GTalk).
     */
    private final boolean padding;

    /**
     * Creates a new instance of this class with padding enabled.
     */
    protected DataAttribute() {
        this(true);
    }

    /**
     * Creates a new instance of this class.
     * @param padding true to pad the data if the length is not on a word
     * boundary.
     */
    protected DataAttribute(boolean padding) {
        super(Attribute.Type.DATA);

        this.padding = padding;
    }

    /**
     * Copies the value of the data attribute from the specified
     * attributeValue.
     * @param attributeValue a binary array containing this attribute's
     *   field values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *   offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     * @throws StunException if attributeValue contains invalid data.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        data = new byte[length];
        System.arraycopy(attributeValue, offset, data, 0, length);
    }

    /**
     * Returns a binary representation of this attribute.
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        int dataLength = getDataLength();
        byte binary[] = new byte[HEADER_LENGTH + dataLength + (padding ? ((4 - dataLength % 4) % 4) : 0)];

        //Type
        int type = getAttributeType().getType();
        binary[0] = (byte) (type >> 8);
        binary[1] = (byte) (type & 0x00FF);

        //Length
        binary[2] = (byte) (dataLength >> 8);
        binary[3] = (byte) (dataLength & 0x00FF);

        //data
        System.arraycopy(data, 0, binary, 4, dataLength);

        return binary;
    }

    /**
     * Returns the length of this attribute's body.
     * @return the length of this attribute's value.
     */
    public int getDataLength() {
        return data.length;
    }

    /**
     * Returns a (cloned) byte array containing the data value of the data
     * attribute.
     * @return the binary array containing the data.
     */
    public byte[] getData() {
        return (data == null) ? null : data.clone();
    }

    /**
     * Copies the specified binary array into the the data value of the data
     * attribute.
     *
     * @param data the binary array containing the data.
     */
    public void setData(byte[] data) {
        if (data == null) {
            this.data = null;
            return;
        }

        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when
     * they have the same type length and value.
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */

    public boolean equals(Object obj) {
        if (!(obj instanceof DataAttribute))
            return false;

        if (obj == this)
            return true;

        DataAttribute att = (DataAttribute) obj;
        if (att.getAttributeType() != getAttributeType() || att.getDataLength() != getDataLength() || !Arrays.equals(att.data, data))
            return false;

        return true;
    }
}
