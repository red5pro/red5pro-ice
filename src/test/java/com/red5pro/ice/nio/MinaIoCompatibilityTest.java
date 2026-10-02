package com.red5pro.ice.nio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.RequestListener;
import com.red5pro.ice.stack.StunStack;
import com.red5pro.ice.stack.TransactionID;

public class MinaIoCompatibilityTest {

    @Test
    public void udpAcceptorReceivesRequestAndReturnsResponse() throws Exception {
        verifyRequestAndResponse(Transport.UDP);
    }

    @Test
    public void tcpAcceptorReceivesRequestAndReturnsResponse() throws Exception {
        verifyRequestAndResponse(Transport.TCP);
    }

    private void verifyRequestAndResponse(Transport transport) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        TransportAddress localAddress = new TransportAddress(loopback.getHostAddress(), freePort(), transport);
        StunStack stack = new StunStack();
        IceSocketWrapper iceSocket = IceSocketWrapper.build(localAddress, null);
        AtomicReference<Exception> listenerFailure = new AtomicReference<>();
        stack.addSocket(iceSocket, null, true);
        stack.addRequestListener(new RequestListener() {
            @Override
            public void processRequest(StunMessageEvent event) {
                try {
                    Request request = (Request) event.getMessage();
                    Response response = MessageFactory.create3489BindingResponse(localAddress, null, null);
                    stack.sendResponse(request.getTransactionID(), response, event.getLocalAddress(), event.getRemoteAddress());
                } catch (Exception failure) {
                    listenerFailure.set(failure);
                }
            }
        });

        try {
            Request request = MessageFactory.createBindingRequest();
            request.setTransactionID(TransactionID.createNewTransactionID().getBytes());
            byte[] requestBytes = request.encode(stack);
            byte[] responseBytes = transport == Transport.UDP ? sendAndReceiveUdp(loopback, localAddress.getPort(), requestBytes)
                    : sendAndReceiveTcp(loopback, localAddress.getPort(), requestBytes);
            Response response = (Response) Response.decode(responseBytes, 0, responseBytes.length);

            assertEquals(Message.BINDING_SUCCESS_RESPONSE, response.getMessageType());
            assertArrayEquals(request.getTransactionID(), response.getTransactionID());
            assertNull(listenerFailure.get());
        } finally {
            stack.removeSocket(iceSocket.getTransportId(), localAddress);
            iceSocket.close();
            stack.shutDown();
        }
    }

    private byte[] sendAndReceiveUdp(InetAddress loopback, int port, byte[] requestBytes) throws Exception {
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(3000);
            client.send(new DatagramPacket(requestBytes, requestBytes.length, loopback, port));
            byte[] responseBytes = new byte[1024];
            DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
            client.receive(response);
            byte[] received = new byte[response.getLength()];
            System.arraycopy(response.getData(), response.getOffset(), received, 0, received.length);
            return received;
        }
    }

    private byte[] sendAndReceiveTcp(InetAddress loopback, int port, byte[] requestBytes) throws Exception {
        try (Socket client = new Socket(loopback, port)) {
            client.setSoTimeout(3000);
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            output.writeShort(requestBytes.length);
            output.write(requestBytes);
            output.flush();

            DataInputStream input = new DataInputStream(client.getInputStream());
            int responseLength = input.readUnsignedShort();
            byte[] responseBytes = input.readNBytes(responseLength);
            assertEquals(responseLength, responseBytes.length);
            return responseBytes;
        }
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
