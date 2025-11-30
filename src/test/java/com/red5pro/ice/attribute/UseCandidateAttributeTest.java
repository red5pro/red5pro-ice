/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.TestCase;

import com.red5pro.ice.StunException;

/**
 * Tests the USE-CANDIDATE attribute as defined in RFC 8445 Section 7.1.2.
 *
 * The USE-CANDIDATE attribute indicates that the candidate pair resulting from
 * this check will be used for data if the check succeeds. The attribute has no
 * content (the Length field of the attribute is zero); it serves as a flag.
 * It has an attribute value of 0x0025.
 *
 * @author Red5 Pro
 */
public class UseCandidateAttributeTest extends TestCase {

    private UseCandidateAttribute useCandidateAttribute;

    protected void setUp() throws Exception {
        super.setUp();
        useCandidateAttribute = new UseCandidateAttribute();
    }

    protected void tearDown() throws Exception {
        useCandidateAttribute = null;
        super.tearDown();
    }

    /**
     * Test that the attribute type is correct per RFC 8445.
     */
    public void testAttributeType() {
        assertEquals("USE-CANDIDATE attribute should have type 0x0025", Attribute.Type.USE_CANDIDATE,
                useCandidateAttribute.getAttributeType());
        assertEquals("USE-CANDIDATE type value should be 0x0025", 0x0025, Attribute.Type.USE_CANDIDATE.type);
    }

    /**
     * Test that data length is 0 (flag attribute with no content).
     */
    public void testDataLength() {
        assertEquals("USE-CANDIDATE should have zero data length", 0, useCandidateAttribute.getDataLength());
    }

    /**
     * Test encoding produces only the attribute header (4 bytes).
     */
    public void testEncode() {
        byte[] encoded = useCandidateAttribute.encode();

        assertEquals("Encoded length should be 4 bytes (header only)", 4, encoded.length);

        // Verify type bytes (0x0025)
        assertEquals("Type high byte", 0x00, encoded[0] & 0xFF);
        assertEquals("Type low byte", 0x25, encoded[1] & 0xFF);

        // Verify length bytes (0)
        assertEquals("Length high byte", 0x00, encoded[2] & 0xFF);
        assertEquals("Length low byte", 0x00, encoded[3] & 0xFF);
    }

    /**
     * Test decoding of an empty attribute body.
     */
    public void testDecode() throws StunException {
        byte[] emptyBody = new byte[0];
        useCandidateAttribute.decodeAttributeBody(emptyBody, 0, 0);
        // Should not throw, attribute body is empty
    }

    /**
     * Test equals method.
     */
    public void testEquals() {
        UseCandidateAttribute other = new UseCandidateAttribute();

        // Test equality with another USE-CANDIDATE attribute
        assertTrue("Two USE-CANDIDATE attributes should be equal", useCandidateAttribute.equals(other));

        // Test equality with self
        assertTrue("Attribute should equal itself", useCandidateAttribute.equals(useCandidateAttribute));

        // Test inequality with null
        assertFalse("Attribute should not equal null", useCandidateAttribute.equals(null));

        // Test inequality with different type
        assertFalse("Attribute should not equal different type", useCandidateAttribute.equals("not an attribute"));

        // Test inequality with different attribute type
        PriorityAttribute priorityAttr = new PriorityAttribute();
        assertFalse("USE-CANDIDATE should not equal PRIORITY attribute", useCandidateAttribute.equals(priorityAttr));
    }

    /**
     * Test that encoding and decoding preserves the attribute.
     */
    public void testEncodeDecode() throws StunException {
        byte[] encoded = useCandidateAttribute.encode();

        UseCandidateAttribute decoded = new UseCandidateAttribute();
        // The body is empty, so we decode with offset after header
        if (encoded.length > Attribute.HEADER_LENGTH) {
            decoded.decodeAttributeBody(encoded, Attribute.HEADER_LENGTH, 0);
        }

        assertEquals("Encoded and decoded attributes should be equal", useCandidateAttribute, decoded);
    }

    /**
     * Test the attribute name.
     */
    public void testGetName() {
        assertEquals("Attribute name should be USE_CANDIDATE", "USE_CANDIDATE", useCandidateAttribute.getName());
    }
}
