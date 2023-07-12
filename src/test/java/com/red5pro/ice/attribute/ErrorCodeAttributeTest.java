/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import junit.framework.*;

import java.util.Arrays;

import com.red5pro.ice.*;

import com.red5pro.ice.StunException;

/**
 *
 * @author Emil Ivov
 */
public class ErrorCodeAttributeTest extends TestCase {
    private ErrorCodeAttribute errorCodeAttribute = null;
    private MsgFixture msgFixture;

    public ErrorCodeAttributeTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        errorCodeAttribute = new ErrorCodeAttribute();
        msgFixture = new MsgFixture();
    }

    protected void tearDown() throws Exception {
        errorCodeAttribute = null;
        msgFixture = null;
        super.tearDown();
    }

    /**
     * Test Attribute type
     */
    public void testErrorCodeAttribute()
    {

        errorCodeAttribute = new ErrorCodeAttribute();

        assertEquals("ErrorCodeAttribute() constructed an attribute with an invalid type",
                     Attribute.Type.ERROR_CODE,
                     errorCodeAttribute.getAttributeType());
    }

    /**
     * Test whether sample binary arrays are properly decoded.
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testDecodeAttributeBody()
        throws StunException {
        byte[] attributeValue = msgFixture.errCodeTestValue;
        char offset = Attribute.HEADER_LENGTH;
        char length = (char)(attributeValue.length - Attribute.HEADER_LENGTH);
        errorCodeAttribute.decodeAttributeBody(attributeValue, offset, length);

        assertEquals("Error Class was not correctly decoded",
                     MsgFixture.ERROR_CLASS,
                     errorCodeAttribute.getErrorClass());

        assertEquals("Error Number was not correctly decoded",
                     MsgFixture.ERROR_NUMBER,
                     errorCodeAttribute.getErrorNumber());

        assertEquals("Reason phrase was not correctly decoded",
                     MsgFixture.REASON_PHRASE.trim(),
                     errorCodeAttribute.getReasonPhrase().trim());

    }

    /**
     * Construct and encode a sample object and assert equality with a sample
     * binary array.
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testEncode()
        throws StunException
    {
        byte[] expectedReturn = msgFixture.errCodeTestValue;

        errorCodeAttribute.setErrorClass(MsgFixture.ERROR_CLASS);
        errorCodeAttribute.setErrorNumber(MsgFixture.ERROR_NUMBER);

        errorCodeAttribute.setReasonPhrase(MsgFixture.REASON_PHRASE);

        byte[] actualReturn = errorCodeAttribute.encode();

        assertTrue("encode() did not return the expected binary array.",
                   Arrays.equals( expectedReturn, actualReturn));
    }

    /**
     * Tests the equals method against a null, a different and an identical
     * object.
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testEquals()
        throws StunException
    {

        //null value test
        ErrorCodeAttribute target = null;
        boolean expectedReturn = false;
        boolean actualReturn = errorCodeAttribute.equals(target);
        assertEquals("equals() failed against a null value target.",
                     expectedReturn, actualReturn);

        //different objects
        target = new ErrorCodeAttribute();
        expectedReturn = false;

        target.setErrorClass(MsgFixture.ERROR_CLASS);
        target.setErrorNumber(MsgFixture.ERROR_NUMBER);

        errorCodeAttribute.setErrorClass((byte)(MsgFixture.ERROR_CLASS+1));
        errorCodeAttribute.setErrorNumber((byte)(MsgFixture.ERROR_NUMBER+1));

        actualReturn = errorCodeAttribute.equals(target);
        assertEquals("equals() failed against a not equal target.",
                     expectedReturn, actualReturn);

        //different objects
        target = new ErrorCodeAttribute();
        errorCodeAttribute = new ErrorCodeAttribute();
        expectedReturn = true;

        target.setErrorClass(MsgFixture.ERROR_CLASS);
        target.setErrorNumber(MsgFixture.ERROR_NUMBER);

        errorCodeAttribute.setErrorClass(MsgFixture.ERROR_CLASS);
        errorCodeAttribute.setErrorNumber(MsgFixture.ERROR_NUMBER);

        actualReturn = errorCodeAttribute.equals(target);
        assertEquals("equals() failed against a not equal target.",
                     expectedReturn, actualReturn);


    }

    /**
     * Test whether data length is propertly calculated.
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testGetDataLength()
        throws StunException
    {
        int expectedReturn = MsgFixture.REASON_PHRASE.getBytes().length
                            + 4; //error code specific header

        errorCodeAttribute.setErrorClass(MsgFixture.ERROR_CLASS);
        errorCodeAttribute.setErrorNumber(MsgFixture.ERROR_NUMBER);
        errorCodeAttribute.setReasonPhrase(MsgFixture.REASON_PHRASE);

        int actualReturn = errorCodeAttribute.getDataLength();
        assertEquals("data length1", expectedReturn, actualReturn);
    }

    /**
     * Test whether error code is properly calculated from error class and number
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testGetErrorCode()
        throws StunException
    {
        char expectedReturn = (char)(100*MsgFixture.ERROR_CLASS
                                     + MsgFixture.ERROR_NUMBER);

        errorCodeAttribute.setErrorClass(MsgFixture.ERROR_CLASS);
        errorCodeAttribute.setErrorNumber(MsgFixture.ERROR_NUMBER);

        char actualReturn = errorCodeAttribute.getErrorCode();
        assertEquals("return value", expectedReturn, actualReturn);
    }

    /**
     * Test whether we get a proper name for that attribute.
     */
    public void testGetName() {
        String expectedReturn = "ERROR_CODE";
        String actualReturn = errorCodeAttribute.getName();
        assertEquals("return value", expectedReturn, actualReturn);

    }

    /**
     * Test whether error code is properly calculated from error class and number
     *
     * @throws StunException java.lang.Exception if we fail
     */
    public void testSetErrorCode() throws StunException {
        char errorCode = (char)(MsgFixture.ERROR_CLASS*100 + MsgFixture.ERROR_NUMBER);
        errorCodeAttribute.setErrorCode(errorCode);

        assertEquals("An error class was not properly set after decoding an error code.",
                     (int)MsgFixture.ERROR_CLASS,
                     (int)errorCodeAttribute.getErrorClass());
        assertEquals("An error number was not properly set after decoding an error code.",
                     (int)MsgFixture.ERROR_NUMBER,
                     (int)errorCodeAttribute.getErrorNumber());
    }


}
