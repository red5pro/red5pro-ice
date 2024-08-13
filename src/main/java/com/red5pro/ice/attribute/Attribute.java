/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.StunException;

/**
 * After the header are 0 or more attributes.  Each attribute is TLV
 * encoded, with a 16 bit type, 16 bit length, and variable value:
 *<pre>
 *     0                   1                   2                   3
 *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    |         Type                  |            Length             |
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    |                             Value                             ....
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 *    The following types are defined:
 *
 * STUN attributes:
 *    0x0001: MAPPED-ADDRESS
 *    0x0002: RESPONSE-ADDRESS
 *    0x0003: CHANGE-REQUEST
 *    0x0004: SOURCE-ADDRESS
 *    0x0005: CHANGED-ADDRESS
 *    0x0006: USERNAME
 *    0x0007: PASSWORD
 *    0x0008: MESSAGE-INTEGRITY
 *    0x0009: ERROR-CODE
 *    0x000a: UNKNOWN-ATTRIBUTES
 *    0x000b: REFLECTED-FROM
 *    0x0014: REALM
 *    0x0015: NONCE
 *    0x0020: XOR-MAPPED-ADDRESS
 *    0x8022: SOFTWARE
 *    0x8023: ALTERNATE-SERVER
 *    0x8028: FINGERPRINT
 *
 * TURN attributes:
 *    0x000C: CHANNEL-NUMBER
 *    0x000D: LIFETIME
 *    0x0012: XOR-PEER-ADDRESS
 *    0x0013: DATA
 *    0x0016: XOR-RELAYED-ADDRESS
 *    0x0018: EVEN-PORT
 *    0x0019: REQUESTED-TRANSPORT
 *    0x001A: DONT-FRAGMENT
 *    0x0022: RESERVATION-TOKEN
 *
 * ICE attributes:
 *    0x0024: PRIORITY
 *    0x0025: USE-CANDIDATE
 *    0x8029: ICE-CONTROLLED
 *    0x802A: ICE-CONTROLLING
 * </pre>
 *
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Namal Senarathne
 * @author Aakash Garg
 */
public abstract class Attribute {

    public enum Type {
        /* STUN attributes */
        MAPPED_ADDRESS(0x0001), // Mapped address attribute
        RESPONSE_ADDRESS(0x0002), // Response address attribute
        CHANGE_REQUEST(0x0003), // Change request attribute
        SOURCE_ADDRESS(0x0004), // Source address attribute
        CHANGED_ADDRESS(0x0005), // Changed address attribute
        USERNAME(0x0006), // Username attribute
        PASSWORD(0x0007), // Password attribute
        MESSAGE_INTEGRITY(0x0008), // Message integrity attribute
        ERROR_CODE(0x0009), // Error code attribute
        UNKNOWN_ATTRIBUTES(0x000a), // Unknown attributes attribute
        REFLECTED_FROM(0x000b), // Reflected from attribute
        REALM(0x0014), // Realm attribute
        NONCE(0x0015), // Nonce attribute
        XOR_MAPPED_ADDRESS(0x0020), // XOR Mapped address attribute
        XOR_ONLY(0x0021), // XOR only attribute
        SOFTWARE(0x8022), // Software attribute
        ALTERNATE_SERVER(0x8023), // Alternate server attribute
        FINGERPRINT(0x8028), // Fingerprint attribute
        UNKNOWN_OPTIONAL_ATTRIBUTE(0x8000), // Unknown optional attribute
        /* TURN attributes */
        CHANNEL_NUMBER(0x000c), // Channel number attribute
        LIFETIME(0x000d), // Lifetime attribute
        XOR_PEER_ADDRESS(0x0012), // XOR peer address attribute
        DATA(0x0013), // Data attribute
        XOR_RELAYED_ADDRESS(0x0016), // XOR relayed address attribute
        REQUESTED_ADDRESS_FAMILY(0X0017), // Requested Address Family attribute
        EVEN_PORT(0x0018), // Even port attribute
        REQUESTED_TRANSPORT(0x0019), // Requested transport attribute
        DONT_FRAGMENT(0x001a), // Don't fragment attribute
        RESERVATION_TOKEN(0x0022), // Reservation token attribute
        CONNECTION_ID(0x002a), // Connection Id attribute, support attribute
        /* Old TURN attributes */
        MAGIC_COOKIE(0x000f), // Magic cookie attribute
        DESTINATION_ADDRESS(0x0011), // Destination address attribute
        REMOTE_ADDRESS(0x0012), // Destination address attribute
        /* ICE attributes */
        PRIORITY(0x0024), // Priority attribute
        USE_CANDIDATE(0x0025), // Use candidate attribute
        ICE_CONTROLLED(0x8029), // ICE controlled attribute
        ICE_CONTROLLING(0x802a); // ICE controlling attribute

        int type;

        Type(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public static Type valueOf(int type) {
            for (Type t : values()) {
                if (t.type == type) {
                    return t;
                }
            }
            // default is unknown optional
            return UNKNOWN_OPTIONAL_ATTRIBUTE;
        }
    }

    /**
     * The type of the attribute.
     */
    protected final Type attributeType;

    /**
     * The size of an attribute header in bytes = len(TYPE) + len(LENGTH) = 4
     */
    public static final char HEADER_LENGTH = 4;

    /**
     * For attributes that arrive in incoming messages, this field
     * contains their original location in the binary array so that we could
     * later more easily verify attributes like MESSAGE-INTEGRITY.
     */
    private int locationInMessage = -1;

    /**
     * Creates an empty STUN message attribute.
     *
     * @param attributeType the type of the attribute.
     */
    protected Attribute(int attributeType) {
        this.attributeType = Type.valueOf(attributeType);
    }

    /**
     * Creates an empty STUN message attribute.
     *
     * @param attributeType the type of the attribute.
     */
    protected Attribute(Type attributeType) {
        this.attributeType = attributeType;
    }

    /**
     * Returns the length of this attribute's body.
     *
     * @return the length of this attribute's value.
     */
    public abstract int getDataLength();

    /**
     * Returns the human readable name of this attribute. Attribute names do
     * not really matter from the protocol point of view. They are only used
     * for debugging and readability.
     *
     * @return this attribute's name.
     */
    public final String getName() {
        return attributeType.name();
    }

    /**
     * Returns the attribute's type.
     *
     * @return the type of this attribute.
     */
    public Type getAttributeType() {
        return attributeType;
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when
     * they have the same type length and value.
     *
     * @param obj the object to compare this attribute with.
     *
     * @return true if the attributes are equal and false otherwise.
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Returns a binary representation of this attribute.
     *
     * @return a binary representation of this attribute.
     */
    public abstract byte[] encode();

    /**
     * For attributes that have arriving in incoming messages, this method
     * stores their original location in the binary array so that we could
     * later more easily verify attributes like MESSAGE-INTEGRITY.
     *
     * @param index the original location of this attribute in the datagram
     * we got off the wire
     */
    public void setLocationInMessage(int index) {
        this.locationInMessage = index;
    }

    /**
     * For attributes that have arriving in incoming messages, this method
     * returns their original location in the binary array so that we could
     * later more easily verify attributes like MESSAGE-INTEGRITY.
     *
     * @return the original location of this attribute in the datagram
     * we got off the wire or -1 if this is not an incoming {@link Attribute}
     */
    public int getLocationInMessage() {
        return this.locationInMessage;
    }

    /**
     * Sets this attribute's fields according to attributeValue array.
     *
     * @param attributeValue a binary array containing this attribute's field
     * values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     * offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     *
     * @throws StunException if attrubteValue contains invalid data.
     */
    abstract void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException;

}
