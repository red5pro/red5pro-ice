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
 * Makes basic stun tests for cases where local network addresses and the public
 * NAT address are using different IP versions. (e.g. Local addresses are v4
 * public NAT address is v6 or vice versa)
 *
 *
 * The StunAddressDiscovererTest_XXX set of tests were created to verify stun
 * operation for scenarios of some basic types of firewalls. The purpose of
 * these tests is to make sure that transaction retransmissions and rereceptions
 * are handled transparently by the stack, as well as verify overall protocol
 * operations for IPv4/IPv6 and mixed environments.
 *
 * <p>Company: Net Research Team, Louis Pasteur University</p>
 * @author Emil Ivov
 */
public class StunAddressDiscovererTest_v4v6 extends TestCase {

    private NetworkConfigurationDiscoveryProcess stunAddressDiscoverer_v6 = null;

    private NetworkConfigurationDiscoveryProcess stunAddressDiscoverer_v4 = null;

    private TransportAddress discovererAddress_v4;

    private TransportAddress discovererAddress_v6;

    private TransportAddress responseServerAddress_v6;

    private TransportAddress responseServerAddress_v4;

    private TransportAddress mappedClientAddress_v6;

    private TransportAddress mappedClientAddress_v6_Port2;

    private TransportAddress mappedClientAddress_v4;

    private TransportAddress mappedClientAddress_v4_Port2;

    private ResponseSequenceServer responseServer_v6 = null;

    private ResponseSequenceServer responseServer_v4 = null;

    public StunAddressDiscovererTest_v4v6(String name) throws StunException {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();

        discovererAddress_v4 = new TransportAddress("127.0.0.1", PortManager.findFreeUdpPort(), Transport.UDP);
        discovererAddress_v6 = new TransportAddress("::1", PortManager.findFreeUdpPort(), Transport.UDP);
        responseServerAddress_v6 = new TransportAddress("::1", PortManager.findFreeUdpPort(), Transport.UDP);
        responseServerAddress_v4 = new TransportAddress("127.0.0.1", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddress_v6 = new TransportAddress("2001:660:4701:1001:ff::1", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddress_v6_Port2 = new TransportAddress("2001:660:4701:1001:ff::1", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddress_v4 = new TransportAddress("130.79.99.55", PortManager.findFreeUdpPort(), Transport.UDP);
        mappedClientAddress_v4_Port2 = new TransportAddress("130.79.99.55", PortManager.findFreeUdpPort(), Transport.UDP);

        StunStack stunStack = new StunStack();

        responseServer_v6 = new ResponseSequenceServer(stunStack, responseServerAddress_v6);
        responseServer_v4 = new ResponseSequenceServer(stunStack, responseServerAddress_v4);

        stunAddressDiscoverer_v6 = new NetworkConfigurationDiscoveryProcess(stunStack, discovererAddress_v6, responseServerAddress_v6);
        stunAddressDiscoverer_v4 = new NetworkConfigurationDiscoveryProcess(stunStack, discovererAddress_v4, responseServerAddress_v4);

        stunAddressDiscoverer_v6.start();
        stunAddressDiscoverer_v4.start();
        responseServer_v6.start();
        responseServer_v4.start();

        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "100");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "2");
    }

    protected void tearDown() throws Exception {
        System.clearProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);
        System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);

        responseServer_v6.shutDown();
        responseServer_v4.shutDown();
        stunAddressDiscoverer_v6.shutDown();
        stunAddressDiscoverer_v6 = null;
        stunAddressDiscoverer_v4.shutDown();
        stunAddressDiscoverer_v4 = null;

        //give the sockets the time to clear out
        Thread.sleep(1000);

        super.tearDown();
    }

    /**
     * Performs a test where no responces are given the stun client so that
     * it concludes it is behind a Symmetric NAT.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeSymmetricNat_Local_v6_Public_v4() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress_v4, responseServerAddress_v6, responseServerAddress_v6);
        Response testIResponse2 = null;
        Response testIResponse3 = MessageFactory.create3489BindingResponse(mappedClientAddress_v4_Port2, responseServerAddress_v6, responseServerAddress_v6);

        responseServer_v6.addMessage(testIResponse1);
        responseServer_v6.addMessage(testIResponse2);
        responseServer_v6.addMessage(testIResponse3);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.SYMMETRIC_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress_v4);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer_v6.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a v4-v6 sym env.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responces are given the stun client so that
     * it concludes it is behind a Symmetric NAT.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeSymmetricNat_Local_v4_Public_v6() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress_v6, responseServerAddress_v4, responseServerAddress_v4);
        Response testIResponse2 = null;
        Response testIResponse3 = MessageFactory.create3489BindingResponse(mappedClientAddress_v6_Port2, responseServerAddress_v4, responseServerAddress_v4);

        responseServer_v4.addMessage(testIResponse1);
        responseServer_v4.addMessage(testIResponse2);
        responseServer_v4.addMessage(testIResponse3);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.SYMMETRIC_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress_v6);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer_v4.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responces are given the stun client so that
     * it concludes it is behind a Full Cone.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeFullCone_Local_v6_Public_v4() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress_v4, responseServerAddress_v6, responseServerAddress_v6);
        Response testIResponse2 = MessageFactory.create3489BindingResponse(mappedClientAddress_v4, responseServerAddress_v6, responseServerAddress_v6);

        responseServer_v6.addMessage(testIResponse1);
        responseServer_v6.addMessage(testIResponse2);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.FULL_CONE_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress_v4);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer_v6.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }

    /**
     * Performs a test where no responces are given the stun client so that
     * it concludes it is behind a Full Cone.
     * @throws Exception if anything goes wrong ( surprised? ).
     */
    public void testRecognizeFullCone_Local_v4_Public_v6() throws Exception {
        //define the server response sequence
        Response testIResponse1 = MessageFactory.create3489BindingResponse(mappedClientAddress_v6, responseServerAddress_v4, responseServerAddress_v4);
        Response testIResponse2 = MessageFactory.create3489BindingResponse(mappedClientAddress_v6, responseServerAddress_v4, responseServerAddress_v4);

        responseServer_v4.addMessage(testIResponse1);
        responseServer_v4.addMessage(testIResponse2);

        StunDiscoveryReport expectedReturn = new StunDiscoveryReport();

        expectedReturn.setNatType(StunDiscoveryReport.FULL_CONE_NAT);
        expectedReturn.setPublicAddress(mappedClientAddress_v6);

        StunDiscoveryReport actualReturn = stunAddressDiscoverer_v4.determineAddress();
        assertEquals("The StunAddressDiscoverer failed for a no-udp environment.", expectedReturn, actualReturn);

    }
}
