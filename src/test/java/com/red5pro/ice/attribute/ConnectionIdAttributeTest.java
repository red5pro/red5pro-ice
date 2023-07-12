/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

import java.util.*;

import junit.framework.*;

/**
 * Class to test the ConnectionIdAttribute class.
 * 
 * @author Aakash Garg
 * 
 */
public class ConnectionIdAttributeTest
    extends TestCase
{
    private ConnectionIdAttribute connectionIdAttribute = null;

    private MsgFixture msgFixture;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        this.connectionIdAttribute = new ConnectionIdAttribute();
        this.msgFixture = new MsgFixture();
    }

    @Override
    protected void tearDown() throws Exception
    {
        this.connectionIdAttribute = null;
        this.msgFixture = null;
        super.tearDown();
    }

    /**
     * Tests whether data length is properly calculated.
     */
    public void testGetDataLength()
    {
        int expectedReturn = 4;
        this.connectionIdAttribute
            .setConnectionIdValue(MsgFixture.CONNECTION_ID);
        int actualReturn = this.connectionIdAttribute.getDataLength();
        assertEquals(
            "Datalength is not properly calculated", expectedReturn,
            actualReturn);
    }

    /**
     * Tests getting the name.
     */
    public void testGetName()
    {
        String expectedReturn = "CONNECTION_ID";
        String actualReturn = connectionIdAttribute.getName();
        assertEquals(
            "getting name failed", expectedReturn, actualReturn);
    }

    /**
     * Tests the equals method against a null, a different and an identical
     * object.
     */
    public void testEqualsObject()
    {
        // null test
        ConnectionIdAttribute target = null;
        boolean expectedReturn = false;
        boolean actualReturn = connectionIdAttribute.equals(target);

        assertEquals(
            "ConnectionIdAttribute.equals() failed against a null target.",
            expectedReturn, actualReturn);

        // difference test
        target = new ConnectionIdAttribute();

        int connectionId = MsgFixture.CONNECTION_ID_2;
        target.setConnectionIdValue(connectionId);

        connectionIdAttribute.setConnectionIdValue(MsgFixture.CONNECTION_ID);
        expectedReturn = false;
        actualReturn = connectionIdAttribute.equals(target);
        assertEquals(
            "ConnectionIdAttribute.equals() failed against a different target.",
            expectedReturn, actualReturn);

        // equality test
        target.setConnectionIdValue(MsgFixture.CONNECTION_ID);

        expectedReturn = true;
        actualReturn = connectionIdAttribute.equals(target);
        assertEquals(
            "ConnectionIdAttribute.equals() failed against an equal target.",
            expectedReturn, actualReturn);
    }

    /**
     * Test whether attributes are properly encoded.
     */
    public void testEncode()
    {
        byte[] expectedReturn = msgFixture.connectionId;
        connectionIdAttribute.setConnectionIdValue(MsgFixture.CONNECTION_ID);
        byte[] actualReturn = connectionIdAttribute.encode();

        assertTrue(
            "ConnectionIdAttribute.encode() did not "
                + "properly encode a sample attribute", Arrays.equals(
                expectedReturn, actualReturn));
    }

    /**
     * Test whether sample binary arrays are correctly decoded.
     * 
     * @throws StunException if something goes wrong while decoding 
     *             Attribute Body.
     */
    public void testDecodeAttributeBody() throws StunException
    {
        byte[] attributeValue = msgFixture.connectionId;
        char offset = Attribute.HEADER_LENGTH;
        char length = (char) (attributeValue.length - offset);

        connectionIdAttribute.decodeAttributeBody(
            attributeValue, offset, length);

        assertEquals(
            "ConnectionIdAttribute.decode() did not properly decode the "
                + "connection id field.", MsgFixture.CONNECTION_ID,
            connectionIdAttribute.getConnectionIdValue());
    }

    /**
     * Tests that the connection Id is always integer.
     */
    public void testGetConnectionIdValue()
    {
        int expectedReturn = 0x5555;
        this.connectionIdAttribute
            .setConnectionIdValue(MsgFixture.CONNECTION_ID);
        int actualReturn = this.connectionIdAttribute.getConnectionIdValue();
        assertEquals(
            "ConnectionId is not properly calculated", expectedReturn,
            actualReturn);

        expectedReturn = 0x2222;
        this.connectionIdAttribute
            .setConnectionIdValue(MsgFixture.CONNECTION_ID_2);
        actualReturn = this.connectionIdAttribute.getConnectionIdValue();
        assertEquals(
            "ConnectionId is not properly calculated", expectedReturn,
            actualReturn);
    }

}
