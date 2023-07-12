/* See LICENSE.md for license information */
package com.red5pro.ice.stunclient;

import java.io.IOException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StunException;
import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.ice.nio.IceUdpTransport;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.RequestListener;
import com.red5pro.ice.stack.StunStack;

/**
 * This class implements a programmable STUN server that sends predefined
 * sequences of responses. It may be used to test whether a STUN client
 * behaves correctly in different use cases.
 *
 * @author Emil Ivov
 */
public class ResponseSequenceServer implements RequestListener {

    private static final Logger logger = LoggerFactory.getLogger(ResponseSequenceServer.class);

    /**
     * The sequence of responses to send.
     */
    private Vector<Object> messageSequence = new Vector<>();

    /**
     * The StunStack used by this instance for the purposes of STUN communication.
     */
    private final StunStack stunStack;

    private TransportAddress serverAddress;

    private IceSocketWrapper localSocket;

    /**
     * Initializes a new ResponseSequenceServer instance with a specific StunStack to be used for the purposes of STUN communication.
     *
     * @param stunStack the StunStack to be used by the new instance for the purposes of STUN communication
     * @param bindAddress
     */
    public ResponseSequenceServer(StunStack stunStack, TransportAddress bindAddress) {
        this.stunStack = stunStack;
        this.serverAddress = bindAddress;
    }

    /**
     * Initializes the underlying stack.
     * 
     * @throws StunException if something else fails
     * @throws IOException if we fail to bind a local socket.
     */
    public void start() throws IOException, StunException {
        localSocket = IceSocketWrapper.build(serverAddress, null);
        stunStack.addRequestListener(serverAddress, this);
        IceUdpTransport.getInstance(localSocket.getId()).registerStackAndSocket(stunStack, localSocket);
    }

    /**
     * Resets the server (deletes the sequence and stops the stack)
     */
    public void shutDown() {
        messageSequence.removeAllElements();
        localSocket.close();
    }

    /**
     * Adds the specified response to this sequence or marks a pause (i.e. do not respond) if response is null.
     * 
     * @param response the response to add or null to mark a pause
     */
    public void addMessage(Response response) {
        if (response == null) {
            // leave a mark to skip a message
            messageSequence.add(false);
        } else {
            messageSequence.add(response);
        }
    }

    /**
     * Completely ignores the event that is passed and just sends the next message from the sequence - or does nothing if there's something
     * different from a Response on the current position.
     * 
     * @param evt the event being dispatched
     */
    public void processRequest(StunMessageEvent evt) {
        if (messageSequence.isEmpty()) {
            return;
        }
        Object obj = messageSequence.remove(0);
        if (!(obj instanceof Response)) {
            return;
        }
        Response res = (Response) obj;
        try {
            stunStack.sendResponse(evt.getMessage().getTransactionID(), res, serverAddress, evt.getRemoteAddress());
        } catch (Exception ex) {
            logger.warn("Failed to send a response", ex);
        }
    }

    /**
     * Returns a string representation of this Server.
     * 
     * @return the ip address and port where this server is bound
     */
    public String toString() {
        return serverAddress == null ? "null" : serverAddress.toString();
    }

}
