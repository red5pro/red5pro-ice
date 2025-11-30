/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.TestCase;

import com.red5pro.ice.StunException;

/**
 * Tests the PRIORITY attribute as defined in RFC 8445 Section 16.1.
 *
 * The PRIORITY attribute indicates the priority that is to be associated with a
 * peer-reflexive candidate, should one be discovered by this check. It is a
 * 32-bit unsigned integer, and has an attribute value of 0x0024.
 *
 * @author Red5 Pro
 */
public class PriorityAttributeTest extends TestCase {

    private PriorityAttribute priorityAttribute;

    protected void setUp() throws Exception {
        super.setUp();
        priorityAttribute = new PriorityAttribute();
    }

    protected void tearDown() throws Exception {
        priorityAttribute = null;
        super.tearDown();
    }

    /**
     * Test that the attribute type is correct per RFC 8445.
     */
    public void testAttributeType() {
        assertEquals("PRIORITY attribute should have type 0x0024", Attribute.Type.PRIORITY, priorityAttribute.getAttributeType());
        assertEquals("PRIORITY type value should be 0x0024", 0x0024, Attribute.Type.PRIORITY.type);
    }

    /**
     * Test encoding and decoding of various priority values.
     * RFC 8445 Section 5.1.2 defines the priority formula.
     */
    public void testEncodeDecode() throws StunException {
        // Test with a typical priority value
        long testPriority = 2130706431L; // (2^24)*126 + (2^8)*65535 + (2^0)*255
        priorityAttribute.setPriority(testPriority);

        byte[] encoded = priorityAttribute.encode();
        assertEquals("Encoded length should be 8 bytes (4 header + 4 data)", 8, encoded.length);

        // Decode and verify
        PriorityAttribute decoded = new PriorityAttribute();
        decoded.decodeAttributeBody(encoded, Attribute.HEADER_LENGTH, 4);
        assertEquals("Decoded priority should match original", testPriority, decoded.getPriority());
    }

    /**
     * Test the data length is always 4 bytes as per RFC 8445.
     */
    public void testDataLength() {
        assertEquals("PRIORITY data length should be 4 bytes", 4, priorityAttribute.getDataLength());
    }

    /**
     * Test priority boundary values.
     * Priority must be between 1 and (2^31 - 1) = 2147483647
     */
    public void testPriorityBoundaries() throws StunException {
        // Test minimum valid priority
        priorityAttribute.setPriority(1L);
        assertEquals("Minimum priority should be 1", 1L, priorityAttribute.getPriority());

        // Test maximum valid priority (2^31 - 1)
        priorityAttribute.setPriority(0x7FFFFFFFL);
        assertEquals("Maximum priority should be 2^31 - 1", 0x7FFFFFFFL, priorityAttribute.getPriority());

        // Test invalid priority (0)
        try {
            priorityAttribute.setPriority(0L);
            fail("Priority of 0 should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test invalid priority (> 2^31 - 1)
        try {
            priorityAttribute.setPriority(0x80000000L);
            fail("Priority > 2^31 - 1 should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    /**
     * Test equals method.
     */
    public void testEquals() throws StunException {
        long priority = 1686052607L;
        priorityAttribute.setPriority(priority);

        // Test equality with same priority
        PriorityAttribute other = new PriorityAttribute();
        other.setPriority(priority);
        assertTrue("Attributes with same priority should be equal", priorityAttribute.equals(other));

        // Test inequality with different priority
        other.setPriority(priority - 1);
        assertFalse("Attributes with different priorities should not be equal", priorityAttribute.equals(other));

        // Test inequality with null
        assertFalse("Attribute should not equal null", priorityAttribute.equals(null));

        // Test inequality with different type
        assertFalse("Attribute should not equal different type", priorityAttribute.equals("not an attribute"));
    }

    /**
     * Test binary encoding format matches RFC specification.
     * Format: 2 bytes type + 2 bytes length + 4 bytes priority (big-endian)
     */
    public void testEncodingFormat() throws StunException {
        // Set a known priority value: 0x12345678
        long priority = 0x12345678L;
        priorityAttribute.setPriority(priority);

        byte[] encoded = priorityAttribute.encode();

        // Verify type bytes (0x0024)
        assertEquals("Type high byte", 0x00, encoded[0] & 0xFF);
        assertEquals("Type low byte", 0x24, encoded[1] & 0xFF);

        // Verify length bytes (4)
        assertEquals("Length high byte", 0x00, encoded[2] & 0xFF);
        assertEquals("Length low byte", 0x04, encoded[3] & 0xFF);

        // Verify priority bytes (big-endian: 0x12 0x34 0x56 0x78)
        assertEquals("Priority byte 0", 0x12, encoded[4] & 0xFF);
        assertEquals("Priority byte 1", 0x34, encoded[5] & 0xFF);
        assertEquals("Priority byte 2", 0x56, encoded[6] & 0xFF);
        assertEquals("Priority byte 3", 0x78, encoded[7] & 0xFF);
    }

    /**
     * Test decoding from raw bytes.
     */
    public void testDecodeFromBytes() throws StunException {
        // Create raw attribute bytes with priority 0x7FFFFFFF (max value)
        byte[] rawAttribute = new byte[] { 0x00, 0x24, // type
                0x00, 0x04, // length
                0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // priority
        };

        PriorityAttribute attr = new PriorityAttribute();
        attr.decodeAttributeBody(rawAttribute, Attribute.HEADER_LENGTH, 4);

        assertEquals("Decoded priority should be max value", 0x7FFFFFFFL, attr.getPriority());
    }
}
