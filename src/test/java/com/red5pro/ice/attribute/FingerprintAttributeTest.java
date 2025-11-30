/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Arrays;

import junit.framework.TestCase;

import com.red5pro.ice.StunException;

/**
 * Tests the FINGERPRINT attribute as defined in RFC 5389 Section 15.5.
 *
 * The FINGERPRINT attribute MAY be present in all STUN messages. The value of
 * the attribute is computed as the CRC-32 of the STUN message up to (but
 * excluding) the FINGERPRINT attribute itself, XOR'd with the 32-bit value
 * 0x5354554e.
 *
 * The FINGERPRINT attribute can aid in distinguishing STUN packets from packets
 * of other protocols.
 *
 * @author Red5 Pro
 */
public class FingerprintAttributeTest extends TestCase {

    private FingerprintAttribute fingerprintAttribute;

    protected void setUp() throws Exception {
        super.setUp();
        fingerprintAttribute = new FingerprintAttribute();
    }

    protected void tearDown() throws Exception {
        fingerprintAttribute = null;
        super.tearDown();
    }

    /**
     * Test that the attribute type is correct per RFC 5389.
     */
    public void testAttributeType() {
        assertEquals("FINGERPRINT attribute should have correct type", Attribute.Type.FINGERPRINT, fingerprintAttribute.getAttributeType());
        assertEquals("FINGERPRINT type value should be 0x8028", 0x8028, Attribute.Type.FINGERPRINT.type);
    }

    /**
     * Test that data length is 4 bytes (CRC-32 value).
     */
    public void testDataLength() {
        assertEquals("FINGERPRINT data length should be 4 bytes", 4, fingerprintAttribute.getDataLength());
    }

    /**
     * Test the XOR mask value as specified in RFC 5389.
     */
    public void testXorMask() {
        byte[] expectedMask = new byte[] { 0x53, 0x54, 0x55, 0x4e }; // "STUN"
        assertTrue("XOR mask should be 0x5354554e (STUN)", Arrays.equals(expectedMask, FingerprintAttribute.XOR_MASK));
    }

    /**
     * Test CRC-32 calculation with XOR mask.
     * Uses a known message to verify the calculation.
     */
    public void testCalculateXorCRC32() {
        // Simple test message
        byte[] testMessage = new byte[] { 0x00, 0x01, 0x00, 0x00, // STUN header type and length
                0x21, 0x12, (byte) 0xA4, 0x42, // Magic cookie
                0x01, 0x02, 0x03, 0x04, // Transaction ID
                0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c };

        byte[] crc = FingerprintAttribute.calculateXorCRC32(testMessage, 0, testMessage.length);

        assertNotNull("CRC should not be null", crc);
        assertEquals("CRC should be 4 bytes", 4, crc.length);

        // Verify CRC is deterministic
        byte[] crc2 = FingerprintAttribute.calculateXorCRC32(testMessage, 0, testMessage.length);
        assertTrue("Same input should produce same CRC", Arrays.equals(crc, crc2));
    }

    /**
     * Test that different messages produce different CRCs.
     */
    public void testDifferentMessagesDifferentCRC() {
        byte[] message1 = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        byte[] message2 = new byte[] { 0x01, 0x02, 0x03, 0x05 };

        byte[] crc1 = FingerprintAttribute.calculateXorCRC32(message1, 0, message1.length);
        byte[] crc2 = FingerprintAttribute.calculateXorCRC32(message2, 0, message2.length);

        assertFalse("Different messages should produce different CRCs", Arrays.equals(crc1, crc2));
    }

    /**
     * Test decoding of FINGERPRINT attribute body.
     */
    public void testDecodeAttributeBody() throws StunException {
        // Raw attribute with checksum value 0x12345678
        byte[] attributeValue = new byte[] { (byte) 0x80, 0x28, // type
                0x00, 0x04, // length
                0x12, 0x34, 0x56, 0x78 // checksum
        };

        fingerprintAttribute.decodeAttributeBody(attributeValue, Attribute.HEADER_LENGTH, 4);

        byte[] checksum = fingerprintAttribute.getChecksum();
        assertNotNull("Checksum should not be null after decode", checksum);
        assertEquals("Checksum length should be 4", 4, checksum.length);
        assertEquals("Checksum byte 0", 0x12, checksum[0] & 0xFF);
        assertEquals("Checksum byte 1", 0x34, checksum[1] & 0xFF);
        assertEquals("Checksum byte 2", 0x56, checksum[2] & 0xFF);
        assertEquals("Checksum byte 3", 0x78, checksum[3] & 0xFF);
    }

    /**
     * Test that decode throws exception for invalid length.
     */
    public void testDecodeInvalidLength() {
        byte[] invalidAttribute = new byte[] { (byte) 0x80, 0x28, // type
                0x00, 0x03, // invalid length (should be 4)
                0x12, 0x34, 0x56 // only 3 bytes
        };

        try {
            fingerprintAttribute.decodeAttributeBody(invalidAttribute, Attribute.HEADER_LENGTH, 3);
            fail("Should throw StunException for invalid length");
        } catch (StunException e) {
            // Expected
        }
    }

    /**
     * Test equals method.
     */
    public void testEquals() {
        FingerprintAttribute other = new FingerprintAttribute();

        // Test equality (without checksum set, should be equal)
        assertTrue("Two FINGERPRINT attributes should be equal", fingerprintAttribute.equals(other));

        // Test equality with self
        assertTrue("Attribute should equal itself", fingerprintAttribute.equals(fingerprintAttribute));

        // Test inequality with null
        assertFalse("Attribute should not equal null", fingerprintAttribute.equals(null));

        // Test inequality with different attribute type
        PriorityAttribute priorityAttr = new PriorityAttribute();
        assertFalse("FINGERPRINT should not equal PRIORITY attribute", fingerprintAttribute.equals(priorityAttr));
    }

    /**
     * Test that the encode() method throws UnsupportedOperationException
     * since FINGERPRINT is a ContentDependentAttribute.
     */
    public void testEncodeThrowsException() {
        try {
            fingerprintAttribute.encode();
            fail("encode() should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected - ContentDependentAttributes must use the content-dependent encode
        }
    }

    /**
     * Test that checksum is null before decode.
     */
    public void testChecksumNullBeforeDecode() {
        assertNull("Checksum should be null before decode", fingerprintAttribute.getChecksum());
    }

    /**
     * Test CRC calculation with offset parameter.
     */
    public void testCalculateXorCRC32WithOffset() {
        byte[] paddedMessage = new byte[] { 0x00, 0x00, 0x00, // padding
                0x01, 0x02, 0x03, 0x04 // actual message
        };

        byte[] crcWithOffset = FingerprintAttribute.calculateXorCRC32(paddedMessage, 3, 4);
        byte[] crcDirect = FingerprintAttribute.calculateXorCRC32(new byte[] { 0x01, 0x02, 0x03, 0x04 }, 0, 4);

        assertTrue("CRC with offset should match CRC of extracted message", Arrays.equals(crcWithOffset, crcDirect));
    }
}
