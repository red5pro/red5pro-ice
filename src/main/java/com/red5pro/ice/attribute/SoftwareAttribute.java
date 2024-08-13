/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.*;

/**
 * The SOFTWARE attribute contains a textual description of the software
 * being used by the software or the client, including manufacturer and version number.
 * The attribute has no impact on operation of the protocol, and serves
 * only as a tool for diagnostic and debugging purposes.
 * The value of SOFTWARE is variable length.  Its length MUST be a
 * multiple of 4 (measured in bytes) in order to guarantee alignment of
 * attributes on word boundaries.
 *
 * @author Emil Ivov
 * @author Sebastien Vincent
 */
public class SoftwareAttribute extends Attribute {
    /**
     * The value that this SoftwareAttribute is transporting.
     */
    private byte[] software = null;

    /**
     * Creates a new SoftwareAttribute.
     */
    protected SoftwareAttribute() {
        super(Attribute.Type.SOFTWARE);
    }

    /**
     * Copies the value of the software attribute from the specified
     * attributeValue.
     *
     * @param attributeValue a binary array containing this attribute's
     * field values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     * offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) {
        software = new byte[length];
        System.arraycopy(attributeValue, offset, software, 0, length);
    }

    /**
     * Returns a binary representation of this attribute.
     *
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        byte binValue[] = new byte[HEADER_LENGTH + getDataLength()
        //add padding
                + (4 - getDataLength() % 4) % 4];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte) (type >> 8);
        binValue[1] = (byte) (type & 0x00FF);

        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);

        //software
        System.arraycopy(software, 0, binValue, 4, getDataLength());

        return binValue;
    }

    /**
     * Returns the length of this attribute's body.
     *
     * @return the length of this attribute's value.
     */
    public int getDataLength() {
        return software.length;
    }

    /**
     * Returns a (cloned) byte array containing the data value of the software
     * attribute.
     * @return the binary array containing the software.
     */
    public byte[] getSoftware() {
        if (software == null)
            return null;

        byte[] copy = new byte[software.length];
        System.arraycopy(software, 0, copy, 0, software.length);
        return copy;
    }

    /**
     * Copies the specified binary array into the the data value of the software
     * attribute.
     *
     * @param software the binary array containing the software.
     */
    public void setSoftware(byte[] software) {
        if (software == null) {
            this.software = null;
            return;
        }

        this.software = new byte[software.length];
        System.arraycopy(software, 0, this.software, 0, software.length);
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when
     * they have the same type length and value.
     *
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof SoftwareAttribute))
            return false;

        if (obj == this)
            return true;

        SoftwareAttribute att = (SoftwareAttribute) obj;
        if (att.getAttributeType() != getAttributeType() || att.getDataLength() != getDataLength()
                || !Arrays.equals(att.software, software))
            return false;

        return true;
    }
}
