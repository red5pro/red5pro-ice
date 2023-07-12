/* See LICENSE.md for license information */
package com.red5pro.ice.message;

/**
 * A response descendant of the message class. The primary purpose of the Response class is to allow better functional definition of the classes in the
 * stack package.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 */
public class Response extends Message {

    /**
     * Constructor.
     */
    Response() {
    }

    /**
     * Determines whether this instance represents a STUN error response.
     *
     * @return true if this instance represents a STUN error response;
     * otherwise, false
     */
    public boolean isErrorResponse() {
        return isErrorResponseType(getMessageType());
    }

    /**
     * Determines whether this instance represents a STUN success response.
     *
     * @return true if this instance represents a STUN success response; otherwise, false
     */
    public boolean isSuccessResponse() {
        return isSuccessResponseType(getMessageType());
    }

    /**
     * Checks whether responseType is a valid response type and if yes sets it as the type of the current instance.
     * @param responseType the type to set
     * @throws IllegalArgumentException if responseType is not a valid response type
     */
    public void setMessageType(char responseType) throws IllegalArgumentException {
        if (!isResponseType(responseType)) {
            throw new IllegalArgumentException(Integer.toString(responseType) + " is not a valid response type.");
        }
        super.setMessageType(responseType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append("(0x");
        sb.append(Integer.toHexString(getMessageType()));
        sb.append(")[attrib.count=");
        sb.append(getAttributeCount());
        sb.append("]");
        sb.append(" successful=");
        sb.append(isSuccessResponse());
        return sb.toString();
    }
}
