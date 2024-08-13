/* See LICENSE.md for license information */
package com.red5pro.ice.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.AttributeDecoder;
import com.red5pro.ice.attribute.AttributeFactory;
import com.red5pro.ice.attribute.ContentDependentAttribute;
import com.red5pro.ice.attribute.FingerprintAttribute;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.ice.stack.TransactionID;
import com.red5pro.ice.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunException;

/**
 * This class represents a STUN message. Messages are TLV (type-length-value) encoded using big endian (network ordered) binary.  All STUN messages start
 * with a STUN header, followed by a STUN payload.  The payload is a series of STUN attributes, the set of which depends on the message type.  The STUN
 * header contains a STUN message type, transaction ID, and length.
 *
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Aakash Garg
 */
public abstract class Message {

    private static final Logger logger = LoggerFactory.getLogger(Message.class);

    /* general declaration */
    /**
     * STUN request code.
     */
    public static final char STUN_REQUEST = 0x0000;

    /**
     * STUN indication code.
     */
    public static final char STUN_INDICATION = 0x0010;

    /**
     * STUN success response code.
     */
    public static final char STUN_SUCCESS_RESP = 0x0100;

    /**
     * STUN error response code.
     */
    public static final char STUN_ERROR_RESP = 0x0110;

    /* STUN methods */
    /**
     * STUN binding method.
     */
    public static final char STUN_METHOD_BINDING = 0x0001;

    /**
     * STUN binding request code.
     */
    public static final char BINDING_REQUEST = (STUN_METHOD_BINDING | STUN_REQUEST);

    /**
     * STUN binding success response code.
     */
    public static final char BINDING_SUCCESS_RESPONSE = (STUN_METHOD_BINDING | STUN_SUCCESS_RESP);

    /**
     * STUN binding error response code.
     */
    public static final char BINDING_ERROR_RESPONSE = (STUN_METHOD_BINDING | STUN_ERROR_RESP);

    /**
     * STUN binding request code.
     */
    public static final char BINDING_INDICATION = (STUN_METHOD_BINDING | STUN_INDICATION);

    /**
     * STUN shared secret request.
     */
    public static final char SHARED_SECRET_REQUEST = 0x0002;

    /**
     * STUN shared secret response.
     */
    public static final char SHARED_SECRET_RESPONSE = 0x0102;

    /**
     * STUN shared secret error response.
     */
    public static final char SHARED_SECRET_ERROR_RESPONSE = 0x0112;

    /* TURN methods */
    /**
     * TURN allocate method code.
     */
    public static final char TURN_METHOD_ALLOCATE = 0x0003;

    /**
     * TURN refresh method code.
     */
    public static final char TURN_METHOD_REFRESH = 0x0004;

    /**
     * TURN send method code.
     */
    public static final char TURN_METHOD_SEND = 0x0006;

    /**
     * TURN data method code.
     */
    public static final char TURN_METHOD_DATA = 0x0007;

    /**
     * TURN CreatePermission method code.
     */
    public static final char TURN_METHOD_CREATEPERMISSION = 0x0008;

    /**
     * TURN ChannelBind method code.
     */
    public static final char TURN_METHOD_CHANNELBIND = 0x0009;

    /**
     * TURN Connect method code.
     */
    public static final char TURN_METHOD_CONNECT = 0X000a;

    /**
     * TURN ConnectionBind method code.
     */
    public static final char TURN_METHOD_CONNECTION_BIND = 0X000b;

    /**
     * TURN ConnectionAttempt method code.
     */
    public static final char TURN_METHOD_CONNECTION_ATTEMPT = 0X000c;

    /**
     * TURN allocate request code.
     */
    public static final char ALLOCATE_REQUEST = (TURN_METHOD_ALLOCATE | STUN_REQUEST);

    /**
     * TURN allocate response code.
     */
    public static final char ALLOCATE_RESPONSE = (TURN_METHOD_ALLOCATE | STUN_SUCCESS_RESP);

    /**
     * TURN allocate error response code.
     */
    public static final char ALLOCATE_ERROR_RESPONSE = (TURN_METHOD_ALLOCATE | STUN_ERROR_RESP);

    /**
     * TURN refresh request code.
     */
    public static final char REFRESH_REQUEST = (TURN_METHOD_REFRESH | STUN_REQUEST);

    /**
     * TURN allocate refresh request code.
     */
    public static final char ALLOCATE_REFRESH_REQUEST = (TURN_METHOD_ALLOCATE | REFRESH_REQUEST);

    /**
     * TURN refresh response code.
     */
    public static final char REFRESH_RESPONSE = (TURN_METHOD_REFRESH | STUN_SUCCESS_RESP);

    /**
     * TURN refresh error response code.
     */
    public static final char REFRESH_ERROR_RESPONSE = (TURN_METHOD_REFRESH | STUN_ERROR_RESP);

    /**
     * TURN ChannelBind request code.
     */
    public static final char CHANNELBIND_REQUEST = (TURN_METHOD_CHANNELBIND | STUN_REQUEST);

    /**
     * TURN ChannelBind response code.
     */
    public static final char CHANNELBIND_RESPONSE = (TURN_METHOD_CHANNELBIND | STUN_SUCCESS_RESP);

    /**
     * TURN ChannelBind error response code.
     */
    public static final char CHANNELBIND_ERROR_RESPONSE = (TURN_METHOD_CHANNELBIND | STUN_ERROR_RESP);

    /**
     * TURN CreatePermission request code.
     */
    public static final char CREATEPERMISSION_REQUEST = (TURN_METHOD_CREATEPERMISSION | STUN_REQUEST);

    /**
     * TURN CreatePermission response code.
     */
    public static final char CREATEPERMISSION_RESPONSE = (TURN_METHOD_CREATEPERMISSION | STUN_SUCCESS_RESP);

    /**
     * TURN CreatePermission error response code.
     */
    public static final char CREATEPERMISSION_ERROR_RESPONSE = (TURN_METHOD_CREATEPERMISSION | STUN_ERROR_RESP);

    /**
     * TURN send indication code.
     */
    public static final char SEND_INDICATION = (TURN_METHOD_SEND | STUN_INDICATION);

    /**
     * TURN data indication code.
     */
    public static final char DATA_INDICATION = (TURN_METHOD_DATA | STUN_INDICATION);

    /**
     * TURN Connect Request code.
     */
    public static final char CONNECT_REQUEST = (TURN_METHOD_CONNECT | STUN_REQUEST);

    /**
     * TURN Connect Success Response code.
     */
    public static final char CONNECT_RESPONSE = (TURN_METHOD_CONNECT | STUN_SUCCESS_RESP);

    /**
     * TURN Connect Error Response code.
     */
    public static final char CONNECT_ERROR_RESPONSE = (TURN_METHOD_CONNECT | STUN_ERROR_RESP);

    /**
     * TURN Connection Bind Request code.
     */
    public static final char CONNECTION_BIND_REQUEST = (TURN_METHOD_CONNECTION_BIND | STUN_REQUEST);

    /**
     * TURN Connection Bind Success Response code.
     */
    public static final char CONNECTION_BIND_SUCCESS_RESPONSE = (TURN_METHOD_CONNECTION_BIND | STUN_SUCCESS_RESP);

    /**
     * TURN Connection Bind error code.
     */
    public static final char CONNECTION_BIND_ERROR_RESPONSE = (TURN_METHOD_CONNECTION_BIND | STUN_ERROR_RESP);

    /**
     * TURN Connection Attempt Indication code.
     */
    public static final char CONNECTION_ATTEMPT_INDICATION = (TURN_METHOD_CONNECTION_ATTEMPT | STUN_INDICATION);

    /* Old TURN method */
    /**
     * TURN Send request.
     */
    public static final char SEND_REQUEST = 0x0004;

    /**
     * TURN Send request.
     */
    public static final char OLD_DATA_INDICATION = 0x0115;

    //Message fields
    /**
     * The length of Stun Message Headers in bytes = len(Type) + len(DataLength) + len(Transaction ID).
     */
    public static final byte HEADER_LENGTH = 20;

    /**
     * Indicates the type of the message. The message type can be Binding Request,
     * Binding Response, Binding Error Response, Shared Secret Request, Shared
     * Secret Response, or Shared Secret Error Response.
     */
    protected char messageType = 0x0000;

    /**
     * The transaction ID is used to correlate requests and responses.
     */
    protected byte[] transactionID;

    /**
     * The magic cookie (0x2112A442).
     */
    public static final byte[] MAGIC_COOKIE = { 0x21, 0x12, (byte) 0xA4, 0x42 };

    /**
     * The list of attributes contained by the message. Order is important
     * so we'll be using a ConcurrentLinkedDeque
     */
    protected ConcurrentLinkedDeque<Attribute> attributes = new ConcurrentLinkedDeque<>();

    /**
     * Describes which attributes are present in which messages.  An
     * M indicates that inclusion of the attribute in the message is
     * mandatory, O means its optional, C means it's conditional based on
     * some other aspect of the message, and N/A means that the attribute is
     * not applicable to that message type.
     *
     * For classic STUN :
     *
     * <pre>
     *                                         Binding  Shared  Shared  Shared
     *                       Binding  Binding  Error    Secret  Secret  Secret
     *   Att.                Req.     Resp.    Resp.    Req.    Resp.   Error
     *                                                                  Resp.
     *   _____________________________________________________________________
     *   MAPPED-ADDRESS      N/A      M        N/A      N/A     N/A     N/A
     *   RESPONSE-ADDRESS    O        N/A      N/A      N/A     N/A     N/A
     *   CHANGE-REQUEST      O        N/A      N/A      N/A     N/A     N/A
     *   SOURCE-ADDRESS      N/A      M        N/A      N/A     N/A     N/A
     *   CHANGED-ADDRESS     N/A      M        N/A      N/A     N/A     N/A
     *   USERNAME            O        N/A      N/A      N/A     M       N/A
     *   PASSWORD            N/A      N/A      N/A      N/A     M       N/A
     *   MESSAGE-INTEGRITY   O        O        N/A      N/A     N/A     N/A
     *   ERROR-CODE          N/A      N/A      M        N/A     N/A     M
     *   UNKNOWN-ATTRIBUTES  N/A      N/A      C        N/A     N/A     C
     *   REFLECTED-FROM      N/A      C        N/A      N/A     N/A     N/A
     *   XOR-MAPPED-ADDRESS  N/A      M        N/A      N/A     N/A     N/A
     *   XOR-ONLY            O        N/A      N/A      N/A     N/A     N/A
     *   SOFTWARE            N/A      O        O        N/A     O       O
     * </pre>
     */
    public static final byte N_A = 0;

    /**
     * C means it's conditional based on some other aspect of the message.
     */
    public static final byte C = 1;

    /**
     * O means the parameter is optional.
     *
     * @see Message#N_A
     */
    public static final byte O = 2;

    /**
     * M indicates that inclusion of the attribute in the message is mandatory.
     *
     * @see Message#N_A
     */
    public static final byte M = 3;

    //Message indices
    protected static final byte BINDING_REQUEST_PRESENTITY_INDEX = 0;

    protected static final byte BINDING_RESPONSE_PRESENTITY_INDEX = 1;

    protected static final byte BINDING_ERROR_RESPONSE_PRESENTITY_INDEX = 2;

    protected static final byte SHARED_SECRET_REQUEST_PRESENTITY_INDEX = 3;

    protected static final byte SHARED_SECRET_RESPONSE_PRESENTITY_INDEX = 4;

    protected static final byte SHARED_SECRET_ERROR_RESPONSE_PRESENTITY_INDEX = 5;

    protected static final byte ALLOCATE_REQUEST_PRESENTITY_INDEX = 6;

    protected static final byte ALLOCATE_RESPONSE_PRESENTITY_INDEX = 7;

    protected static final byte REFRESH_REQUEST_PRESENTITY_INDEX = 8;

    protected static final byte REFRESH_RESPONSE_PRESENTITY_INDEX = 9;

    protected static final byte CHANNELBIND_REQUEST_PRESENTITY_INDEX = 10;

    protected static final byte CHANNELBIND_RESPONSE_PRESENTITY_INDEX = 11;

    protected static final byte SEND_INDICATION_PRESENTITY_INDEX = 12;

    protected static final byte DATA_INDICATION_PRESENTITY_INDEX = 13;

    //Attribute indices
    protected static final byte MAPPED_ADDRESS_PRESENTITY_INDEX = 0;

    protected static final byte RESPONSE_ADDRESS_PRESENTITY_INDEX = 1;

    protected static final byte CHANGE_REQUEST_PRESENTITY_INDEX = 2;

    protected static final byte SOURCE_ADDRESS_PRESENTITY_INDEX = 3;

    protected static final byte CHANGED_ADDRESS_PRESENTITY_INDEX = 4;

    protected static final byte USERNAME_PRESENTITY_INDEX = 5;

    protected static final byte PASSWORD_PRESENTITY_INDEX = 6;

    protected static final byte MESSAGE_INTEGRITY_PRESENTITY_INDEX = 7;

    protected static final byte ERROR_CODE_PRESENTITY_INDEX = 8;

    protected static final byte UNKNOWN_ATTRIBUTES_PRESENTITY_INDEX = 9;

    protected static final byte REFLECTED_FROM_PRESENTITY_INDEX = 10;

    protected static final byte XOR_MAPPED_ADDRESS_PRESENTITY_INDEX = 11;

    protected static final byte XOR_ONLY_PRESENTITY_INDEX = 12;

    protected static final byte SOFTWARE_PRESENTITY_INDEX = 13;

    protected static final byte UNKNOWN_OPTIONAL_ATTRIBUTES_PRESENTITY_INDEX = 14;

    protected static final byte ALTERNATE_SERVER_PRESENTITY_INDEX = 15;

    protected static final byte REALM_PRESENTITY_INDEX = 16;

    protected static final byte NONCE_PRESENTITY_INDEX = 17;

    protected static final byte FINGERPRINT_PRESENTITY_INDEX = 18;

    /* TURN attributes */
    protected static final byte CHANNEL_NUMBER_PRESENTITY_INDEX = 19;

    protected static final byte LIFETIME_PRESENTITY_INDEX = 20;

    protected static final byte XOR_PEER_ADDRESS_PRESENTITY_INDEX = 21;

    protected static final byte DATA_PRESENTITY_INDEX = 22;

    protected static final byte XOR_RELAYED_ADDRESS_PRESENTITY_INDEX = 23;

    protected static final byte EVEN_PORT_PRESENTITY_INDEX = 24;

    protected static final byte REQUESTED_TRANSPORT_PRESENTITY_INDEX = 25;

    protected static final byte DONT_FRAGMENT_PRESENTITY_INDEX = 26;

    protected static final byte RESERVATION_TOKEN_PRESENTITY_INDEX = 27;

    /* ICE attributes */
    protected static final byte PRIORITY_PRESENTITY_INDEX = 28;

    protected static final byte ICE_CONTROLLING_PRESENTITY_INDEX = 29;

    protected static final byte ICE_CONTROLLED_PRESENTITY_INDEX = 30;

    protected static final byte USE_CANDIDATE_PRESENTITY_INDEX = 31;

    /* Old TURN attributes */
    protected static final byte DESTINATION_ADDRESS_PRESENTITY_INDEX = 29;

    protected final static byte attributePresentities[][] = new byte[][] {
            //                                            Binding   Shared   Shared   Shared  Alloc   Alloc   Rfrsh   Rfrsh   ChnlBnd  ChnlBnd Send    Data
            //                        Binding   Binding   Error     Secret   Secret   Secret  Req.    Resp.   Req.    Resp.   Req.     Resp.   Indic.  Indic.
            //  Att.                  Req.      Resp.     Resp.     Req.     Resp.    Error
            //                                                                        Resp.
            //  ____________________________________________________________________________________________________________________________________________
            /* MAPPED-ADDRESS */{ N_A, M, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* RESPONSE-ADDRESS */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* CHANGE-REQUEST */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* SOURCE-ADDRESS */{ N_A, M, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, M },
            /* CHANGED-ADDRESS */{ N_A, M, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* USERNAME */{ O, N_A, N_A, N_A, M, N_A, O, N_A, O, N_A, O, N_A, N_A, N_A },
            /* PASSWORD */{ N_A, N_A, N_A, N_A, M, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* MESSAGE-INTEGRITY */{ O, O, N_A, N_A, N_A, N_A, O, O, O, O, O, O, N_A, N_A },
            /* ERROR-CODE */{ N_A, N_A, M, N_A, N_A, M, N_A, M, N_A, M, N_A, M, N_A, N_A },
            /* UNKNOWN-ATTRIBUTES */{ N_A, N_A, C, N_A, N_A, C, N_A, C, N_A, C, N_A, C, N_A, N_A },
            /* REFLECTED-FROM */{ N_A, C, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* XOR-MAPPED-ADDRESS */{ N_A, C, N_A, N_A, N_A, N_A, N_A, M, N_A, N_A, N_A, N_A, N_A, N_A },
            /* XOR-ONLY */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* SOFTWARE */{ N_A, O, O, N_A, O, O, O, O, O, O, O, O, O, N_A },
            /* UNKNOWN_OPTIONAL */{ O, O, O, O, O, O, O, O, O, O, O, O, N_A, N_A },
            /* ALTERNATE_SERVER */{ O, O, O, O, O, O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* REALM */{ O, N_A, N_A, N_A, M, N_A, O, O, O, O, O, O, N_A, N_A },
            /* NONCE */{ O, N_A, N_A, N_A, M, N_A, O, O, O, O, O, O, N_A, N_A },
            /* FINGERPRINT */{ O, O, O, O, O, O, O, O, O, O, O, O, N_A, N_A },
            /* CHANNEL-NUMBER */{ N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, M, N_A, N_A, N_A },
            /* LIFETIME */{ N_A, N_A, N_A, N_A, N_A, N_A, O, N_A, O, N_A, N_A, N_A, N_A, N_A },
            /* XOR-PEER-ADDRESS */{ N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, M, N_A, M, M },
            /* DATA */{ N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, O, N_A, N_A, N_A, O, M },
            /* XOR-RELAYED-ADDRESS */{ N_A, N_A, N_A, N_A, N_A, N_A, N_A, M, N_A, N_A, N_A, N_A, N_A, N_A },
            /* EVEN-PORT */{ N_A, N_A, N_A, N_A, N_A, N_A, O, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* REQUESTED-TRANSPORT */{ N_A, N_A, N_A, N_A, N_A, N_A, M, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* DONT-FRAGMENT */{ N_A, N_A, N_A, N_A, N_A, N_A, O, N_A, N_A, N_A, N_A, N_A, O, N_A },
            /* RESERVATION-TOKEN */{ N_A, N_A, N_A, N_A, N_A, N_A, O, O, N_A, N_A, N_A, N_A, N_A, N_A },
            /* PRIORITY */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* ICE-CONTROLLING */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* ICE-CONTROLLED */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* USE-CANDIDATE */{ O, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A },
            /* DESTINATION-ADDRESS */{ N_A, N_A, N_A, N_A, N_A, N_A, N_A, N_A, O, N_A, N_A, N_A, M, N_A }, };

    /**
     * Creates an empty STUN Message.
     */
    protected Message() {
    }

    /**
     * Returns the length of this message's body.
     * @return the length of the data in this message.
     */
    public char getDataLength() {
        char length = 0;
        List<Attribute> attrs = getAttributes();
        for (Attribute att : attrs) {
            int attLen = att.getDataLength() + Attribute.HEADER_LENGTH;
            //take attribute padding into account:
            attLen += (4 - (attLen % 4)) % 4;
            length += attLen;
        }
        return length;
    }

    /**
     * Returns the length of this message's body without padding.
     * Some STUN/ICE dialect does not take into account padding (GTalk).
     *
     * @return the length of the data in this message.
     */
    public char getDataLengthWithoutPadding() {
        char length = 0;
        List<Attribute> attrs = getAttributes();
        for (Attribute att : attrs) {
            int attLen = att.getDataLength() + Attribute.HEADER_LENGTH;
            length += attLen;
        }
        return length;
    }

    /**
     * Puts the specified attribute into this message. If an attribute with that name was already added, it would be replaced.
     *
     * @param attribute the attribute to put into this message
     * @throws IllegalArgumentException if the message cannot contain such an attribute
     */
    public void putAttribute(Attribute attribute) throws IllegalArgumentException {
        attributes.add(attribute);
    }

    /**
     * Returns the attribute with the specified type or null if no such attribute exists.
     *
     * @param attributeType the type of the attribute
     * @return the attribute with the specified type or null if no such attribute exists
     */
    public Attribute getAttribute(Attribute.Type attributeType) {
        for (Attribute attr : attributes) {
            if (attr.getAttributeType() == attributeType) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Returns a copy of all {@link Attribute}s in this {@link Message}.
     *
     * @return a copy of all {@link Attribute}s in this {@link Message}.
     */
    public List<Attribute> getAttributes() {
        return Collections.unmodifiableList(Arrays.asList(attributes.toArray(new Attribute[0])));
    }

    /**
     * Removes the specified attribute.
     *
     * @param attributeType the attribute to remove
     * @return the Attribute we've just removed
     */
    public Attribute removeAttribute(Attribute.Type attributeType) {
        for (Attribute attr : attributes) {
            if (attr.getAttributeType() == attributeType) {
                attributes.remove(attr);
                return attr;
            }
        }
        return null;
    }

    /**
     * Returns whether or not any of the given attributes exist in the message.
     *
     * @param attrs Set of Attribute.Type to search for
     * @return true if any attribute types match and false otherwise
     */
    public boolean containsAnyAttributes(EnumSet<Attribute.Type> attrs) {
        for (Attribute attr : attributes) {
            if (attrs.contains(attr.getAttributeType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether or not all of the given attributes exist in the message.
     *
     * @param attrs Set of Attribute.Type to search for
     * @return true if all attribute types match and false otherwise
     */
    public boolean containsAllAttributes(EnumSet<Attribute.Type> attrs) {
        List<Attribute.Type> found = new ArrayList<>();
        for (Attribute attr : attributes) {
            Attribute.Type type = attr.getAttributeType();
            if (attrs.contains(type)) {
                found.add(type);
            }
        }
        if (!found.isEmpty()) {
            return EnumSet.copyOf(found).containsAll(attrs);
        }
        return false;
    }

    /**
     * Returns whether or not none of the given attributes exist in the message.
     *
     * @param attrs Set of Attribute.Type to search for
     * @return true if none attribute types match and false otherwise
     */
    public boolean containsNoneAttributes(EnumSet<Attribute.Type> attrs) {
        boolean contains = true;
        for (Attribute attr : attributes) {
            if (attrs.contains(attr.getAttributeType())) {
                contains = false;
            }
        }
        return contains;
    }

    /**
     * Returns the number of attributes, currently contained by the message.
     *
     * @return the number of attributes, currently contained by the message.
     */
    public int getAttributeCount() {
        return attributes.size();
    }

    /**
     * Sets this message's type to be messageType. Method is package access as it should not permit changing the type of message once it has been
     * initialized (could provoke attribute discrepancies). Called by messageFactory.
     *
     * @param messageType the message type.
     */
    protected void setMessageType(char messageType) {
        this.messageType = messageType;
    }

    /**
     * The message type of this message.
     *
     * @return the message type of the message.
     */
    public char getMessageType() {
        return messageType;
    }

    /**
     * Copies the specified tranID and sets it as this message's transactionID.
     *
     * @param tranID the transaction id to set in this message
     * @throws StunException ILLEGAL_ARGUMENT if the transaction id is not valid
     */
    public void setTransactionID(byte[] tranID) throws StunException {
        if (tranID == null || (tranID.length != TransactionID.RFC5389_TRANSACTION_ID_LENGTH
                && tranID.length != TransactionID.RFC3489_TRANSACTION_ID_LENGTH)) {
            throw new StunException(StunException.ILLEGAL_ARGUMENT, "Invalid transaction id length");
        }
        int tranIDLength = tranID.length;
        transactionID = new byte[tranIDLength];
        System.arraycopy(tranID, 0, transactionID, 0, tranIDLength);
    }

    /**
     * Returns a reference to this message's transaction id.
     *
     * @return a reference to this message's transaction id.
     */
    public byte[] getTransactionID() {
        return transactionID;
    }

    /**
     * Returns the human readable name of this message. Message names do
     * not really matter from the protocol point of view. They are only used
     * for debugging and readability.
     *
     * @return this message's name
     */
    public String getName() {
        switch (messageType) {
            case ALLOCATE_REQUEST:
                return "ALLOCATE-REQUEST";
            case ALLOCATE_RESPONSE:
                return "ALLOCATE-RESPONSE";
            case ALLOCATE_ERROR_RESPONSE:
                return "ALLOCATE-ERROR-RESPONSE";
            case BINDING_REQUEST:
                return "BINDING-REQUEST";
            case BINDING_SUCCESS_RESPONSE:
                return "BINDING-RESPONSE";
            case BINDING_ERROR_RESPONSE:
                return "BINDING-ERROR-RESPONSE";
            case CREATEPERMISSION_REQUEST:
                return "CREATE-PERMISSION-REQUEST";
            case CREATEPERMISSION_RESPONSE:
                return "CREATE-PERMISSION-RESPONSE";
            case CREATEPERMISSION_ERROR_RESPONSE:
                return "CREATE-PERMISSION-ERROR-RESPONSE";
            case DATA_INDICATION:
                return "DATA-INDICATION";
            case REFRESH_REQUEST:
                return "REFRESH-REQUEST";
            case REFRESH_RESPONSE:
                return "REFRESH-RESPONSE";
            case REFRESH_ERROR_RESPONSE:
                return "REFRESH-ERROR-RESPONSE";
            case SEND_INDICATION:
                return "SEND-INDICATION";
            case SHARED_SECRET_REQUEST:
                return "SHARED-SECRET-REQUEST";
            case SHARED_SECRET_RESPONSE:
                return "SHARED-SECRET-RESPONSE";
            case SHARED_SECRET_ERROR_RESPONSE:
                return "SHARED-SECRET-ERROR-RESPONSE";
            case CHANNELBIND_REQUEST:
                return "CHANNELBIND-REQUEST";
            case CHANNELBIND_RESPONSE:
                return "CHANNELBIND-RESPONSE";
            case CHANNELBIND_ERROR_RESPONSE:
                return "CHANNELBIND_ERROR_RESPONSE";
            default:
                return "UNKNOWN-MESSAGE";
        }
    }

    /**
     * Compares two STUN Messages. Messages are considered equal when their type, length, and all their attributes are equal.
     *
     * @param obj the object to compare this message with
     *
     * @return true if the messages are equal and false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Message))
            return false;

        if (obj == this)
            return true;

        Message msg = (Message) obj;
        if (msg.getMessageType() != getMessageType())
            return false;
        if (msg.getDataLength() != getDataLength())
            return false;

        //compare attributes
        for (Attribute localAtt : attributes) {
            if (!localAtt.equals(msg.getAttribute(localAtt.getAttributeType())))
                return false;
        }

        return true;
    }

    /**
     * Returns a binary representation of this message.
     *
     * @param stunStack the StunStack in the context of which the request to encode this Message is being made
     * @return a binary representation of this message
     * @throws IllegalStateException if the message does not have all required attributes
     */
    public byte[] encode(StunStack stunStack) throws IllegalStateException {
        prepareForEncoding();
        final char dataLength;
        dataLength = getDataLength();
        byte binMsg[] = new byte[HEADER_LENGTH + dataLength];
        int offset = 0;
        // STUN Message Type
        binMsg[offset++] = (byte) (getMessageType() >> 8);
        binMsg[offset++] = (byte) (getMessageType() & 0xFF);
        // Message Length
        final int messageLengthOffset = offset;
        offset += 2;
        byte tranID[] = getTransactionID();
        if (tranID.length == TransactionID.RFC5389_TRANSACTION_ID_LENGTH) {
            System.arraycopy(MAGIC_COOKIE, 0, binMsg, offset, 4);
            offset += 4;
            System.arraycopy(tranID, 0, binMsg, offset, TransactionID.RFC5389_TRANSACTION_ID_LENGTH);
            offset += TransactionID.RFC5389_TRANSACTION_ID_LENGTH;
        } else {
            // RFC3489 behavior - transaction id is 4 bytes longer
            System.arraycopy(tranID, 0, binMsg, offset, TransactionID.RFC3489_TRANSACTION_ID_LENGTH);
            offset += TransactionID.RFC3489_TRANSACTION_ID_LENGTH;
        }
        char dataLengthForContentDependentAttribute = 0;
        for (Attribute attribute : attributes) {
            int attributeLength = attribute.getDataLength() + Attribute.HEADER_LENGTH;
            //take attribute padding into account:
            attributeLength += (4 - attributeLength % 4) % 4;
            dataLengthForContentDependentAttribute += attributeLength;
            //special handling for message integrity and fingerprint values
            byte[] binAtt;
            if (attribute instanceof ContentDependentAttribute) {
                // "Message Length" seen by a ContentDependentAttribute is up to and including the very Attribute but without any other Attribute instances after it.
                binMsg[messageLengthOffset] = (byte) (dataLengthForContentDependentAttribute >> 8);
                binMsg[messageLengthOffset + 1] = (byte) (dataLengthForContentDependentAttribute & 0xFF);
                binAtt = ((ContentDependentAttribute) attribute).encode(stunStack, binMsg, 0, offset);
            } else {
                binAtt = attribute.encode();
            }
            System.arraycopy(binAtt, 0, binMsg, offset, binAtt.length);
            // Offset by attributeLength and not by binAtt.length because attributeLength takes the attribute padding into account and binAtt.length does not.
            offset += attributeLength;
        }
        // Message Length
        binMsg[messageLengthOffset] = (byte) (dataLength >> 8);
        binMsg[messageLengthOffset + 1] = (byte) (dataLength & 0xFF);
        return binMsg;
    }

    /**
     * Adds attributes that have been requested via configuration properties.
     * Asserts attribute order where necessary.
     */
    private void prepareForEncoding() {
        //remove MESSAGE-INTEGRITY and FINGERPRINT attributes so that we can make sure they are added at the end.
        Attribute msgIntAttr = removeAttribute(Attribute.Type.MESSAGE_INTEGRITY);
        Attribute fingerprint = removeAttribute(Attribute.Type.FINGERPRINT);
        //add a SOFTWARE attribute if the user said so, and unless they did it themselves.
        String software = System.getProperty(StackProperties.SOFTWARE);
        if (getAttribute(Attribute.Type.SOFTWARE) == null && software != null && software.length() > 0) {
            putAttribute(AttributeFactory.createSoftwareAttribute(software.getBytes()));
        }
        //re-add MESSAGE-INTEGRITY if there was one.
        if (msgIntAttr != null) {
            putAttribute(msgIntAttr);
        }
        //add FINGERPRINT if there was one or if user told us to add it everywhere.
        if (fingerprint == null && Boolean.getBoolean(StackProperties.ALWAYS_SIGN)) {
            fingerprint = AttributeFactory.createFingerprintAttribute();
        }
        if (fingerprint != null) {
            putAttribute(fingerprint);
        }
    }

    /**
     * Constructs a message from its binary representation.
     *
     * @param binMessage the binary array that contains the encoded message
     * @param offset the index where the message starts
     * @param arrayLen the length of the message
     * @return a Message object constructed from the binMessage array
     *
     * @throws StunException ILLEGAL_ARGUMENT if one or more of the arguments have invalid values
     */
    public static Message decode(byte[] binMessage, int offset, int arrayLen) throws StunException {
        if (logger.isDebugEnabled()) {
            logger.debug("decode - offset: {} length: {}\n{}", offset, arrayLen,
                    Utils.toHexString(Arrays.copyOfRange(binMessage, offset, (offset + arrayLen))));
        }
        int originalOffset = offset;
        arrayLen = Math.min(binMessage.length, arrayLen);
        if (binMessage == null || arrayLen - offset < Message.HEADER_LENGTH) {
            throw new StunException(StunException.ILLEGAL_ARGUMENT, "The given binary array is not a valid StunMessage");
        }
        char messageType = (char) ((binMessage[offset++] << 8) | (binMessage[offset++] & 0xFF));
        Message message;
        /* 0x0115 is a old TURN DATA indication message type */
        if (Message.isResponseType(messageType) && messageType != OLD_DATA_INDICATION) {
            message = new Response();
        } else if (Message.isRequestType(messageType)) {
            message = new Request();
        } else {
            message = new Indication();
        }
        message.setMessageType(messageType);
        int length = (binMessage[offset++] << 8) | (binMessage[offset++] & 0xFF);
        /* copy the cookie */
        byte cookie[] = new byte[4];
        System.arraycopy(binMessage, offset, cookie, 0, 4);
        offset += 4;
        boolean rfc3489Compat = !Arrays.equals(MAGIC_COOKIE, cookie);
        int transactionIdLength = rfc3489Compat ? TransactionID.RFC3489_TRANSACTION_ID_LENGTH : TransactionID.RFC5389_TRANSACTION_ID_LENGTH;
        if (arrayLen - offset - transactionIdLength < length) {
            throw new StunException(StunException.ILLEGAL_ARGUMENT,
                    "The given binary array does not seem to contain a whole StunMessage: given " + arrayLen + " bytes of "
                            + message.getName() + " but expecting " + (offset + transactionIdLength + length));
        }
        try {
            byte[] tranID = new byte[transactionIdLength];
            if (!rfc3489Compat) {
                System.arraycopy(binMessage, offset, tranID, 0, transactionIdLength);
            } else {
                System.arraycopy(binMessage, 0, tranID, 0, transactionIdLength);
            }
            message.setTransactionID(tranID);
        } catch (StunException exc) {
            throw new StunException(StunException.ILLEGAL_ARGUMENT, "The given binary array does not seem to contain a whole StunMessage",
                    exc);
        }
        // update offset to just beyond transaction id
        offset += transactionIdLength;
        while (offset - Message.HEADER_LENGTH < length) {
            Attribute att = AttributeDecoder.decode(binMessage, offset, (length - offset));
            performAttributeSpecificActions(att, binMessage, originalOffset, offset);
            message.putAttribute(att);
            offset += att.getDataLength() + Attribute.HEADER_LENGTH;
            // now also skip any potential padding that might have come with this attribute.
            if ((att.getDataLength() % 4) > 0) {
                offset += (4 - (att.getDataLength() % 4));
            }
        }
        return message;
    }

    /**
     * Executes actions related specific attributes like asserting proper fingerprint checksum.
     *
     * @param attribute the Attribute we'd like to process
     * @param binMessage the byte array that the message arrived with
     * @param offset the index where data starts in binMessage
     * @param msgLen the number of message bytes in binMessage
     *
     * @throws StunException if there's something in the attribute that
     * caused us to discard the whole message (e.g. an invalid checksum or
     * username)
     */
    private static void performAttributeSpecificActions(Attribute attribute, byte[] binMessage, int offset, int msgLen)
            throws StunException {
        //check finger print CRC
        if (attribute instanceof FingerprintAttribute) {
            if (!validateFingerprint((FingerprintAttribute) attribute, binMessage, offset, msgLen)) {
                //RFC 5389 says that we should ignore bad CRCs rather than reply with an error response.
                throw new StunException("Wrong value in FINGERPRINT");
            }
        }
    }

    /**
     * Recalculates the FINGERPRINT CRC32 checksum of the message
     * array so that we could compare it with the value brought by the
     * {@link FingerprintAttribute}.
     *
     * @param fingerprint the attribute that we need to validate.
     * @param message the message whose CRC32 checksum we'd need to recalculate.
     * @param offset the index in message where data starts.
     * @param length the number of bytes in message that the CRC32
     * would need to be calculated over.
     *
     * @return true if FINGERPRINT contains a valid CRC32
     * value and false otherwise.
     */
    private static boolean validateFingerprint(FingerprintAttribute fingerprint, byte[] message, int offset, int length) {
        byte[] incomingCrcBytes = fingerprint.getChecksum();
        //now check whether the CRC really is what it's supposed to be.
        //re calculate the check sum
        byte[] realCrcBytes = FingerprintAttribute.calculateXorCRC32(message, offset, length);
        //CRC validation.
        if (!Arrays.equals(incomingCrcBytes, realCrcBytes)) {
            if (logger.isDebugEnabled()) {
                logger.debug("An incoming message arrived with a wrong FINGERPRINT attribute value. CRC Was:"
                        + Arrays.toString(incomingCrcBytes) + ". Should have been:" + Arrays.toString(realCrcBytes) + ". Will ignore.");
            }
            return false;
        }
        return true;
    }

    /**
     * Determines if the message type is a Error Response.
     * @param type type to test
     * @return true if the type is Error Response, false otherwise
     */
    public static boolean isErrorResponseType(char type) {
        return ((type & 0x0110) == STUN_ERROR_RESP);
    }

    /**
     * Determines if the message type is a Success Response.
     * @param type type to test
     * @return true if the type is Success Response, false otherwise
     */
    public static boolean isSuccessResponseType(char type) {
        return ((type & 0x0110) == STUN_SUCCESS_RESP);
    }

    /**
     * Determines whether type could be the type of a STUN Response (as opposed
     * to STUN Request).
     * @param type the type to test.
     * @return true if type is a valid response type.
     */
    public static boolean isResponseType(char type) {
        return (isSuccessResponseType(type) || isErrorResponseType(type));
    }

    /**
     * Determines if the message type is Indication.
     * @param type type to test
     * @return true if the type is Indication, false otherwise
     */
    public static boolean isIndicationType(char type) {
        return ((type & 0x0110) == STUN_INDICATION);
    }

    /**
     * Determines whether type could be the type of a STUN Request (as opposed
     * to STUN Response).
     * @param type the type to test.
     * @return true if type is a valid request type.
     */
    public static boolean isRequestType(char type) {
        return ((type & 0x0110) == STUN_REQUEST);
    }

    /**
     * Returns a String representation of this message.
     *
     * @return  a String representation of this message.
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getName());
        stringBuilder.append("(0x");
        stringBuilder.append(Integer.toHexString(getMessageType()));
        stringBuilder.append(")[attrib.count=");
        stringBuilder.append(getAttributeCount());
        stringBuilder.append(" len=");
        stringBuilder.append((int) this.getDataLength());
        byte[] transactionID = getTransactionID();
        if (transactionID != null) {
            stringBuilder.append(" tranID=");
            stringBuilder.append(TransactionID.toString(transactionID));
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
