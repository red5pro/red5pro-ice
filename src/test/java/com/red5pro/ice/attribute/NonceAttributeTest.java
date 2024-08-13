/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.*;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 * Tests the nonce attribute class.
 *
 * @author Emil Ivov
 * @author Sebastien Vincent
 */
public class NonceAttributeTest extends TestCase {
    private NonceAttribute nonceAttribute = new NonceAttribute();
    MsgFixture msgFixture = null;
    String nonceValue = "0123456789abcdef";
    byte[] attributeBinValue = new byte[] { (byte) (nonceAttribute.getAttributeType().type >> 8),
            (byte) (nonceAttribute.getAttributeType().type & 0x00FF), 0, (byte) nonceValue.length(), '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    protected void setUp() throws Exception {
        super.setUp();
        msgFixture = new MsgFixture();

        nonceAttribute = new NonceAttribute();
        nonceAttribute.setNonce(nonceValue.getBytes());
    }

    protected void tearDown() throws Exception {
        nonceAttribute = null;
        msgFixture = null;
        super.tearDown();
    }

    /**
     * Tests decoding of the nonce attribute.
     * @throws StunException upon a failure
     */
    public void testDecodeAttributeBody() throws StunException {
        char offset = 0;
        NonceAttribute decoded = new NonceAttribute();
        char length = (char) nonceValue.length();
        decoded.decodeAttributeBody(nonceValue.getBytes(), offset, length);

        //nonce value
        assertEquals("decode failed", nonceAttribute, decoded);
    }

    /**
     * Tests the encode method
     */
    public void testEncode() {
        assertTrue("encode failed", Arrays.equals(nonceAttribute.encode(), attributeBinValue));
    }

    /**
     * Test Equals
     */
    public void testEquals() {
        NonceAttribute nonceAttribute2 = new NonceAttribute();
        nonceAttribute2.setNonce(nonceValue.getBytes());

        //test positive equals
        assertEquals("testequals failed", nonceAttribute, nonceAttribute2);

        //test negative equals
        nonceAttribute2 = new NonceAttribute();
        nonceAttribute2.setNonce("some other nonce".getBytes());

        //test positive equals
        assertFalse("testequals failed", nonceAttribute.equals(nonceAttribute2));

        //test null equals
        assertFalse("testequals failed", nonceAttribute.equals(null));
    }

    /**
     * Tests extracting data length
     */
    public void testGetDataLength() {
        int expectedReturn = (char) nonceValue.length();
        int actualReturn = nonceAttribute.getDataLength();
        assertEquals("getDataLength - failed", expectedReturn, actualReturn);
    }

    /**
     * Tests getting the name
     */
    public void testGetName() {
        String expectedReturn = "NONCE";
        String actualReturn = nonceAttribute.getName();
        assertEquals("getting name failed", expectedReturn, actualReturn);
    }

    public void testSetGetNonce() {
        byte[] expectedReturn = nonceValue.getBytes();

        NonceAttribute att = new NonceAttribute();
        att.setNonce(expectedReturn);

        byte[] actualReturn = att.getNonce();
        assertTrue("nonce setter or getter failed", Arrays.equals(expectedReturn, actualReturn));
    }
}
