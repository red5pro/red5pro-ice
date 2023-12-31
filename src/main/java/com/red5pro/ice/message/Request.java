/* See LICENSE.md for license information */
package com.red5pro.ice.message;

/**
 * A request descendant of the message class. The primary purpose of the Request class is to allow better functional definition of the classes in the
 * stack package.
 *
 * @author Emil Ivov
 */
public class Request extends Message {

    /**
     * Constructor.
     */
    Request() {
    }

    /**
     * Checks whether requestType is a valid request type and if yes sets it as the type of the current instance.
     * @param requestType the type to set
     * @throws IllegalArgumentException if requestType is not a valid request type
     */
    public void setMessageType(char requestType) throws IllegalArgumentException {
        if (!isRequestType(requestType)) {
            throw new IllegalArgumentException((int) (requestType) + " - is not a valid request type.");
        }
        super.setMessageType(requestType);
    }
}
