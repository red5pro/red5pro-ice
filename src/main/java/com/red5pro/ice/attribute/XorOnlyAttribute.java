/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.StunException;

/**
 *  This attribute is present in a Binding Request.  It is used by a
 *  client to request that a server compliant to this specification omit
 *  the MAPPED-ADDRESS from a Binding Response, and include only the XOR-
 *  MAPPED-ADDRESS.  This is necessary in cases where a Binding Response
 *  is failing integrity checks because a NAT is rewriting the contents
 *  of a MAPPED-ADDRESS in the Binding Response.
 *
 * This attribute has a length of zero, and therefore contains no other
 * information past the common attribute header.
 *
 * @author Emil Ivov
 */
public class XorOnlyAttribute extends Attribute {
    /**
     * Constructor.
     */
    protected XorOnlyAttribute() {
        super(Attribute.Type.XOR_ONLY);
    }

    /**
     * Sets this attribute's fields according to attributeValue array.
     *
     * @param attributeValue a binary array containing this attribute's
     *   field values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *   offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     * @throws StunException if attrubteValue contains invalid data.
     */
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        //nothing to do cause we have 0 length
    }

    /**
     * Returns a binary representation of this attribute.
     *
     * @return a binary representation of this attribute.
     */
    public byte[] encode() {
        byte binValue[] = new byte[HEADER_LENGTH + getDataLength()];

        //Type
        int type = getAttributeType().getType();
        binValue[0] = (byte) (type >> 8);
        binValue[1] = (byte) (type & 0x00FF);

        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);

        return binValue;
    }

    /**
     * Returns the length of this attribute's body. (Which in the case of the
     * XOR-ONLY attribute is 0);
     *
     * @return the length of this attribute's value.
     */
    public int getDataLength() {
        return 0;
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when
     * they have the same type length and value.
     *
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */

    public boolean equals(Object obj) {
        if (!(obj instanceof XorOnlyAttribute))
            return false;

        if (obj == this)
            return true;

        XorOnlyAttribute att = (XorOnlyAttribute) obj;
        if (att.getAttributeType() != getAttributeType() || att.getDataLength() != getDataLength())
            return false;

        return true;
    }
}
