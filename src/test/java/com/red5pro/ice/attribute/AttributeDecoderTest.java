/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.*;

import com.red5pro.ice.Transport;

import junit.framework.*;

/**
 * We have already tested individual decode methods, so our job here
 * is to verify that that AttributeDecoder.decode distributes the right way.
 */
public class AttributeDecoderTest extends TestCase {
    private MsgFixture msgFixture;

    private byte[] expectedAttributeValue = null;

    public AttributeDecoderTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();

        msgFixture = new MsgFixture();

        //init a sample body
        int offset = Attribute.HEADER_LENGTH;
        expectedAttributeValue = new byte[msgFixture.unknownOptionalAttribute.length - offset];
        System.arraycopy(msgFixture.unknownOptionalAttribute, offset, expectedAttributeValue, 0, expectedAttributeValue.length);
    }

    protected void tearDown() throws Exception {
        msgFixture = null;
        super.tearDown();
    }

    public void testDecodeMappedAddress() throws Exception {
        //
        byte[] bytes = msgFixture.mappedAddress;
        char offset = 0;
        char length = (char) bytes.length;

        //create the message
        MappedAddressAttribute expectedReturn = new MappedAddressAttribute(new TransportAddress(MsgFixture.ADDRESS_ATTRIBUTE_ADDRESS, MsgFixture.ADDRESS_ATTRIBUTE_PORT, Transport.UDP));

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);

        assertEquals("AttributeDecoder.decode() failed for a MAPPED-ADDRESS attribute", expectedReturn, actualReturn);
    }

    public void testDecodeMappedAddress_v6() throws Exception {
        //
        byte[] bytes = msgFixture.mappedAddressv6;
        char offset = 0;
        char length = (char) bytes.length;

        //create the message
        MappedAddressAttribute expectedReturn = new MappedAddressAttribute(new TransportAddress(MsgFixture.ADDRESS_ATTRIBUTE_ADDRESS_V6, MsgFixture.ADDRESS_ATTRIBUTE_PORT, Transport.UDP));

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);

        assertEquals("AttributeDecoder.decode() failed for a MAPPED-ADDRESS attribute", expectedReturn, actualReturn);
    }

    public void testDecodeChangeRequest() throws Exception {
        //
        byte[] bytes = msgFixture.chngReqTestValue1;
        char offset = 0;
        char length = (char) bytes.length;

        //create the message
        ChangeRequestAttribute expectedReturn = new ChangeRequestAttribute();
        expectedReturn.setChangeIpFlag(MsgFixture.CHANGE_IP_FLAG_1);
        expectedReturn.setChangePortFlag(MsgFixture.CHANGE_PORT_FLAG_1);

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);
        assertEquals("AttributeDecoder.decode() failed for a CHANGE-REQUEST attribute", expectedReturn, actualReturn);

    }

    public void testDecodeErrorCode() throws Exception {
        //
        byte[] bytes = msgFixture.errCodeTestValue;
        char offset = 0;
        char length = (char) bytes.length;

        //create the message
        ErrorCodeAttribute expectedReturn = new ErrorCodeAttribute();
        expectedReturn.setErrorClass(MsgFixture.ERROR_CLASS);
        expectedReturn.setErrorNumber(MsgFixture.ERROR_NUMBER);
        expectedReturn.setReasonPhrase(MsgFixture.REASON_PHRASE);

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);
        assertEquals("AttributeDecoder.decode() failed for a ERROR-CODE attribute", expectedReturn, actualReturn);

    }

    public void testDecodeUnknownAttributes() throws Exception {
        //unknown attributes
        byte[] bytes = msgFixture.unknownAttsDecodeTestValue;
        char offset = 0;
        char length = (char) msgFixture.mappedAddress.length;

        //create the message
        UnknownAttributesAttribute expectedReturn = new UnknownAttributesAttribute();
        expectedReturn.addAttributeID(MsgFixture.UNKNOWN_ATTRIBUTES_1ST_ATT);
        expectedReturn.addAttributeID(MsgFixture.UNKNOWN_ATTRIBUTES_2ND_ATT);
        expectedReturn.addAttributeID(MsgFixture.UNKNOWN_ATTRIBUTES_3D_ATT);

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);

        assertEquals("AttributeDecoder.decode() failed for a ERROR-CODE attribute", expectedReturn, actualReturn);

    }

    public void testDecodeUnknownOptionalAttribute() throws Exception {
        //unknown attributes
        byte[] bytes = msgFixture.unknownOptionalAttribute;
        int offset = 0;
        int length = msgFixture.mappedAddress.length;

        //create the message
        OptionalAttribute expectedReturn = new OptionalAttribute();
        expectedReturn.setBody(expectedAttributeValue, 0, expectedAttributeValue.length);

        Attribute actualReturn = AttributeDecoder.decode(bytes, offset, length);
        assertEquals("AttributeDecoder.decode() failed for a UNKNOWN_OPTIONAL attribute", expectedReturn, actualReturn);

    }
}
