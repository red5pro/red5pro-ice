/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.*;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 * Tests the software attribute class.
 *
 * @author Emil Ivov
 */
public class SoftwareAttributeTest extends TestCase
{
    private SoftwareAttribute softwareAttribute = null;
    MsgFixture msgFixture = null;
    String softwareValue = "turnserver.org";
    byte[] attributeBinValue = new byte[]{
            (byte)(SoftwareAttribute.Type.SOFTWARE.type>>8),
            (byte)(SoftwareAttribute.Type.SOFTWARE.type & 0x00FF),
            0, (byte)softwareValue.length(),
            't', 'u', 'r', 'n', 's', 'e', 'r','v', 'e', 'r', '.', 'o', 'r', 'g',
            0x00, 0x00};

    protected void setUp() throws Exception
    {
        super.setUp();
        msgFixture = new MsgFixture();

        softwareAttribute = new SoftwareAttribute();
        softwareAttribute.setSoftware(softwareValue.getBytes());
    }

    protected void tearDown() throws Exception
    {
        softwareAttribute = null;
        msgFixture = null;
        super.tearDown();
    }

    /**
     * Tests decoding of the software attribute.
     * @throws StunException upon a failure
     */
    public void testDecodeAttributeBody() throws StunException
    {
        char offset = 0;
        SoftwareAttribute decoded = new SoftwareAttribute();
        char length = (char)softwareValue.length();
        decoded.decodeAttributeBody(softwareValue.getBytes(), offset, length);

        //software value
        assertEquals( "decode failed", softwareAttribute, decoded);
    }

    /**
     * Tests the encode method
     */
    public void testEncode()
    {
        assertTrue("encode failed",
                   Arrays.equals(softwareAttribute.encode(),
                                 attributeBinValue));
    }

    /**
     * Test Equals
     */
    public void testEquals()
    {
        SoftwareAttribute softwareAttribute2 = new SoftwareAttribute();
        softwareAttribute2.setSoftware(softwareValue.getBytes());

        //test positive equals
        assertEquals("testequals failed", softwareAttribute, softwareAttribute2);

        //test negative equals
        softwareAttribute2 = new SoftwareAttribute();
        softwareAttribute2.setSoftware("some other software".getBytes());

        //test positive equals
        assertFalse("testequals failed",
                    softwareAttribute.equals(softwareAttribute2));

        //test null equals
        assertFalse("testequals failed",
                    softwareAttribute.equals(null));
    }

    /**
     * Tests extracting data length
     */
    public void testGetDataLength()
    {
        int expectedReturn = (char)softwareValue.length();
        int actualReturn = softwareAttribute.getDataLength();
        assertEquals("getDataLength - failed", expectedReturn, actualReturn);
    }

    /**
     * Tests getting the name
     */
    public void testGetName()
    {
        String expectedReturn = "SOFTWARE";
        String actualReturn = softwareAttribute.getName();
        assertEquals("getting name failed", expectedReturn, actualReturn);
    }

    public void testSetGetSoftware()
    {
        byte[] expectedReturn = softwareValue.getBytes();

        SoftwareAttribute att = new SoftwareAttribute();
        att.setSoftware(expectedReturn);

        byte[] actualReturn = att.getSoftware();
        assertTrue("software setter or getter failed",
                     Arrays.equals( expectedReturn,
                                    actualReturn));
    }
}
