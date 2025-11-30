/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.TestCase;

import com.red5pro.ice.StunException;

/**
 * Tests the ICE-CONTROLLED and ICE-CONTROLLING attributes as defined in
 * RFC 8445 Section 7.1.3.
 *
 * The ICE-CONTROLLING and ICE-CONTROLLED attributes are used to determine
 * which agent is controlling and which is controlled. Each contains a 64-bit
 * random tie-breaker value.
 *
 * ICE-CONTROLLING: attribute value 0x802A
 * ICE-CONTROLLED: attribute value 0x8029
 *
 * @author Red5 Pro
 */
public class IceControlAttributeTest extends TestCase {

    private IceControllingAttribute controllingAttribute;
    private IceControlledAttribute controlledAttribute;

    protected void setUp() throws Exception {
        super.setUp();
        controllingAttribute = new IceControllingAttribute();
        controlledAttribute = new IceControlledAttribute();
    }

    protected void tearDown() throws Exception {
        controllingAttribute = null;
        controlledAttribute = null;
        super.tearDown();
    }

    /**
     * Test that the attribute types are correct per RFC 8445.
     */
    public void testAttributeTypes() {
        // ICE-CONTROLLING type 0x802A
        assertEquals("ICE-CONTROLLING attribute type should be 0x802A", Attribute.Type.ICE_CONTROLLING,
                controllingAttribute.getAttributeType());
        assertEquals("ICE-CONTROLLING type value should be 0x802A", 0x802A, Attribute.Type.ICE_CONTROLLING.type);

        // ICE-CONTROLLED type 0x8029
        assertEquals("ICE-CONTROLLED attribute type should be 0x8029", Attribute.Type.ICE_CONTROLLED,
                controlledAttribute.getAttributeType());
        assertEquals("ICE-CONTROLLED type value should be 0x8029", 0x8029, Attribute.Type.ICE_CONTROLLED.type);
    }

    /**
     * Test that data length is 8 bytes (64-bit tie-breaker).
     */
    public void testDataLength() {
        assertEquals("ICE-CONTROLLING data length should be 8 bytes", 8, controllingAttribute.getDataLength());
        assertEquals("ICE-CONTROLLED data length should be 8 bytes", 8, controlledAttribute.getDataLength());
    }

    /**
     * Test the isControlling property.
     */
    public void testIsControlling() {
        assertTrue("ICE-CONTROLLING should return true for isControlling", controllingAttribute.isControlling());
        assertFalse("ICE-CONTROLLED should return false for isControlling", controlledAttribute.isControlling());
    }

    /**
     * Test encoding and decoding of tie-breaker values.
     */
    public void testEncodeDecode() throws StunException {
        // Test with a specific tie-breaker value
        long tieBreaker = 0x123456789ABCDEF0L;
        controllingAttribute.setTieBreaker(tieBreaker);

        byte[] encoded = controllingAttribute.encode();
        assertEquals("Encoded length should be 12 bytes (4 header + 8 data)", 12, encoded.length);

        // Decode and verify
        IceControllingAttribute decoded = new IceControllingAttribute();
        decoded.decodeAttributeBody(encoded, Attribute.HEADER_LENGTH, 8);
        assertEquals("Decoded tie-breaker should match original", tieBreaker, decoded.getTieBreaker());
    }

    /**
     * Test encoding format matches RFC specification.
     * Format: 2 bytes type + 2 bytes length + 8 bytes tie-breaker (big-endian)
     */
    public void testEncodingFormat() {
        // Set a known tie-breaker value
        long tieBreaker = 0x0102030405060708L;
        controllingAttribute.setTieBreaker(tieBreaker);

        byte[] encoded = controllingAttribute.encode();

        // Verify type bytes (0x802A)
        assertEquals("Type high byte", 0x80, encoded[0] & 0xFF);
        assertEquals("Type low byte", 0x2A, encoded[1] & 0xFF);

        // Verify length bytes (8)
        assertEquals("Length high byte", 0x00, encoded[2] & 0xFF);
        assertEquals("Length low byte", 0x08, encoded[3] & 0xFF);

        // Verify tie-breaker bytes (big-endian)
        assertEquals("Tie-breaker byte 0", 0x01, encoded[4] & 0xFF);
        assertEquals("Tie-breaker byte 1", 0x02, encoded[5] & 0xFF);
        assertEquals("Tie-breaker byte 2", 0x03, encoded[6] & 0xFF);
        assertEquals("Tie-breaker byte 3", 0x04, encoded[7] & 0xFF);
        assertEquals("Tie-breaker byte 4", 0x05, encoded[8] & 0xFF);
        assertEquals("Tie-breaker byte 5", 0x06, encoded[9] & 0xFF);
        assertEquals("Tie-breaker byte 6", 0x07, encoded[10] & 0xFF);
        assertEquals("Tie-breaker byte 7", 0x08, encoded[11] & 0xFF);
    }

    /**
     * Test ICE-CONTROLLED encoding format.
     */
    public void testControlledEncodingFormat() {
        long tieBreaker = 0xFEDCBA9876543210L;
        controlledAttribute.setTieBreaker(tieBreaker);

        byte[] encoded = controlledAttribute.encode();

        // Verify type bytes (0x8029)
        assertEquals("Type high byte", 0x80, encoded[0] & 0xFF);
        assertEquals("Type low byte", 0x29, encoded[1] & 0xFF);
    }

    /**
     * Test equals method for ICE-CONTROLLING.
     */
    public void testControllingEquals() {
        long tieBreaker = 0xABCDEF1234567890L;
        controllingAttribute.setTieBreaker(tieBreaker);

        // Test equality with same values
        IceControllingAttribute other = new IceControllingAttribute();
        other.setTieBreaker(tieBreaker);
        assertTrue("Attributes with same tie-breaker should be equal", controllingAttribute.equals(other));

        // Test inequality with different tie-breaker
        other.setTieBreaker(tieBreaker + 1);
        assertFalse("Attributes with different tie-breakers should not be equal", controllingAttribute.equals(other));

        // Test inequality with null
        assertFalse("Attribute should not equal null", controllingAttribute.equals(null));

        // Test inequality with ICE-CONTROLLED
        controlledAttribute.setTieBreaker(tieBreaker);
        assertFalse("ICE-CONTROLLING should not equal ICE-CONTROLLED", controllingAttribute.equals(controlledAttribute));
    }

    /**
     * Test equals method for ICE-CONTROLLED.
     */
    public void testControlledEquals() {
        long tieBreaker = 0x1234567890ABCDEFL;
        controlledAttribute.setTieBreaker(tieBreaker);

        // Test equality with same values
        IceControlledAttribute other = new IceControlledAttribute();
        other.setTieBreaker(tieBreaker);
        assertTrue("Attributes with same tie-breaker should be equal", controlledAttribute.equals(other));

        // Test inequality with different tie-breaker
        other.setTieBreaker(tieBreaker - 1);
        assertFalse("Attributes with different tie-breakers should not be equal", controlledAttribute.equals(other));
    }

    /**
     * Test decoding from raw bytes for ICE-CONTROLLING.
     */
    public void testDecodeControllingFromBytes() throws StunException {
        // Create raw attribute bytes with tie-breaker 0xFFFFFFFFFFFFFFFF
        byte[] rawAttribute = new byte[] { (byte) 0x80, 0x2A, // type ICE-CONTROLLING
                0x00, 0x08, // length
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // tie-breaker
        };

        IceControllingAttribute attr = new IceControllingAttribute();
        attr.decodeAttributeBody(rawAttribute, Attribute.HEADER_LENGTH, 8);

        assertEquals("Decoded tie-breaker should be max value", 0xFFFFFFFFFFFFFFFFL, attr.getTieBreaker());
    }

    /**
     * Test decoding from raw bytes for ICE-CONTROLLED.
     */
    public void testDecodeControlledFromBytes() throws StunException {
        // Create raw attribute bytes with tie-breaker 0x0000000000000001
        byte[] rawAttribute = new byte[] { (byte) 0x80, 0x29, // type ICE-CONTROLLED
                0x00, 0x08, // length
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 // tie-breaker
        };

        IceControlledAttribute attr = new IceControlledAttribute();
        attr.decodeAttributeBody(rawAttribute, Attribute.HEADER_LENGTH, 8);

        assertEquals("Decoded tie-breaker should be 1", 1L, attr.getTieBreaker());
    }

    /**
     * Test tie-breaker boundary values.
     */
    public void testTieBreakerBoundaries() throws StunException {
        // Test minimum tie-breaker (0)
        controllingAttribute.setTieBreaker(0L);
        assertEquals("Minimum tie-breaker should be 0", 0L, controllingAttribute.getTieBreaker());

        // Test maximum tie-breaker (unsigned 64-bit max)
        controllingAttribute.setTieBreaker(0xFFFFFFFFFFFFFFFFL);
        assertEquals("Maximum tie-breaker should be unsigned max", 0xFFFFFFFFFFFFFFFFL, controllingAttribute.getTieBreaker());

        // Verify encoding/decoding preserves max value
        byte[] encoded = controllingAttribute.encode();
        IceControllingAttribute decoded = new IceControllingAttribute();
        decoded.decodeAttributeBody(encoded, Attribute.HEADER_LENGTH, 8);
        assertEquals("Max tie-breaker should survive encode/decode", 0xFFFFFFFFFFFFFFFFL, decoded.getTieBreaker());
    }
}
