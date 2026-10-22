package com.red5pro.ice.nio;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * Marks outgoing buffers so MINA's deferred datagram flush can reset them.
 * <p>
 * When the kernel send buffer is full, NioDatagramAcceptor queues the write and later calls {@link IoBuffer#reset()} on the buffer after
 * sending it. That requires a mark which nothing else sets, so the reset throws InvalidMarkException after the datagram was already sent,
 * the exception reaches {@link IceHandler#exceptionCaught(IoSession, Throwable)} and a healthy session is closed. Marking the buffer
 * before it enters the chain makes the reset valid.
 */
public class WriteMarkFilter extends IoFilterAdapter {

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        Object message = writeRequest.getMessage();
        if (message instanceof IoBuffer) {
            ((IoBuffer) message).mark();
        }
        nextFilter.filterWrite(session, writeRequest);
    }

}
