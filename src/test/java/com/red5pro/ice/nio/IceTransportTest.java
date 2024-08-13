package com.red5pro.ice.nio;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.MappedAddressAttribute;
import com.red5pro.ice.attribute.XorMappedAddressAttribute;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.AbstractResponseCollector;
import com.red5pro.ice.BaseStunMessageEvent;
import com.red5pro.ice.ResponseCollector;
import com.red5pro.ice.StunFailureEvent;
import com.red5pro.ice.StunResponseEvent;
import com.red5pro.ice.StunTimeoutEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;


public class IceTransportTest {

    private static final Logger log = LoggerFactory.getLogger(IceTransportTest.class);

    // static Amazon IP service
    private static final String AWS_IP_CHECK_URI = "https://checkip.amazonaws.com";

    private static String GOOGLE_STUN = "stun1.l.google.com";

    private static int GOOGLE_STUN_PORT = 19302;

    @Test
    public void testUDPWithGoogleStun() {
        // udp for this test
        Transport transport = Transport.UDP;
        String localIP = resolveLocalIP(), publicIP = resolveIPOverHTTP(AWS_IP_CHECK_URI);
        int port = 49155;
        // ice4j
        System.setProperty("com.red5pro.ice.BIND_RETRIES", "1");
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_LOCAL_ADDRESS", localIP);
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_PUBLIC_ADDRESS", publicIP);
        TransportAddress stunTransportAddress = new TransportAddress(GOOGLE_STUN, GOOGLE_STUN_PORT, transport);
        // use ice4j to get our public IP
        try {
            TransportAddress localTransportAddress = new TransportAddress(localIP, port, transport);
            publicIP = resolvePublicIP(localTransportAddress, stunTransportAddress);
        } catch (Throwable t) {
            log.warn("Exception contacting STUN server at: {}", stunTransportAddress, t);
        }
        log.info("Public IP address: {}", publicIP);
        //assertEquals("71.38.180.149", publicIP);
        // set up allowed addresses to prevent NIC scanning in HostCandidateHarvester.getAvailableHostAddresses()
        String allowedIPs = null;
        if (localIP.contentEquals(publicIP)) {
            allowedIPs = localIP;
        } else {
            allowedIPs = String.format("%s;%s", localIP, publicIP);
        }
        System.setProperty("com.red5pro.ice.harvest.ALLOWED_ADDRESSES", allowedIPs);
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_DEFAULT_TRANSPORT", transport.getTransportName());
        // default termination delay to 1s since anything less seems to fail on multiple subscribers on quick connect intervals
        System.setProperty("com.red5pro.ice.TERMINATION_DELAY", "500");
    }

    @Test
    public void testManyQuicklyUDPWithGoogleStun() {
        // udp for this test
        Transport transport = Transport.UDP;
        String localIP = resolveLocalIP(), publicIP = resolveIPOverHTTP(AWS_IP_CHECK_URI);
        int port = 49155;
        // ice4j
        System.setProperty("com.red5pro.ice.BIND_RETRIES", "1");
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_LOCAL_ADDRESS", localIP);
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_PUBLIC_ADDRESS", publicIP);
        TransportAddress stunTransportAddress = new TransportAddress(GOOGLE_STUN, GOOGLE_STUN_PORT, transport);
        // use ice4j to get our public IP
        try {
            TransportAddress localTransportAddress = new TransportAddress(localIP, port, transport);
            publicIP = resolvePublicIP(localTransportAddress, stunTransportAddress);
        } catch (Throwable t) {
            log.warn("Exception contacting STUN server at: {}", stunTransportAddress, t);
        }
        log.info("Public IP address: {}", publicIP);
        //assertEquals("71.38.180.149", publicIP);
        // set up allowed addresses to prevent NIC scanning in HostCandidateHarvester.getAvailableHostAddresses()
        String allowedIPs = null;
        if (localIP.contentEquals(publicIP)) {
            allowedIPs = localIP;
        } else {
            allowedIPs = String.format("%s;%s", localIP, publicIP);
        }
        System.setProperty("com.red5pro.ice.harvest.ALLOWED_ADDRESSES", allowedIPs);
        System.setProperty("com.red5pro.ice.harvest.NAT_HARVESTER_DEFAULT_TRANSPORT", transport.getTransportName());
        // default termination delay to 1s since anything less seems to fail on multiple subscribers on quick connect intervals
        System.setProperty("com.red5pro.ice.TERMINATION_DELAY", "500");

        // test
    }

    /**
     * Resolves the servers public IP address using a STUN binding request.
     *
     * @param localTransportAddress
     * @param stunTransportAddress
     * @return public IP address or null if some failure occurs
     * @throws IOException
     * @throws InterruptedException
     */
    private String resolvePublicIP(TransportAddress localTransportAddress, TransportAddress stunTransportAddress)
            throws IOException, InterruptedException {
        String publicIP = null;
        SynchronousQueue<Response> que = new SynchronousQueue<>();
        // collector for responses
        ResponseCollector responseCollector = new AbstractResponseCollector() {

            /**
             * Notifies this ResponseCollector that a transaction described by the specified BaseStunMessageEvent has failed. The possible
             * reasons for the failure include timeouts, unreachable destination, etc.
             *
             * @param event the BaseStunMessageEvent which describes the failed transaction and the runtime type of which specifies the failure reason
             * @see AbstractResponseCollector#processFailure(BaseStunMessageEvent)
             */
            @Override
            protected void processFailure(BaseStunMessageEvent event) {
                String msg;
                if (event instanceof StunFailureEvent) {
                    msg = "Unreachable";
                } else if (event instanceof StunTimeoutEvent) {
                    msg = "Timeout";
                } else {
                    msg = "Failure";
                }
                log.debug("ResponseCollector: {}", msg);
            }

            /**
             * Queues the received response.
             *
             * @param response a StunMessageEvent which describes the received STUN Response
             */
            @Override
            public void processResponse(StunResponseEvent response) {
                que.offer((Response) response.getMessage());
            }

        };
        // init the stun stack
        StunStack stunStack = new StunStack();
        // create an ice socket wrapper with the transport based on the addresses supplied
        IceSocketWrapper iceSocket = IceSocketWrapper.build(localTransportAddress, stunTransportAddress);
        // add the wrapper to the stack
        if (iceSocket.isUDP()) {
            // when its udp, bind so we'll be listening
            stunStack.addSocket(iceSocket, stunTransportAddress, true);
        } else if (iceSocket.isTCP()) {
            // get the handler
            IceHandler handler = IceTransport.getIceHandler();
            // now connect as a client
            NioSocketConnector connector = new NioSocketConnector(1);
            SocketSessionConfig config = connector.getSessionConfig();
            config.setReuseAddress(true);
            config.setTcpNoDelay(true);
            // set an idle time of 30s (default)
            config.setIdleTime(IdleStatus.BOTH_IDLE, IceTransport.getTimeout());
            // set connection timeout of x milliseconds
            connector.setConnectTimeoutMillis(3000L);
            // add the ice protocol encoder/decoder
            connector.getFilterChain().addLast("protocol", IceTransport.getProtocolcodecfilter());
            // set the handler on the connector
            connector.setHandler(handler);
            // register
            handler.registerStackAndSocket(stunStack, iceSocket);
            // dont bind when using tcp, since java doesn't allow client+server at the same time
            stunStack.addSocket(iceSocket, stunTransportAddress, false);
            // connect
            connector.setDefaultRemoteAddress(stunTransportAddress);
            ConnectFuture future = connector.connect(stunTransportAddress, localTransportAddress);
            future.addListener(new IoFutureListener<ConnectFuture>() {

                @Override
                public void operationComplete(ConnectFuture future) {
                    log.debug("operationComplete {} {}", future.isDone(), future.isCanceled());
                    if (future.isConnected()) {
                        IoSession sess = future.getSession();
                        if (sess != null) {
                            iceSocket.setSession(sess);
                        }
                    } else {
                        log.warn("Exception connecting", future.getException());
                    }
                }

            });
            future.awaitUninterruptibly();
        }
        Request bindingRequest = MessageFactory.createBindingRequest();
        stunStack.sendRequest(bindingRequest, stunTransportAddress, localTransportAddress, responseCollector);
        // wait for its arrival with a timeout of 3s
        Response res = que.poll(3000L, TimeUnit.MILLISECONDS);
        if (res != null) {
            // in classic STUN, the response contains a MAPPED-ADDRESS
            MappedAddressAttribute maAtt = (MappedAddressAttribute) res.getAttribute(Attribute.Type.MAPPED_ADDRESS);
            if (maAtt != null) {
                publicIP = maAtt.getAddress().getHostString();
            }
            // in STUN bis, the response contains a XOR-MAPPED-ADDRESS
            XorMappedAddressAttribute xorAtt = (XorMappedAddressAttribute) res.getAttribute(Attribute.Type.XOR_MAPPED_ADDRESS);
            if (xorAtt != null) {
                byte xoring[] = new byte[16];
                System.arraycopy(Message.MAGIC_COOKIE, 0, xoring, 0, 4);
                System.arraycopy(res.getTransactionID(), 0, xoring, 4, 12);
                publicIP = xorAtt.applyXor(xoring).getHostString();
            }
        }
        // clean up
        if (iceSocket != null) {
            iceSocket.close();
        }
        stunStack.shutDown();
        log.info("Public IP: {}", publicIP);
        return publicIP;
    }

    public static String resolveLocalIP() {
        String ipAddress = null;
        try {
            // cheap and dirty way to get the preferred local IP
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.connect(InetAddress.getByName("8.8.8.8"), 53);
                ipAddress = socket.getLocalAddress().getHostAddress();
            } catch (Throwable t) {
                log.warn("Exception getting local address via dgram", t);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ce) {
                    }
                }
            }
            log.debug("Local address (detected): {}", ipAddress);
        } catch (Exception e) {
            log.warn("Exception getting local address", e);
        }
        return ipAddress;
    }

    /**
     * Resolves an IP with a given URL.
     *
     * @param url location of IP resolver service
     * @return IP address or null if some failure occurs
     */
    public static String resolveIPOverHTTP(String url) {
        String ipAddress = null;
        BufferedReader in = null;
        try {
            URL checkip = new URL(url);
            URLConnection con = checkip.openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(3000);
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            // read the first line and ensure that it starts with a number, if not we're most likely getting html
            ipAddress = in.readLine().trim();
            log.debug("Public address (detected): {}", ipAddress);
        } catch (FileNotFoundException fnfe) {
            // this will occur in a wavelength zone where carrier IP is enabled
            if (log.isDebugEnabled()) {
                log.warn("Host could not be reached, probably carrier IP enabled zone", fnfe);
            }
        } catch (Throwable t) {
            log.warn("Host could not be reached or timed-out", t);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.warn("Exception getting public IP", e);
                }
            }
        }
        return ipAddress;
    }

}
