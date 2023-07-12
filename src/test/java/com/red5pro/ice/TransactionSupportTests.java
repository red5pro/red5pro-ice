/* See LICENSE.md for license information */
package com.red5pro.ice;

import java.util.Arrays;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.RequestListener;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.ice.stack.TransactionID;

import junit.framework.TestCase;
import test.PortUtil;

/**
 * Test how client and server behave, how they recognize/adopt messages and
 * how they both handle retransmissions (i.e. client transactions should make
 * them and server transactions should hide them)
 *
 * @author Emil Ivov
 */
public class TransactionSupportTests extends TestCase {

    private final static Logger logger = LoggerFactory.getLogger(TransactionSupportTests.class);

    /**
     * The client address we use for this test.
     */
    TransportAddress clientAddress;

    /**
     * The client address we use for this test.
     */
    TransportAddress serverAddress;

    /**
     * The socket the client uses in this test.
     */
    IceSocketWrapper clientSock;

    /**
     * The socket the server uses in this test.
     */
    IceSocketWrapper serverSock;

    /**
     * The StunStack used by this TransactionSupportTests.
     */
    private StunStack stunStack;

    /**
     * The request we send in this test.
     */
    Request bindingRequest;

    /**
     * The response we send in this test.
     */
    Response bindingResponse;

    /**
     * The tool that collects requests.
     */
    PlainRequestCollector requestCollector;

    /**
     * The tool that collects responses.
     */
    PlainResponseCollector responseCollector;

    /**
     * Inits sockets.
     *
     * @throws Exception if something goes bad.
     */
    protected void setUp() throws Exception {
        super.setUp();
        clientAddress = new TransportAddress("127.0.0.1", PortUtil.getPort(), Transport.UDP);
        serverAddress = new TransportAddress("127.0.0.1", PortUtil.getPort(), Transport.UDP);

        stunStack = new StunStack();

        clientSock = IceSocketWrapper.build(clientAddress, null);
        serverSock = IceSocketWrapper.build(serverAddress, null);

        // a non-controlling / passive stun (server) needs to be bound so it can receive
        //IceUdpTransport.getInstance().registerStackAndSocket(stunStack, serverSock);

        stunStack.addSocket(clientSock, clientSock.getRemoteTransportAddress(), false);
        stunStack.addSocket(serverSock, serverSock.getRemoteTransportAddress(), true); // do socket binding

        bindingRequest = MessageFactory.createBindingRequest();
        bindingResponse = MessageFactory.create3489BindingResponse(clientAddress, clientAddress, serverAddress);

        requestCollector = new PlainRequestCollector();
        responseCollector = new PlainResponseCollector();

        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "false");
        System.setProperty(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, "false");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "");
        System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "");

    }

    /**
     * Frees all sockets that we are currently using.
     *
     * @throws Exception if something does not go as planned.
     */
    protected void tearDown() throws Exception {
        stunStack.removeSocket(clientSock.getId(), clientAddress);
        stunStack.removeSocket(serverSock.getId(), serverAddress);

        clientSock.close();
        serverSock.close();

        requestCollector = null;
        responseCollector = null;

        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "false");
        System.setProperty(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, "false");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "");
        System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "");

        //IceUdpTransport.getInstance().stop();
        super.tearDown();
    }

    /**
     * Test that requests are retransmitted if no response is received
     *
     * @throws java.lang.Exception upon any failure
     */
    public void testClientRetransmissions() throws Exception {
        String oldRetransValue = System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        String oldMaxWaitValue = System.getProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);

        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "100");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");

        //prepare to listen
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "true");

        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait for retransmissions
        Thread.sleep(1000);

        //verify
        Vector<StunMessageEvent> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());

        assertTrue("No retransmissions of the request have been received", reqs.size() > 1);
        assertTrue("The binding request has been retransmitted more than it should have!", reqs.size() >= 3);

        //restore the retransmissions prop in case others are counting on
        //defaults.
        if (oldRetransValue != null)
            System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, oldRetransValue);
        else
            System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);

        if (oldMaxWaitValue != null)
            System.getProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, oldRetransValue);
        else
            System.clearProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);
    }

    /**
     * Make sure that retransmissions are not seen by the server user and that
     * it only gets a single request.
     *
     * @throws Exception if anything goes wrong.
     */
    public void testServerRetransmissionHiding() throws Exception {
        String oldRetransValue = System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");
        //prepare to listen
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait for retransmissions
        Thread.sleep(1000);

        //verify
        Vector<StunMessageEvent> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());

        assertTrue("Retransmissions of a binding request were propagated to the server", reqs.size() <= 1);

        //restore the retransmissions prop in case others are counting on
        //defaults.
        if (oldRetransValue != null)
            System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, oldRetransValue);
        else
            System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
    }

    /**
     * Makes sure that once a request has been answered by the server,
     * retransmissions of this request are not propagated to the UA and are
     * automatically handled with a retransmission of the last seen response
     *
     * @throws Exception if we screw up.
     */
    public void testServerResponseRetransmissions() throws Exception {
        String oldRetransValue = System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "100");

        //prepare to listen
        System.setProperty(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, "true");
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait for the message to arrive
        requestCollector.waitForRequest();

        Vector<StunMessageEvent> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());

        StunMessageEvent evt = reqs.get(0);

        byte[] tid = evt.getMessage().getTransactionID();

        stunStack.sendResponse(tid, bindingResponse, serverAddress, clientAddress);

        //wait for retransmissions
        Thread.sleep(500);

        //verify that we received a fair number of retransmitted responses.
        assertTrue("There were too few retransmissions of a binding response: " + responseCollector.receivedResponses.size(), responseCollector.receivedResponses.size() < 3);

        //restore the retransmissions prop in case others are counting on
        //defaults.
        if (oldRetransValue != null)
            System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, oldRetransValue);
        else
            System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);

        System.clearProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);
    }

    /**
     * A (very) weak test, verifying that transaction IDs are unique.
     * @throws Exception in case we feel like it.
     */
    public void testUniqueIDs() throws Exception {
        logger.info("---------------------------------\n testUniqueIDs");
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send req 1
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait for retransmissions
        requestCollector.waitForRequest();

        Vector<StunMessageEvent> reqs1 = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());
        StunMessageEvent evt1 = reqs1.get(0);

        //send a response to make the other guy shut up
        byte[] tid = evt1.getMessage().getTransactionID();

        stunStack.sendResponse(tid, bindingResponse, serverAddress, clientAddress);

        //send req 2
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait for retransmissions
        Thread.sleep(1000);

        Vector<StunMessageEvent> reqs2 = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());
        StunMessageEvent evt2 = reqs2.get(0);

        logger.info("txid: {} txid: {}", evt1.getMessage().getTransactionID(), evt2.getMessage().getTransactionID());
        assertFalse("Consecutive requests were assigned the same transaction id", Arrays.equals(evt1.getMessage().getTransactionID(), evt2.getMessage().getTransactionID()));
    }

    /**
     * Tests whether the properties for configuring the maximum number of
     * retransmissions in a transaction are working properly.
     *
     * @throws Exception if the gods so decide.
     */
    public void testClientTransactionMaxRetransmisssionsConfigurationParameter() throws Exception {
        //MAX_RETRANSMISSIONS

        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");
        //make sure we see retransmissions so that we may count them
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "true");
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);
        //wait for retransmissions
        Thread.sleep(1600);

        //verify
        Vector<StunMessageEvent> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());

        assertTrue("No retransmissions of the request have been received", reqs.size() > 1);
        assertEquals("The MAX_RETRANSMISSIONS param was not taken into account!", reqs.size(), 3);

    }

    /**
     * Tests whether the properties for configuring the minimum transaction
     * wait interval is working properly.
     *
     * @throws Exception if we are having a bad day.
     */
    public void testMinWaitIntervalConfigurationParameter() throws Exception {
        //MAX_RETRANSMISSIONS
        System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "50");
        //make sure we see retransmissions so that we may count them
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "true");
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait a while
        requestCollector.waitForRequest();

        //verify
        Vector<?> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());
        assertTrue("A retransmissions of the request was sent too early", reqs.size() < 2);

        //wait for a send
        Thread.sleep(110);

        reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());

        //verify
        assertEquals("A retransmissions of the request was not sent", 2, reqs.size());
    }

    /**
     * Tests whether the properties for configuring the maximum transaction
     * wait interval is working properly.
     *
     * @throws Exception if the gods so decide.
     */
    public void testMaxWaitIntervalConfigurationParameter() throws Exception {
        //MAX_RETRANSMISSIONS
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "100");
        //make sure we see retransmissions so that we may count them
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "true");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "11");
        stunStack.addRequestListener(serverAddress, requestCollector);
        //send
        stunStack.sendRequest(bindingRequest, serverAddress, clientAddress, responseCollector);

        //wait a while
        Thread.sleep(1200);

        //verify
        Vector<StunMessageEvent> reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());
        assertEquals("Not all retransmissions were made for the expected period " + "of time", 12, reqs.size());

        //wait for a send
        Thread.sleep(1800);

        //verify
        reqs = requestCollector.getRequestsForTransaction(bindingRequest.getTransactionID());
        assertEquals("A retransmissions of the request was sent, while not " + "supposed to", 12, reqs.size());
    }

    /**
     * A simply utility for asynchronous collection of requests.
     */
    private class PlainRequestCollector implements RequestListener {

        private Vector<StunMessageEvent> receivedRequestsVector = new Vector<>();

        private Boolean lock = Boolean.TRUE;

        /**
         * Logs the newly received request.
         *
         * @param evt the {@link StunMessageEvent} to log.
         */
        public void processRequest(StunMessageEvent evt) {
            logger.info("processRequest: {}", evt);
            synchronized (lock) {
                receivedRequestsVector.add(evt);
                lock.notify();
            }
        }

        /**
         * Only return requests from the specified tran because we might have capture others too.
         *
         * @param tranid the transaction that we'd like to get requests for.
         *
         * @return a Vector containing all request that we have received and that match <pre>tranid</pre>.
         */
        public Vector<StunMessageEvent> getRequestsForTransaction(byte[] tranid) {
            logger.info("getRequestsForTransaction: {}", TransactionID.toString(tranid));
            Vector<StunMessageEvent> newVec = new Vector<>();
            for (StunMessageEvent evt : receivedRequestsVector) {
                Message msg = evt.getMessage();
                if (Arrays.equals(tranid, msg.getTransactionID())) {
                    newVec.add(evt);
                }
            }
            return newVec;
        }

        /**
         * Blocks until a request arrives or 50 ms pass.
         */
        public void waitForRequest() {
            synchronized (lock) {
                try {
                    lock.wait(50);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * A simple utility for asynchronously collecting responses.
     */
    private static class PlainResponseCollector extends AbstractResponseCollector {
        /**
         * The responses we've collected so far.
         */
        public final Vector<Object> receivedResponses = new Vector<>();

        /**
         * Notifies this <pre>ResponseCollector</pre> that a transaction described by
         * the specified <pre>BaseStunMessageEvent</pre> has failed. The possible
         * reasons for the failure include timeouts, unreachable destination, etc.
         *
         * @param event the <pre>BaseStunMessageEvent</pre> which describes the failed
         * transaction and the runtime type of which specifies the failure reason
         * @see AbstractResponseCollector#processFailure(BaseStunMessageEvent)
         */
        protected void processFailure(BaseStunMessageEvent event) {
            logger.info("processFailure: {}", event);
            String receivedResponse;
            if (event instanceof StunFailureEvent)
                receivedResponse = "unreachable";
            else if (event instanceof StunTimeoutEvent)
                receivedResponse = "timeout";
            else
                receivedResponse = "failure";
            receivedResponses.add(receivedResponse);
        }

        /**
         * Logs the received <pre>response</pre>
         *
         * @param response the event to log.
         */
        public void processResponse(StunResponseEvent response) {
            logger.info("processResponse: {}", response);
            receivedResponses.add(response);
        }
    }
}
