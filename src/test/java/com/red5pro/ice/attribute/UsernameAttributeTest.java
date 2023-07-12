/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.*;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 * Tests the username attribute class.
 *
 * @author Emil Ivov
 * @author Sebastien Vincent
 */
public class UsernameAttributeTest extends TestCase
{
    private UsernameAttribute usernameAttribute = null;
    MsgFixture msgFixture = null;
    String usernameValue = "username";
    byte[] attributeBinValue = new byte[]{
            (byte)(UsernameAttribute.Type.USERNAME.type>>8),
            (byte)(UsernameAttribute.Type.USERNAME.type & 0x00FF),
            0, (byte)usernameValue.length(),
            'u', 's', 'e', 'r', 'n', 'a', 'm','e'};

    protected void setUp() throws Exception
    {
        super.setUp();
        msgFixture = new MsgFixture();

        usernameAttribute = new UsernameAttribute();
        usernameAttribute.setUsername(usernameValue.getBytes());
    }

    protected void tearDown() throws Exception
    {
        usernameAttribute = null;
        msgFixture = null;
        super.tearDown();
    }

    /**
     * Tests decoding of the username attribute.
     * @throws StunException upon a failure
     */
    public void testDecodeAttributeBody() throws StunException
    {
        char offset = 0;
        UsernameAttribute decoded = new UsernameAttribute();
        char length = (char)usernameValue.length();
        decoded.decodeAttributeBody(usernameValue.getBytes(), offset, length);

        //username value
        assertEquals( "decode failed", usernameAttribute, decoded);
    }

    /**
     * Tests the encode method
     */
    public void testEncode()
    {
        assertTrue("encode failed",
                   Arrays.equals(usernameAttribute.encode(),
                                 attributeBinValue));
    }

    /**
     * Test Equals
     */
    public void testEquals()
    {
        UsernameAttribute usernameAttribute2 = new UsernameAttribute();
        usernameAttribute2.setUsername(usernameValue.getBytes());

        //test positive equals
        assertEquals("testequals failed", usernameAttribute, usernameAttribute2);

        //test negative equals
        usernameAttribute2 = new UsernameAttribute();
        usernameAttribute2.setUsername("some other username".getBytes());

        //test positive equals
        assertFalse("testequals failed",
                    usernameAttribute.equals(usernameAttribute2));

        //test null equals
        assertFalse("testequals failed",
                    usernameAttribute.equals(null));
    }

    /**
     * Tests extracting data length
     */
    public void testGetDataLength()
    {
        int expectedReturn = (char)usernameValue.length();
        int actualReturn = usernameAttribute.getDataLength();
        assertEquals("getDataLength - failed", expectedReturn, actualReturn);
    }

    /**
     * Tests getting the name
     */
    public void testGetName()
    {
        String expectedReturn = "USERNAME";
        String actualReturn = usernameAttribute.getName();
        assertEquals("getting name failed", expectedReturn, actualReturn);
    }

    public void testSetGetUsername()
    {
        byte[] expectedReturn = usernameValue.getBytes();

        UsernameAttribute att = new UsernameAttribute();
        att.setUsername(expectedReturn);

        byte[] actualReturn = att.getUsername();
        assertTrue("username setter or getter failed",
                     Arrays.equals( expectedReturn,
                                    actualReturn));
    }
}
