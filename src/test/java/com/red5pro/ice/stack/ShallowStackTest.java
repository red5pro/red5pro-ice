/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.AbstractResponseCollector;
import com.red5pro.ice.Agent;
import com.red5pro.ice.BaseStunMessageEvent;
import com.red5pro.ice.CandidateType;
import com.red5pro.ice.Component;
import com.red5pro.ice.IceMediaStream;
import com.red5pro.ice.IceProcessingState;
import com.red5pro.ice.LocalCandidate;
import com.red5pro.ice.MsgFixture;
import com.red5pro.ice.RemoteCandidate;
import com.red5pro.ice.StunFailureEvent;
import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.StunResponseEvent;
import com.red5pro.ice.StunTimeoutEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.harvest.MappingCandidateHarvesters;
import com.red5pro.ice.nio.IceTcpTransport;
import com.red5pro.ice.nio.IceTransportTest;
import com.red5pro.ice.nio.IceUdpTransport;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.socket.IceUdpSocketWrapper;
import com.red5pro.ice.util.Utils;
import com.red5pro.server.util.PortManager;

import junit.framework.TestCase;

/**
 * All unit stack tests should be provided later. I just don't have the time now.
 *
 * @author Emil Ivov
 */
public class ShallowStackTest extends TestCase {

    private static final Logger logger = LoggerFactory.getLogger(ShallowStackTest.class);

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private StunStack stunStack;

    private MsgFixture msgFixture;

    private TransportAddress serverAddress;

    private TransportAddress localAddress;

    private DatagramCollector dgramCollector;

    private IceSocketWrapper localSock;

    /**
     * Transport type to be used for the test.
     */
    static Transport selectedTransport = Transport.UDP;

    //static String IPAddress = "fe80::995e:3662:2b68:2410";
    static String IPAddress = IceTransportTest.resolveLocalIP();

    /**
     * Creates a test instance for the method with the specified name.
     *
     * @param name the name of the test we'd like to create an instance for.
     */
    public ShallowStackTest(String name) {
        super(name);
    }

    /**
     * Initializes whatever sockets we'll be using in our tests.
     *
     * @throws Exception if something goes wrong with socket initialization.
     */
    @Before
    protected void setUp() throws Exception {
        super.setUp();
        logger.info("--------------------------------------------------------------------------------------\nSettting up {}", getClass().getName());
        System.setProperty("com.red5pro.ice.TERMINATION_DELAY", "500");
        System.setProperty("com.red5pro.ice.BIND_RETRIES", "1");
        //System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_LOCAL_ADDRESS", IPAddress);
        //System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_PUBLIC_ADDRESS", IPAddress);
        System.setProperty("com.red5pro.ice.harvest.ALLOWED_ADDRESSES", IPAddress);
        System.setProperty("com.red5pro.ice.ipv6.DISABLED", "true");
        // initializes the mapping harvesters
        MappingCandidateHarvesters.getHarvesters();
        //logger.info("setup");
        msgFixture = new MsgFixture();
        // XXX Paul: ephemeral port selection using 0 isnt working since the InetSocketAddress used by TransportAddress doesnt show the selected port
        // this causes connector lookups to fail due to port being still set to 0
        int serverPort = PortManager.findFreeUdpPort();
        serverAddress = new TransportAddress(IPAddress, serverPort, selectedTransport);
        // create and start listening here
        dgramCollector = new DatagramCollector();
        dgramCollector.startListening(serverAddress);
        // init the stack
        stunStack = new StunStack();
        // access point
        localAddress = new TransportAddress(IPAddress, PortManager.findFreeUdpPort(), selectedTransport);
        logger.info("Server: {} Client: {}", serverPort, localAddress.getPort());
        if (selectedTransport == Transport.UDP) {
            localSock = IceSocketWrapper.build(localAddress, null);
            // add the wrapper to the stack
            stunStack.addSocket(localSock, null, false);
        } else {
            localSock = IceSocketWrapper.build(localAddress, serverAddress);
            // add the wrapper to the stack
            stunStack.addSocket(localSock, serverAddress, false);
        }
    }

    /**
     * Releases the sockets we use here.
     *
     * @throws Exception if closing the sockets fails.
     */
    @After
    protected void tearDown() throws Exception {
        //logger.info("teardown");
        dgramCollector.stopListening();
        stunStack.removeSocket(localSock.getId(), localAddress);
        localSock.close();
        msgFixture = null;
        stunStack.shutDown();
        super.tearDown();
        logger.info("======================================================================================\nTorn down {}", getClass().getName());
    }

    @SuppressWarnings("incomplete-switch")
    @Test
    public void testMassBindings() throws Exception {
        // setup the acceptor 
        //IceUdpTransport.getInstance(localSock.getId()).registerStackAndSocket(stunStack, localSock);
        // create some agents
        int agentCount = 32;
        final List<Agent> agents = new ArrayList<>();
        for (int a = 0; a < agentCount; a++) {
            Agent agent = new Agent();
            agent.setProperty("proref", String.format("agent#%d", a));
            agent.setProperty("allocatedPort", String.format("%d", 49160 + a));
            agent.setProperty("remotePort", String.format("%d", 49260 + a));
            agents.add(agent);
        }
        // spawn n agents
        agents.forEach(agent -> {
            executor.submit(() -> {
                // publisher = false; subscriber = true
                agent.setControlling(true);
                agent.setTrickling(true);
                logger.debug("Agent state: {}", agent.getState());
                // create latch
                CountDownLatch iceSetupLatch = new CountDownLatch(1);
                // use a property change listener
                agent.addStateChangeListener((evt) -> {
                    logger.debug("Change event: {}", evt);
                    String id = agent.getProperty("proref");
                    int allocatedPort = Integer.valueOf(agent.getProperty("allocatedPort"));
                    long iceStartTime = Long.valueOf(agent.getProperty("iceStartTime"));
                    final IceProcessingState state = (IceProcessingState) evt.getNewValue();
                    switch (state) {
                        case COMPLETED:
                            logger.debug("ICE connectivity completed: {} elapsed: {}", id, (System.currentTimeMillis() - iceStartTime));
                            break;
                        case FAILED:
                            logger.warn("ICE connectivity failed for: {} port: {} elapsed: {}", id, allocatedPort, (System.currentTimeMillis() - iceStartTime));
                            // now stop
                            agent.free();
                            break;
                        case TERMINATED:
                            logger.warn("ICE connectivity terminated: {} elapsed: {}", id, (System.currentTimeMillis() - iceStartTime));
                            iceSetupLatch.countDown();
                            break;
                    }
                });
                try {
                    IceMediaStream stream = agent.createMediaStream("media-0");
                    int port = Integer.valueOf(agent.getProperty("allocatedPort"));
                    Component component = agent.createComponent(stream, Transport.UDP, port, port, port);
                    int allocatedPort = component.getSocket(Transport.UDP).getLocalPort();
                    assertEquals(port, allocatedPort);
                    // may want to check port
                    LocalCandidate localCand = component.getDefaultCandidate();
                    // create server-end / remote
                    int remotePort = Integer.valueOf(agent.getProperty("remotePort"));
                    TransportAddress serverAddr = new TransportAddress(IPAddress, remotePort, Transport.UDP);
                    // create remote candidate
                    RemoteCandidate remoteCand = new RemoteCandidate(serverAddr, component, CandidateType.HOST_CANDIDATE, localCand.getFoundation(), localCand.getComponentId(), 1686052607L, null);
                    String remoteUfrag = String.format("rem%d", remotePort);
                    remoteCand.setUfrag(remoteUfrag);
                    stream.setRemoteUfrag(remoteUfrag);
                    component.addRemoteCandidate(remoteCand);
                    remoteCand.setProperty("proref", agent.getProperty("proref"));
                    // server socket and its own stunstack
                    StunStack stnStack = new StunStack();
                    IceUdpSocketWrapper serverSock = (IceUdpSocketWrapper) IceSocketWrapper.build(serverAddr, null);
                    stnStack.addSocket(serverSock, serverSock.getRemoteTransportAddress(), true); // do socket binding   
                    // instance a remote server
                    //ResponseSequenceServer server = new ResponseSequenceServer(stnStack, serverAddr);
                    //server.start();             
                } catch (Exception e) {
                    logger.warn("Exception in setupICE for: {}", agent.getProperty("proref"));
                }
                long iceStartTime = System.currentTimeMillis();
                agent.setProperty("iceStartTime", String.format("%d", iceStartTime));
                agent.startConnectivityEstablishment();
                try {
                    if (iceSetupLatch.await(5000L, TimeUnit.MILLISECONDS)) {
                        logger.debug("ICE establishment is complete");
                    } else {
                        logger.warn("ICE establishment failed for: {}", agent.getProperty("proref"));
                        fail("ICE timeded out");
                    }
                } catch (Exception e) {
                    logger.warn("Exception in setupICE for: {}", agent.getProperty("proref"));
                }
            });
        });
        try {
            Thread.sleep(7000L);
        } catch (Exception e) {
            logger.warn("Exception in test", e);
        }
        logger.info("Cleaning up agents");
        agents.forEach(agent -> {
            agent.free();
        });
        //server.shutDown();
    }

    /**
     * Sends a binding request using the stack to a bare socket, and verifies
     * that it is received and that the contents of the datagram corresponds to
     * the request that was sent.
     *
     * @throws java.lang.Exception if we fail
     */
    public void testSendRequest() throws Exception {
        logger.info("\n SendRequest");
        Request bindingRequest = MessageFactory.createBindingRequest();
        //dgramCollector.startListening(serverAddress);
        stunStack.sendRequest(bindingRequest, serverAddress, localAddress, new SimpleResponseCollector());
        // wait for its arrival
        dgramCollector.waitForPacket();
        DatagramPacket receivedPacket = dgramCollector.collectPacket();
        assertTrue("The stack did not properly send a Binding Request", (receivedPacket.getLength() > 0));
        Request receivedRequest = (Request) Request.decode(receivedPacket.getData(), 0, receivedPacket.getLength());
        assertEquals("The received request did not match the one that was sent", bindingRequest, receivedRequest);
        logger.info("Sent request: {}", Utils.toHexString(bindingRequest.encode(stunStack)));
        logger.info("Received request: {}", Utils.toHexString(receivedRequest.encode(stunStack)));
        // wait for retransmissions
        //dgramCollector.startListening(serverAddress);
        dgramCollector.waitForPacket();
        receivedPacket = dgramCollector.collectPacket();
        assertTrue("The stack did not retransmit a Binding Request", (receivedPacket.getLength() > 0));
        receivedRequest = (Request) Request.decode(receivedPacket.getData(), 0, receivedPacket.getLength());
        assertEquals("The retransmitted request did not match the original", bindingRequest, receivedRequest);
    }

    /**
     * Sends a byte array containing a bindingRequest, through a datagram socket and verifies that the stack receives it alright.
     *
     * @throws java.lang.Exception if we fail
     */
    public void testReceiveRequest() throws Exception {
        logger.info("\n ReceiveRequest");
        // we're expecting to receive on the ice4j side (non-controlling)
        if (selectedTransport == Transport.UDP) {
            IceUdpTransport.getInstance(localSock.getId()).registerStackAndSocket(stunStack, localSock);
        } else {
            IceTcpTransport.getInstance(localSock.getId()).registerStackAndSocket(stunStack, localSock);
        }
        SimpleRequestCollector requestCollector = new SimpleRequestCollector();
        stunStack.addRequestListener(requestCollector);
        dgramCollector.send(msgFixture.bindingRequest2, localAddress);
        // wait for the packet to arrive
        requestCollector.waitForRequest();
        Request collectedRequest = requestCollector.collectedRequest;
        assertNotNull("No request has been received", collectedRequest);
        byte[] expectedReturn = msgFixture.bindingRequest2;
        byte[] actualReturn = collectedRequest.encode(stunStack);
        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(expectedReturn, actualReturn));
        logger.info(Utils.toHexString(expectedReturn));
        logger.info(Utils.toHexString(actualReturn));
    }

    /**
     * Sends a byte array containing a bindingRequest, through a datagram socket,
     * verifies that the stack receives it properly and then sends a response
     * using the stack. Finally, the response is expected at the other end and
     * compared with the sent one.
     *
     * @throws java.lang.Exception if we fail
     */
    public void testSendResponse() throws Exception {
        logger.info("\n SendResponse");
        // we're expecting to receive on the ice4j side (non-controlling)
        if (selectedTransport == Transport.UDP) {
            IceUdpTransport.getInstance(localSock.getId()).registerStackAndSocket(stunStack, localSock);
        } else {
            IceTcpTransport.getInstance(localSock.getId()).registerStackAndSocket(stunStack, localSock);
        }
        //---------- send & receive the request --------------------------------
        SimpleRequestCollector requestCollector = new SimpleRequestCollector();
        stunStack.addRequestListener(requestCollector);
        dgramCollector.send(msgFixture.bindingRequest, localAddress);
        // wait for the packet to arrive
        requestCollector.waitForRequest();
        Request collectedRequest = requestCollector.collectedRequest;
        assertNotNull(collectedRequest);
        byte[] expectedReturn = msgFixture.bindingRequest;
        byte[] actualReturn = collectedRequest.encode(stunStack);
        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(expectedReturn, actualReturn));
        //---------- create the response ---------------------------------------
        Response bindingResponse = MessageFactory.create3489BindingResponse(new TransportAddress(MsgFixture.ADDRESS_ATTRIBUTE_ADDRESS, MsgFixture.ADDRESS_ATTRIBUTE_PORT, Transport.UDP), new TransportAddress(MsgFixture.ADDRESS_ATTRIBUTE_ADDRESS_2, MsgFixture.ADDRESS_ATTRIBUTE_PORT_2, Transport.UDP), new TransportAddress(MsgFixture.ADDRESS_ATTRIBUTE_ADDRESS_3, MsgFixture.ADDRESS_ATTRIBUTE_PORT_3, Transport.UDP));
        //---------- send & receive the response -------------------------------
        //dgramCollector.startListening(serverAddress);
        stunStack.sendResponse(collectedRequest.getTransactionID(), bindingResponse, localAddress, serverAddress);
        // wait for its arrival
        dgramCollector.waitForPacket();
        DatagramPacket receivedPacket = dgramCollector.collectPacket();
        assertTrue("The stack did not properly send a Binding Request", (receivedPacket.getLength() > 0));
        Response receivedResponse = (Response) Response.decode(receivedPacket.getData(), 0, receivedPacket.getLength());
        assertEquals("The received request did not match the one that was sent.", bindingResponse, receivedResponse);
    }

    /**
     * Performs a basic test on message reception
     *
     * @throws Exception if something fails somewhere.
     */
    public void testReceiveResponse() throws Exception {
        logger.info("\n ReceiveResponse");
        SimpleResponseCollector collector = new SimpleResponseCollector();
        //--------------- send the original request ----------------------------
        Request bindingRequest = MessageFactory.createBindingRequest();
        TransactionID transId = stunStack.sendRequest(bindingRequest, serverAddress, localAddress, collector);
        logger.info("Request transaction id: {}", transId);
        // wait for its arrival
        collector.waitForResponse();
        // create the right response
        byte[] response = new byte[msgFixture.bindingResponse.length];
        System.arraycopy(msgFixture.bindingResponse, 0, response, 0, response.length);
        // Set the valid tid.
        System.arraycopy(bindingRequest.getTransactionID(), 0, response, 8, 12);
        // send the response
        dgramCollector.send(response, localAddress);
        // wait for the packet to arrive
        collector.waitForResponse();
        Response collectedResponse = collector.collectedResponse;
        byte[] expectedReturn = response;
        byte[] actualReturn = collectedResponse.encode(stunStack);
        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(expectedReturn, actualReturn));
    }

    /**
     * Created to test Edge provided data, which we know has issues.
     */
    //    public void testEdgeControlled() throws Exception {
    //        logger.info("\nEdge");
    //        // user name
    //        @SuppressWarnings("unused")
    //        final String userName = "7vska1bkv1e9u7:9YVL";
    //        // register our dummy credential authority
    //        stunStack.getCredentialsManager().registerAuthority(new CredentialsAuthority() {
    //
    //            // local key / override the key so our data is valid
    //            byte[] localKey = hexStringToByteArray("363734656A3873726272346C6475316C3736636264676F356D73");
    //
    //            byte[] remoteKey = hexStringToByteArray("364974756553306563335930774959314167714B626A6456");
    //
    //            @Override
    //            public byte[] getLocalKey(String username) {
    //                return localKey;
    //            }
    //
    //            @Override
    //            public byte[] getRemoteKey(String username, String media) {
    //                return remoteKey;
    //            }
    //
    //            @Override
    //            public boolean checkLocalUserName(String username) {
    //                return username.split(":")[0].equals(username);
    //            }
    //
    //        });
    //
    //        byte[] txId = hexStringToByteArray("ED815F6A0BD1AFEF51BA05FF");
    //        // valid sized username == 19
    //        byte[] req1 = hexStringToByteArray("0001005C2112A442ED815F6A0BD1AFEF51BA05FF000600143776736B6131626B7631653975373A3959564C00002400046EFFFEFF802A00080000000000318222805400043100000080700004000000030008001446B190015E4C153EBC92E6EEFF7EDD379AECE6C58028000465E68F73");
    //        // incorrect size for username == 20
    //        //byte[] req2 = hexStringToByteArray("0001005C2112A442ED815F6A0BD1AFEF51BA05FF000600133776736B6131626B7631653975373A3959564C00002400046EFFFEFF802A0008000000000031822280540001310000008070000400000003000800142C8D92719B35E6AC883576CC430F4540DAEABFA180280004E9CC3B69");
    //
    //        Request collectedRequest = (Request) Message.decode(req1, 0, req1.length);
    //        assertTrue("Transaction ids don't match", Arrays.equals(txId, collectedRequest.getTransactionID()));
    //
    //        byte[] actualReturn = collectedRequest.encode(stunStack);
    //        logger.info(byteArrayToHexString(req1));
    //        logger.info(byteArrayToHexString(actualReturn));
    //
    //        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(req1, actualReturn));
    //    }

    /**
     * Created to test Safari provided data.
     */
    //    public void testSafariControlled() throws Exception {
    //        logger.info("\nSafari");
    //        // user name
    //        @SuppressWarnings("unused")
    //        final String userName = "5tm7u1brpgfl50:lwoc";
    //        // register our dummy credential authority
    //        stunStack.getCredentialsManager().registerAuthority(new CredentialsAuthority() {
    //
    //            // local key / override the key so our data is valid
    //            byte[] localKey = hexStringToByteArray("376A3164366C696B6963366D68356C393232766A743473306867");
    //
    //            byte[] remoteKey = hexStringToByteArray("474A6B2F4A3174533864376A6B55376D626E4C736252492B");
    //
    //            @Override
    //            public byte[] getLocalKey(String username) {
    //                return localKey;
    //            }
    //
    //            @Override
    //            public byte[] getRemoteKey(String username, String media) {
    //                return remoteKey;
    //            }
    //
    //            @Override
    //            public boolean checkLocalUserName(String username) {
    //                return username.split(":")[0].equals(username);
    //            }
    //
    //        });
    //
    //        // valid sized username == 19
    //        byte[] req1 = hexStringToByteArray("000100542112A44236703243454B505231374B6C0006001335746D37753162727067666C35303A6C776F6300C05700040000003280290008F80F30B4FC105EEE002400046E001EFF000800145316B909FEA5D754C82F0510656BDE9CBCCD7F7680280004F7116D96");
    //        byte[] txId = hexStringToByteArray("36703243454B505231374B6C");
    //
    //        Request collectedRequest = (Request) Message.decode(req1, 0, req1.length);
    //        assertTrue("Transaction ids don't match", Arrays.equals(txId, collectedRequest.getTransactionID()));
    //
    //        byte[] actualReturn = collectedRequest.encode(stunStack);
    //        logger.info(byteArrayToHexString(req1));
    //        logger.info(byteArrayToHexString(actualReturn));
    //
    //        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(req1, actualReturn));
    //    }

    /**
     * Created to test Chrome provided data.
     */
    //    public void testChromeControlled() throws Exception {
    //        logger.info("\nChrome");
    //        // user name
    //        @SuppressWarnings("unused")
    //        final String userName = "bpvm21bs5v3ecp:dVlI";
    //        // register our dummy credential authority
    //        stunStack.getCredentialsManager().registerAuthority(new CredentialsAuthority() {
    //
    //            // local key / override the key so our data is valid
    //            byte[] localKey = hexStringToByteArray("3166686335376A31366D353231727569733734396F6669736A65");
    //
    //            byte[] remoteKey = hexStringToByteArray("4D304338575A56514E54676B796848452B6D715332653067");
    //
    //            @Override
    //            public byte[] getLocalKey(String username) {
    //                return localKey;
    //            }
    //
    //            @Override
    //            public byte[] getRemoteKey(String username, String media) {
    //                return remoteKey;
    //            }
    //
    //            @Override
    //            public boolean checkLocalUserName(String username) {
    //                return username.split(":")[0].equals(username);
    //            }
    //
    //        });
    //
    //        // valid sized username == 19
    //        byte[] req1 = hexStringToByteArray("000100582112A442676E6D3976584A48576B6841000600136270766D323162733576336563703A64566C4900C057000400000032802A0008E19044DBFFC7814C00250000002400046E001EFF000800146EF5B93E9F4AB9AE415E0D688834962D279787F48028000489247947");
    //        byte[] txId = hexStringToByteArray("676E6D3976584A48576B6841");
    //
    //        Request collectedRequest = (Request) Message.decode(req1, 0, req1.length);
    //        assertTrue("Transaction ids don't match", Arrays.equals(txId, collectedRequest.getTransactionID()));
    //
    //        byte[] actualReturn = collectedRequest.encode(stunStack);
    //        logger.info(byteArrayToHexString(req1));
    //        logger.info(byteArrayToHexString(actualReturn));
    //
    //        assertTrue("Received request was not the same as the one that was sent", Arrays.equals(req1, actualReturn));
    //    }

    //--------------------------------------- listener implementations ---------
    /**
     * A simple utility that allows us to asynchronously collect messages.
     */
    public static class SimpleResponseCollector extends AbstractResponseCollector {

        /**
         * The response that we've just collected or null if none arrived while we were waiting.
         */
        Response collectedResponse;

        /**
         * Notifies this ResponseCollector that a transaction described by
         * the specified BaseStunMessageEvent has failed. The possible
         * reasons for the failure include timeouts, unreachable destination, etc.
         *
         * @param event the BaseStunMessageEvent which describes the failed
         * transaction and the runtime type of which specifies the failure reason
         * @see AbstractResponseCollector#processFailure(BaseStunMessageEvent)
         */
        protected void processFailure(BaseStunMessageEvent event) {
            String msg;
            if (event instanceof StunFailureEvent) {
                msg = "Unreachable";
            } else if (event instanceof StunTimeoutEvent) {
                msg = "Timeout";
            } else {
                msg = "Failure";
            }
            logger.info("SimpleResponseCollector {}", msg);
        }

        /**
         * Logs the received response and notifies the wait method.
         *
         * @param response a StunMessageEvent which describes the
         * received STUN Response
         */
        public void processResponse(StunResponseEvent response) {
            collectedResponse = (Response) response.getMessage();
            logger.debug("Received response: {}", collectedResponse);
        }

        /**
         * Blocks until a request arrives or 50 ms pass.
         * @throws InterruptedException 
         */
        public void waitForResponse() throws InterruptedException {
            do {
                Thread.sleep(50L);
                break;
            } while (collectedResponse == null);
        }
    }

    /**
     * A utility class for asynchronously collecting requests.
     */
    public class SimpleRequestCollector implements RequestListener {
        /**
         * The one request that this collector has received or null if
         * none arrived while we were waiting.
         */
        private Request collectedRequest;

        /**
         * Indicates that a StunRequest has just been received.
         *
         * @param evt the StunMessageEvent containing the details of
         * the newly received request.
         */
        public void processRequest(StunMessageEvent evt) {
            collectedRequest = (Request) evt.getMessage();
            stunStack.removeRequestListener(this);
            logger.debug("Received request: {}", collectedRequest);
        }

        /**
         * Blocks until a request arrives or 50 ms pass.
         */
        public void waitForRequest() {
            if (collectedRequest == null) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    logger.warn("oops", e);
                }
            }
        }
    }

}
