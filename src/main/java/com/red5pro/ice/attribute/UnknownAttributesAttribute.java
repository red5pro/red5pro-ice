/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.red5pro.ice.StunException;

/**
 * The UNKNOWN-ATTRIBUTES attribute is present only in a Binding Error
 * Response or Shared Secret Error Response when the response code in
 * the ERROR-CODE attribute is 420.
 *
 * The attribute contains a list of 16 bit values, each of which
 * represents an attribute type that was not understood by the server.
 * If the number of unknown attributes is an odd number, one of the
 * attributes MUST be repeated in the list, so that the total length of
 * the list is a multiple of 4 bytes.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Attribute 1 Type           |     Attribute 2 Type        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Attribute 3 Type           |     Attribute 4 Type    ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * @author Emil Ivov
 */
public class UnknownAttributesAttribute extends Attribute {

    /**
     * A list of attribute types that were not understood by the server.
     */
    private List<Integer> unknownAttributes = new LinkedList<>();

    /**
     * Constructor.
     */
    UnknownAttributesAttribute() {
        super(Attribute.Type.UNKNOWN_ATTRIBUTES);
    }

    /**
    * Returns the length (in bytes) of this attribute's body.
    * If the number of unknown attributes is an odd number, one of the
    * attributes MUST be repeated in the list, so that the total length of
    * the list is a multiple of 4 bytes.
    * @return the length of this attribute's value (a multiple of 4).
    */
    public int getDataLength() {
        int len = unknownAttributes.size();

        if ((len % 2) != 0)
            len++;

        return (len * 2);
    }

    /**
     * Adds the specified attribute type to the list of unknown attributes.
     * @param attributeID the id of an attribute to be listed as unknown in this
     * attribute
     */
    public void addAttributeID(int attributeID) {
        //some attributes may be repeated for padding
        //(packet length should be divisible by 4)
        if (!contains(attributeID))
            unknownAttributes.add(attributeID);
    }

    /**
     * Verifies whether the specified attributeID is contained by this attribute.
     * @param attributeID the attribute id to look for.
     * @return true if this attribute contains the specified attribute id.
     */
    public boolean contains(int attributeID) {
        return unknownAttributes.contains(attributeID);
    }

    /**
     * Returns an iterator over the list of attribute IDs contained by this
     * attribute.
     * @return an iterator over the list of attribute IDs contained by this
     * attribute.
     */
    public Iterator<Integer> getAttributes() {
        return unknownAttributes.iterator();
    }

    /**
     * Returns the number of attribute IDs contained by this class.
     * @return the number of attribute IDs contained by this class.
     */
    public int getAttributeCount() {
        return unknownAttributes.size();
    }

    /**
     * Returns the attribute id with index i.
     * @param index the index of the attribute id to return.
     * @return the attribute id with index i.
     */
    public int getAttribute(int index) {
        return unknownAttributes.get(index);
    }

    /**
     * Returns a binary representation of this attribute.
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        byte binValue[] = new byte[getDataLength() + HEADER_LENGTH];
        int offset = 0;

        //Type
        int type = getAttributeType().getType();
        binValue[offset++] = (byte) (type >> 8);
        binValue[offset++] = (byte) (type & 0x00FF);

        //Length
        binValue[offset++] = (byte) (getDataLength() >> 8);
        binValue[offset++] = (byte) (getDataLength() & 0x00FF);

        Iterator<Integer> attributes = getAttributes();
        while (attributes.hasNext()) {
            int att = attributes.next();
            binValue[offset++] = (byte) (att >> 8);
            binValue[offset++] = (byte) (att & 0x00FF);
        }

        // If the number of unknown attributes is an odd number, one of the
        // attributes MUST be repeated in the list, so that the total length of
        // the list is a multiple of 4 bytes.
        if (offset < binValue.length) {
            int att = getAttribute(0);
            binValue[offset++] = (byte) (att >> 8);
            binValue[offset++] = (byte) (att & 0x00FF);
        }

        return binValue;
    }

    /**
     * Compares two STUN Attributes. Attributes are considered equal when their
     * type, length, and all data are the same.
     *
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof UnknownAttributesAttribute))
            return false;

        if (obj == this)
            return true;

        UnknownAttributesAttribute att = (UnknownAttributesAttribute) obj;
        if (att.getAttributeType() != getAttributeType() || att.getDataLength() != getDataLength()
                || !unknownAttributes.equals(att.unknownAttributes))
            return false;

        return true;
    }

    /**
     * Sets this attribute's fields according to attributeValue array.
     *
     * @param attributeValue a binary array containing this attribute's field
     *                       values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *                  offset is equal to the index of the first byte after
     *                  length)
     * @param length the length of the binary array.
     * @throws StunException if attrubteValue contains invalid data.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        if ((length % 2) != 0)
            throw new StunException("Attribute IDs are 2 bytes long and the " + "passed binary array has an odd length " + "value.");
        int originalOffset = offset;
        for (int i = offset; i < originalOffset + length; i += 2) {
            int attributeID = (((attributeValue[offset++] & 0xFF) << 8) | (attributeValue[offset++] & 0xFF));
            addAttributeID(attributeID);
        }
    }
}
