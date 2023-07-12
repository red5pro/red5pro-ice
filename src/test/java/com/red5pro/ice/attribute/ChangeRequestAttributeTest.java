/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.Arrays;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 * Tests the CHANGE-REQUEST attribute class.
 *
 * @author Emil Ivov
 */
public class ChangeRequestAttributeTest extends TestCase
{
    private ChangeRequestAttribute changeRequestAttribute = null;
    private MsgFixture binMessagesFixture;

    public ChangeRequestAttributeTest(String name)
    {
        super(name);
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        changeRequestAttribute = new ChangeRequestAttribute();
        binMessagesFixture = new MsgFixture();
    }

    protected void tearDown() throws Exception
    {
        changeRequestAttribute = null;
        binMessagesFixture = null;
        super.tearDown();
    }

    /**
     * Test whether the constructed object has the proper type.
     */
    public void testChangeRequestAttribute()
    {
        changeRequestAttribute = new ChangeRequestAttribute();

        assertEquals(
            "ChangeRequestAttribute did not construct an attribute with the "
            +"correct type.",
            changeRequestAttribute.getAttributeType(),
            Attribute.Type.CHANGE_REQUEST);

    }

    /**
     * Test whether sample binary arrays are properly decoded.
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testDecodeAttributeBody()
        throws StunException
    {
        byte[] attributeValue = binMessagesFixture.chngReqTestValue1;
        char offset = Attribute.HEADER_LENGTH;
        char length = (char)(attributeValue.length - offset);
        changeRequestAttribute.decodeAttributeBody(attributeValue, offset, length);

        assertEquals("decodeAttributeBody() did not properly decode the changeIpFlag",
                     MsgFixture.CHANGE_IP_FLAG_1,
                     changeRequestAttribute.getChangeIpFlag()
                     );
        assertEquals("decodeAttributeBody() did not properly decode the changePortFlag",
                     MsgFixture.CHANGE_PORT_FLAG_1,
                     changeRequestAttribute.getChangePortFlag()
                     );

        //2nd sample
        attributeValue = binMessagesFixture.chngReqTestValue2;
        changeRequestAttribute.decodeAttributeBody(attributeValue, offset, length);
        assertEquals("decodeAttributeBody() did not properly decode the changeIpFlag",
                     MsgFixture.CHANGE_IP_FLAG_2,
                     changeRequestAttribute.getChangeIpFlag()
                     );
        assertEquals("decodeAttributeBody() did not properly decode the changePortFlag",
                     MsgFixture.CHANGE_PORT_FLAG_2,
                     changeRequestAttribute.getChangePortFlag()
                     );


        changeRequestAttribute.getChangePortFlag();
    }

    /**
     * Create sample objects and test whether they encode properly.
     */
    public void testEncode()
    {
        byte[] expectedReturn = binMessagesFixture.chngReqTestValue1;

        changeRequestAttribute = new ChangeRequestAttribute();

        changeRequestAttribute.setChangeIpFlag(MsgFixture.CHANGE_IP_FLAG_1);
        changeRequestAttribute.setChangePortFlag(MsgFixture.CHANGE_PORT_FLAG_1);

        byte[] actualReturn = changeRequestAttribute.encode();
        assertTrue("Object did not encode properly.",
                   Arrays.equals(expectedReturn, actualReturn));

        //2nd test
        expectedReturn = binMessagesFixture.chngReqTestValue2;
        changeRequestAttribute = new ChangeRequestAttribute();

        changeRequestAttribute.setChangeIpFlag(MsgFixture.CHANGE_IP_FLAG_2);
        changeRequestAttribute.setChangePortFlag(MsgFixture.CHANGE_PORT_FLAG_2);

        actualReturn = changeRequestAttribute.encode();
        assertTrue("Object did not encode properly.",
                   Arrays.equals(expectedReturn, actualReturn));


    }

    /**
     * Tests the equals method against a null, a different and an identical
     * object.
     */
    public void testEquals()
    {
        ChangeRequestAttribute target = null;
        boolean expectedReturn = false;

        //null test
        boolean actualReturn = changeRequestAttribute.equals(target);
        assertEquals("Null value test failed", expectedReturn, actualReturn);

        //test against a different object.
        target = new ChangeRequestAttribute();

        changeRequestAttribute.setChangeIpFlag(true);
        changeRequestAttribute.setChangePortFlag(false);

        target.setChangeIpFlag(false);
        target.setChangePortFlag(true);

        actualReturn = changeRequestAttribute.equals(target);
        assertEquals("Test against a different value failed",
                     expectedReturn, actualReturn);

        //test against an equal value
        target = new ChangeRequestAttribute();

        changeRequestAttribute.setChangeIpFlag(true);
        changeRequestAttribute.setChangePortFlag(false);

        target.setChangeIpFlag(true);
        target.setChangePortFlag(false);

        expectedReturn = true;
        actualReturn = changeRequestAttribute.equals(target);
        assertEquals("Test against an equals value failed",
                     expectedReturn, actualReturn);


    }
    /**
     * Test whether the returned value is always 4.
     */
    public void testGetDataLength()
    {
        int expectedReturn = 4; // constant 4 bytes of data
        int actualReturn = changeRequestAttribute.getDataLength();
        assertEquals("data length returned an invalid value",
                     expectedReturn, actualReturn);
    }

    /**
     * Test whether we get a relevant name.
     */
    public void testGetName()
    {
        String expectedReturn = "CHANGE_REQUEST";
        String actualReturn = changeRequestAttribute.getName();
        assertEquals("Invalid name", expectedReturn, actualReturn);
    }

}
