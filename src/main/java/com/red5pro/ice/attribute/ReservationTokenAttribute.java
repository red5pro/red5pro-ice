/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import java.util.Arrays;

import com.red5pro.ice.Agent;
import com.red5pro.ice.StunException;

/**
 * The RESERVATION-TOKEN attribute contains a token that identifies a
 * reservation port on a TURN server. The value is on 64 bits (8 bytes).
 *
 * @author Sebastien Vincent
 * @author Aakash Garg
 */
public class ReservationTokenAttribute extends Attribute {

    /**
     * ReservationToken value.
     */
    private byte reservationToken[] = null;

    /**
     * A hashcode for hashtable storage.
     */
    private int hashCode = 0;

    /**
     * Constructor.
     */
    protected ReservationTokenAttribute() {
        super(Attribute.Type.RESERVATION_TOKEN);
        this.reservationToken = new byte[8];
    }

    /**
     * Copies the value of the reservationToken attribute from the specified
     * attributeValue.
     * @param attributeValue a binary array containing this attribute's
     *   field values and NOT containing the attribute header.
     * @param offset the position where attribute values begin (most often
     *   offset is equal to the index of the first byte after length)
     * @param length the length of the binary array.
     * @throws StunException if attributeValue contains invalid reservationToken.
     */
    @Override
    void decodeAttributeBody(byte[] attributeValue, int offset, int length) throws StunException {
        if (length != 8) {
            throw new StunException("Length mismatch!");
        }

        reservationToken = new byte[8];
        System.arraycopy(attributeValue, offset, reservationToken, 0, 8);
    }

    /**
     * Returns a binary representation of this attribute.
     * @return a binary representation of this attribute.
     */
    @Override
    public byte[] encode() {
        byte binValue[] = new byte[HEADER_LENGTH + 8];

        //Type
        binValue[0] = (byte) (attributeType.type >> 8);
        binValue[1] = (byte) (attributeType.type & 0x00FF);

        //Length
        binValue[2] = (byte) (8 >> 8);
        binValue[3] = (byte) (8 & 0x00FF);

        //reservationToken
        System.arraycopy(reservationToken, 0, binValue, 4, 8);

        return binValue;
    }

    /**
     * Returns a (cloned) byte array containing the reservationToken value of
     * the reservationToken attribute.
     * @return the binary array containing the reservationToken.
     */
    public byte[] getReservationToken() {
        if (reservationToken == null)
            return null;

        byte[] copy = new byte[reservationToken.length];
        System.arraycopy(reservationToken, 0, copy, 0, reservationToken.length);
        return copy;
    }

    /**
     * Copies the specified binary array into the the reservationToken value of
     * the reservationToken attribute.
     * @param reservationToken the binary array containing the reservationToken.
     */
    public void setReservationToken(byte[] reservationToken) {
        if (reservationToken == null) {
            this.reservationToken = null;
            return;
        }

        this.reservationToken = new byte[reservationToken.length];
        System.arraycopy(reservationToken, 0, this.reservationToken, 0, reservationToken.length);
    }

    /**
     * Returns the length of this attribute's body.
     * @return the length of this attribute's value.
     */
    @Override
    public int getDataLength() {
        return reservationToken.length;
    }

    /**
     * Creates a Reservation Token object.The Reservation Token itself is
     * generated using the following algorithm:
     *
     * The first 6 bytes of the id are given the value of
     * System.currentTimeMillis(). Putting the right most bits first so
     * that we get a more optimized equals() method.
     *
     * @return A Reservation Token object with a unique token value.
     */
    public static ReservationTokenAttribute createNewReservationTokenAttribute() {
        ReservationTokenAttribute token = new ReservationTokenAttribute();

        generateReservationTokenAttribute(token, 8);
        return token;
    }

    /**
     * Generates a random ReservationTokenAttribute
     *
     * @param token ReservationTokenAttribute
     * @param nb number of bytes to generate
     */
    private static void generateReservationTokenAttribute(ReservationTokenAttribute token, int nb) {
        long left = System.currentTimeMillis(); // the first nb/2 bytes of the token
        long right = Agent.random.nextLong(); // the last nb/2 bytes of the token
        int b = nb / 2;

        for (int i = 0; i < b; i++) {
            token.reservationToken[i] = (byte) ((left >> (i * 8)) & 0xFFL);
            token.reservationToken[i + b] = (byte) ((right >> (i * 8)) & 0xFFL);
        }

        //calculate hashcode for Hashtable storage.
        token.hashCode = (token.reservationToken[3] << 24 & 0xFF000000) | (token.reservationToken[2] << 16 & 0x00FF0000)
                | (token.reservationToken[1] << 8 & 0x0000FF00) | (token.reservationToken[0] & 0x000000FF);
    }

    /**
     * Compares two STUN Attributes. Two attributes are considered equal when
     * they have the same type length and value.
     * @param obj the object to compare this attribute with.
     * @return true if the attributes are equal and false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ReservationTokenAttribute))
            return false;

        if (obj == this)
            return true;

        ReservationTokenAttribute att = (ReservationTokenAttribute) obj;
        if (att.getAttributeType() != attributeType || att.getDataLength() != getDataLength()
                || !Arrays.equals(att.reservationToken, reservationToken))
            return false;

        return true;
    }


    /**
     * Returns a string representation of the token.
     *
     * @return a hex string representing the token.
     */
    @Override
    public String toString() {
        return ReservationTokenAttribute.toString(this.reservationToken);
    }

    /**
     * Returns a string representation of the token.
     *
     * @param reservationToken the Reservation Token to convert into
     *            String.
     *
     * @return a hex string representing the token.
     */
    public static String toString(byte[] reservationToken) {
        StringBuilder idStr = new StringBuilder();

        idStr.append("0x");
        for (int i = 0; i < reservationToken.length; i++) {
            if ((reservationToken[i] & 0xFF) <= 15)
                idStr.append("0");

            idStr.append(Integer.toHexString(reservationToken[i] & 0xFF).toUpperCase());
        }
        return idStr.toString();
    }

    /**
     * Returns the hash code of this Reservation-Token.
     */
    @Override
    public int hashCode() {
        return this.hashCode;
    }

}
