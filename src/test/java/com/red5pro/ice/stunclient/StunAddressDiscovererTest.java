/* See LICENSE.md for license information */
package com.red5pro.ice.stunclient;

import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunException;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.server.util.PortManager;

import junit.framework.TestCase;

/**
 * The StunAddressDiscovererTest_XXX set of tests were created to verify stun
 * operation for scenarios of some basic types of firewalls. The purpose of
 * these tests is to make sure that transaction retransmissions and rereceptions
 * are handled transparently by the stack, as well as verify overall protocol
 * operations for IPv4/IPv6 and mixed environments.
 *
 * <p>Company: Net Research Team, Louis Pasteur University</p>
 * @author Emil Ivov
 */
public class StunAddressDiscovererTest extends TestCase {

    private NetworkConfigurationDiscoveryProcess stunAddressDiscoverer = null;

    private TransportAddress discovererAddress;

    private TransportAddress responseServerAddress;

    private TransportAddress mappedClientAddress;

    private TransportAddress mappedClientAddressPort2;

    private ResponseSequenceServer responseServer;

    public StunAddressDiscovererTest(String name) throws StunException {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        discovererAddress = new TransportAddress("127.0.0.1", PortManager.findFreeUdpPort(), Transport.UDP);
        responseServerAddress = new TransportAddress("127.0.0.1", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddress = new TransportAddress("212.56.4.10", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddressPort2 = new TransportAddress("212.56.4.10", PortManager.findFreeUdpPort(), Transport.UDP);

        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "100");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");

        StunStack stunStack = new StunStack();

        responseServer = new ResponseSequenceServer(stunStack, responseServerAddress);
        stunAddressDiscoverer = new NetworkConfigurationDiscoveryProcess(stunStack, discovererAddress, responseServerAddress);

        stunAddressDiscoverer.start();
        responseServer.start();
    }

    protected void tearDown() throws Exception {
        System.clearProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);
        System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        responseServer.shutDown();
        stunAddressDiscoverer.shutDown();
        stunAddressDiscoverer = null;

        super.tearDown();
    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it's in a network where UDP is blocked.
     *
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeBlockedUDP() throws Exception {
        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();
        expectedReturn.setNatType(StunDiscoveryReport.UDP_BLOCKING_FIREWALL);
        expectedReturn.setPublicAddress(null);
        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a Symmetric NAT.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeSymmetricNat() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse2 = null;
        Response testIResponse3 = MessageFactory.create3489BindingResponse(mappedClientAddressPort2, responseServerAddress,
                responseServerAddress);

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);
        responseServer.addMessage(testIResponse3);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.SYMMETRIC_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a Port Restricted Cone.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizePortRestrictedCone() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse2 = null;
        Response testIResponse3 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse4 = null;

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);
        responseServer.addMessage(testIResponse3);
        responseServer.addMessage(testIResponse4);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.PORT_RESTRICTED_CONE_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a Restricted Cone.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeRestrictedCone() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse2 = null;
        Response testIResponse3 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse4 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);
        responseServer.addMessage(testIResponse3);
        responseServer.addMessage(testIResponse4);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.RESTRICTED_CONE_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a Full Cone.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeFullCone() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);
        Response testIResponse2 = MessageFactory.create3489BindingResponse(mappedClientAddress, responseServerAddress,
                responseServerAddress);

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.FULL_CONE_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);
    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a UDP Symmetric Firewall.
     *
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeUdpSymmetricFirewall() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(discovererAddress, responseServerAddress, responseServerAddress);
        Response testIResponse2 = null;

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.SYMMETRIC_UDP_FIREWALL);
        expectedReturn.setPublicAddress(discovererAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responses are given the stun client so that
     * it concludes it is behind a Open Internet.
     *
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeOpenInternet() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(discovererAddress, responseServerAddress, responseServerAddress);
        Response testIResponse2 = MessageFactory.create3489BindingResponse(discovererAddress, responseServerAddress, responseServerAddress);

        responseServer.addMessage(testIResponse1);
        responseServer.addMessage(testIResponse2);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.OPEN_INTERNET);
        expectedReturn.setPublicAddress(discovererAddress);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);
    }
}
