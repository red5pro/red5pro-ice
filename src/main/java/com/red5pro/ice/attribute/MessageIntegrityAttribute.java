/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.red5pro.ice.message.Message;
import com.red5pro.ice.stack.StunStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MESSAGE-INTEGRITY attribute contains an HMAC-SHA1 [RFC2104] of
 * the STUN message.  The MESSAGE-INTEGRITY attribute can be present in
 * any STUN message type.  Since it uses the SHA1 hash, the HMAC will be
 * 20 bytes.  The text used as input to HMAC is the STUN message,
 * including the header, up to and including the attribute preceding the
 * MESSAGE-INTEGRITY attribute.  With the exception of the FINGERPRINT
 * attribute, which appears after MESSAGE-INTEGRITY, agents MUST ignore
 * all other attributes that follow MESSAGE-INTEGRITY.
 * The key for the HMAC depends on whether long-term or short-term
 * credentials are in use.  For long-term credentials, the key is 16
 * bytes:
 * <pre>
 *          key = MD5(username ":" realm ":" SASLprep(password))
 * </pre>
 * That is, the 16-byte key is formed by taking the MD5 hash of the
 * result of concatenating the following five fields: (1) the username,
 * with any quotes and trailing nulls removed, as taken from the
 * USERNAME attribute (in which case SASLprep has already been applied);
 * (2) a single colon; (3) the realm, with any quotes and trailing nulls
 * removed; (4) a single colon; and (5) the password, with any trailing
 * nulls removed and after processing using SASLprep.  For example, if
 * the username was 'user', the realm was 'realm', and the password was
 * 'pass', then the 16-byte HMAC key would be the result of performing
 * an MD5 hash on the string 'user:realm:pass', the resulting hash being
 * 0x8493fbc53ba582fb4c044c456bdc40eb.
 * <p>
 * For short-term credentials:
 * <pre>
 *                        key = SASLprep(password)
 * </pre>
 * where MD5 is defined in RFC 1321 [RFC1321] and SASLprep() is defined
 * in RFC 4013 [RFC4013].
 * <p>
 * The structure of the key when used with long-term credentials
 * facilitates deployment in systems that also utilize SIP.  Typically,
 * SIP systems utilizing SIP's digest authentication mechanism do not
 * actually store the password in the database.  Rather, they store a
 * value called H(A1), which is equal to the key defined above.
 * <p>
 * Based on the rules above, the hash used to construct MESSAGE-
 * INTEGRITY includes the length field from the STUN message header.
 * Prior to performing the hash, the MESSAGE-INTEGRITY attribute MUST be
 * inserted into the message (with dummy content).  The length MUST then
 * be set to point to the length of the message up to, and including,
 * the MESSAGE-INTEGRITY attribute itself, but excluding any attributes
 * after it.  Once the computation is performed, the value of the
 * MESSAGE-INTEGRITY attribute can be filled in, and the value of the
 * length in the STUN header can be set to its correct value -- the
 * length of the entire message.  Similarly, when validating the
 * MESSAGE-INTEGRITY, the length field should be adjusted to point to
 * the end of the MESSAGE-INTEGRITY attribute prior to calculating the
 * HMAC.  Such adjustment is necessary when attributes, such as
 * FINGERPRINT, appear after MESSAGE-INTEGRITY.
 *
 * @author Emil Ivov
 */
public class MessageIntegrityAttribute extends Attribute implements ContentDependentAttribute {

    private static final Logger logger = LoggerFactory.getLogger(MessageIntegrityAttribute.class);

    /**
     * The HMAC-SHA1 algorithm.
     */
    public static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * The HMAC-SHA1 algorithm.
     */
    public static final int DATA_LENGTH = 20;

    /**
     * The actual content of the message
     */
    private byte[] hmacSha1Content;

    /**
     * The username that we should use to obtain an encryption
     * key (password) that the {@link #encode()} method should use when
     * creating the content of this message.
     */
    private String username;

    /**
     * The media name if we use short-term authentication.
     */
    private String media;

    /**
     * Creates a MessageIntegrityAttribute.
     */
    protected MessageIntegrityAttribute() {
        super(Attribute.Type.MESSAGE_INTEGRITY);
    }

    /**
     * Sets the username that we should use to obtain an encryption
     * key (password) that the {@link #encode()} method should use when
     * creating the content of this message.
     *
     * @param username the username that we should use to obtain an encryption
     * key (password) that the {@link #encode()} method should use when
     * creating the content of this message.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Sets the media name that we should use to get the corresponding remote
     * key (short-term authentication only).
     *
     * @param media name
     */
    public void setMedia(String media) {
        this.media = media;
    }

    /**
     * Returns the HMAC-SHA1 value stored in this attribute.
     *
     * @return the HMAC-SHA1 value stored in this attribute.
     */
    public byte[] getHmacSha1Content() {
        return hmacSha1Content;
    }

    /**
     * Encodes message using key and the HMAC-SHA1 algorithm
     * as per RFC 2104 and returns the resulting byte array. This is a utility
     * method that generates content for the {@link MessageIntegrityAttribute}
     * regardless of the credentials being used (short or long term).
     *
     * @param message the STUN message that the resulting content will need to
     * travel in.
     * @param offset the index where data starts in message.
     * @param length the length of the data in message that the method
     * should consider.
     * @param key the key that we should be using for the encoding (which
     * depends on whether we are using short or long term credentials).
     *
     * @return the HMAC that should be used in a
     * MessageIntegrityAttribute transported by message.
     *
     * @throws IllegalArgumentException if the encoding fails for some reason.
     */
    public static byte[] calculateHmacSha1(byte[] message, int offset, int length, byte[] key) throws IllegalArgumentException {
        byte[] hmac;
        try {
            // get an HMAC-SHA1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
            // get an HMAC-SHA1 Mac instance and initialize it with the key
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            // compute the hmac on input data bytes
            byte[] macInput = new byte[length];
            //doFinal seems incapable to only work with a part of an array
            //so we'd need to create an array that only contains what we
            //actually need to work with.
            System.arraycopy(message, offset, macInput, 0, length);
            hmac = mac.doFinal(macInput);
        } catch (Exception exc) {
            throw new IllegalArgumentException("Could not create HMAC-SHA1 request encoding", exc);
        }
        return hmac;
    }

    /**
     * Sets this attribute's fields according to the message and attributeValue
     * arrays.
     *
     * @param attributeValue a binary array containing this attribute's field
     * values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     * offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     * the start of this attribute.
     */
    public void decodeAttributeBody(byte[] attributeValue, int offset, int length) {
        hmacSha1Content = new byte[length];
        System.arraycopy(attributeValue, offset, hmacSha1Content, 0, length);
    }

    /**
     * Returns a binary representation of this attribute.
     *
     * @return nothing
     * @throws UnsupportedOperationException since {@link
     * ContentDependentAttribute}s should be encoded through the content
     * dependent encode method.
     */
    public byte[] encode() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("ContentDependentAttributes should be encoded through the content-dependent encode method");
    }

    /**
     * Returns a binary representation of this attribute.
     *
     * @param stunStack the StunStack in the context of which the
     * request to encode this ContentDependentAttribute is being made
     * @param content the content of the message that this attribute will be
     * transported in
     * @param offset the content-related offset where the actual
     * content starts.
     * @param length the length of the content in the content array.
     *
     * @return a binary representation of this attribute valid for the message
     * with the specified content.
     */
    public byte[] encode(StunStack stunStack, byte[] content, int offset, int length) {
        //if (logger.isDebugEnabled()) {
        //    logger.debug("encode - offset: {} length: {}\n{}", offset, length, StunStack.toHexString(content));
        //}
        byte binValue[] = new byte[HEADER_LENGTH + getDataLength()];
        //Type
        binValue[0] = (byte) (attributeType.type >> 8);
        binValue[1] = (byte) (attributeType.type & 0x00FF);
        //Length
        binValue[2] = (byte) (getDataLength() >> 8);
        binValue[3] = (byte) (getDataLength() & 0x00FF);
        byte[] key = null;
        char msgType = (char) (((content[0] & 0xFF) << 8) | (content[1] & 0xFF));
        if (Message.isRequestType(msgType) || Message.isIndicationType(msgType)) {
            // attribute part of a request, use the remote key
            key = stunStack.getCredentialsManager().getRemoteKey(username, media);
        } else {
            // default to using the local key to prevent any missing parameters passed to hash/key routine
            //} else if (Message.isResponseType(msgType)) {
            // attribute part of a response, use the local key
            key = stunStack.getCredentialsManager().getLocalKey(username);
        }
        if (key.length > 0) {
            // now calculate the HMAC-SHA1
            hmacSha1Content = calculateHmacSha1(content, offset, length, key);
            //username
            System.arraycopy(hmacSha1Content, 0, binValue, 4, getDataLength());
        } else {
            logger.warn("Key bytes were not found for {}", username);
        }
        return binValue;
    }

    /**
     * Returns the length of this attribute's body.
     *
     * @return the length of this attribute's value.
     */
    public int getDataLength() {
        return DATA_LENGTH;
    }

    /**
     * Compares two MessageIntegrityAttributes. Two attributes are
     * considered equal when they have the same type length and value.
     *
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof MessageIntegrityAttribute)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        MessageIntegrityAttribute att = (MessageIntegrityAttribute) obj;
        if (att.getAttributeType() != attributeType || att.getDataLength() != getDataLength()
                || !Arrays.equals(att.hmacSha1Content, hmacSha1Content)) {
            return false;
        }
        return true;
    }
}
