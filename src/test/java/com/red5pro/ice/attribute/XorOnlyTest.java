/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.*;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 * @author Emil Ivov
 */
public class XorOnlyTest extends TestCase {
    private XorOnlyAttribute xorOnly = null;
    private MsgFixture msgFixture = null;

    protected void setUp() throws Exception {
        super.setUp();
        xorOnly = new XorOnlyAttribute();
        msgFixture = new MsgFixture();
    }

    protected void tearDown() throws Exception {
        xorOnly = null;
        msgFixture = null;
        super.tearDown();
    }

    /**
     * Just makes sure that no exceptions are thrown when calling it as the
     * decode method doesn't do anything in the XorOnly att.
     * @throws StunException if sth happens
     */
    public void testDecodeAttributeBody() throws StunException {
        byte[] attributeValue = new byte[] {};
        char offset = 0;
        char length = 0;
        xorOnly.decodeAttributeBody(attributeValue, offset, length);
    }

    /**
     * Test encoding XorOnly attributes.
     */
    public void testEncode() {
        byte[] expectedReturn = new byte[] { (byte) (Attribute.Type.XOR_ONLY.type >> 8), (byte) (Attribute.Type.XOR_ONLY.type & 0x00FF), 0,
                0 };
        byte[] actualReturn = xorOnly.encode();
        assertTrue("XorOnly failed to encode", Arrays.equals(expectedReturn, actualReturn));
    }

    /**
     * Test positive and negative XorOnly.equals() returns
     * @throws Exception if decoding fails
     */
    @SuppressWarnings("unlikely-arg-type")
    public void testEquals() throws Exception {
        XorOnlyAttribute xor2 = new XorOnlyAttribute();
        assertEquals("equals() failes for XorOnly", xorOnly, xor2);

        MappedAddressAttribute maatt = new MappedAddressAttribute();
        maatt.decodeAttributeBody(msgFixture.mappedAddress, (char) 0, (char) msgFixture.mappedAddress.length);


        assertFalse("equals failed to see a difference", xorOnly.equals(maatt));
        assertFalse("equals failed for null", xorOnly.equals(null));
    }

    /**
     * Makes sure the data langth is 0
     */
    public void testGetDataLength() {
        int expectedReturn = 0;
        int actualReturn = xorOnly.getDataLength();
        assertEquals("data length was not 0", expectedReturn, actualReturn);
    }

    /**
     * Verifies the name (do we really need this?).
     */
    public void testGetName() {
        String expectedReturn = "XOR_ONLY";
        String actualReturn = xorOnly.getName();
        assertEquals("Is name correct", expectedReturn, actualReturn);
    }
}
