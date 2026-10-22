package com.red5pro.ice.nio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.junit.Test;

public class WriteMarkFilterTest {

    private final List<String> calls = new ArrayList<>();

    private NextFilter nextFilter() {
        return (NextFilter) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { NextFilter.class },
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return null;
                });
    }

    @Test
    public void markedBufferCanBeResetAfterSend() throws Exception {
        // the write request holds a duplicate of the buffer, which is the one MINA sends and resets
        WriteRequest req = new DefaultWriteRequest(IoBuffer.wrap(new byte[] { 1, 2, 3, 4 }));
        IoBuffer buf = (IoBuffer) req.getMessage();
        new WriteMarkFilter().filterWrite(nextFilter(), null, req);
        assertEquals(1, calls.size());
        assertEquals("filterWrite", calls.get(0));
        // simulate the datagram send consuming the buffer, then MINA's deferred flush reset
        buf.position(buf.limit());
        buf.reset();
        assertEquals(0, buf.position());
        assertEquals(4, buf.remaining());
    }

    @Test
    public void nonBufferMessagePassesThrough() throws Exception {
        WriteRequest req = new DefaultWriteRequest("not a buffer");
        new WriteMarkFilter().filterWrite(nextFilter(), null, req);
        assertEquals(1, calls.size());
        assertSame("not a buffer", req.getMessage());
    }

}
