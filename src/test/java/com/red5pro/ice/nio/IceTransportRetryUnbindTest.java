package com.red5pro.ice.nio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.red5pro.ice.Agent;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.socket.IceSocketWrapper;

public class IceTransportRetryUnbindTest {

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    public void retryUnbindsAddressWithNoRegisteredSocket() throws Exception {
        IceUdpTransport transport = IceUdpTransport.getInstance(AcceptorStrategy.DiscretePerSession.toString());
        TransportAddress addr = new TransportAddress("127.0.0.1", freeUdpPort(), Transport.UDP);
        Long rsvp = transport.addBinding("no-socket", addr);
        assertNotNull(rsvp);
        assertTrue(IceTransport.isBound(addr.getPort()));

        assertEquals(1, transport.retryFailedUnbinds());

        assertFalse(IceTransport.isBound(addr.getPort()));
        assertTrue(transport.isUnbound());
    }

    @Test
    public void retryUnbindsAddressWhoseSocketIsAlreadyClosed() throws Exception {
        IceUdpTransport transport = IceUdpTransport.getInstance(AcceptorStrategy.DiscretePerSession.toString());
        TransportAddress addr = new TransportAddress("127.0.0.1", freeUdpPort(), Transport.UDP);
        IceSocketWrapper socket = IceSocketWrapper.build(addr, null);
        assertTrue(transport.registerStackAndSocket(null, socket));
        assertTrue(IceTransport.isBound(addr.getPort()));
        // simulate a close whose unbind failed: the wrapper is closed but the acceptor still holds the address
        socket.closed.set(true);

        assertEquals(1, transport.retryFailedUnbinds());

        assertFalse(IceTransport.isBound(addr.getPort()));
        assertTrue(transport.isUnbound());
    }

    @Test
    public void retryLeavesLiveSocketsAlone() throws Exception {
        IceUdpTransport transport = IceUdpTransport.getInstance(AcceptorStrategy.DiscretePerSession.toString());
        TransportAddress addr = new TransportAddress("127.0.0.1", freeUdpPort(), Transport.UDP);
        IceSocketWrapper socket = IceSocketWrapper.build(addr, null);
        assertTrue(transport.registerStackAndSocket(null, socket));

        assertEquals(0, transport.retryFailedUnbinds());

        assertTrue(IceTransport.isBound(addr.getPort()));
        socket.close();
    }

    @Test
    public void sweeperSurvivesDeadAgentLeftInRegistry() {
        Agent agent = new Agent();
        agent.free();
        // free() unregistered it; put it back so the sweeper sees mustBeDead() on a registered agent
        IceTransport.getIceHandler().registerAgent(agent);
        assertTrue(agent.mustBeDead());
        IceTransport.getIceHandler().run();
        assertFalse(IceHandler.getAgentIds().contains(agent.getId()));
    }

    @Test
    public void handlerTasksRunOnVirtualThreads() throws Exception {
        AtomicBoolean virtual = new AtomicBoolean();
        IceTransport.getIceHandler().submitTask(() -> virtual.set(Thread.currentThread().isVirtual())).get();
        assertTrue(virtual.get());
    }
}
