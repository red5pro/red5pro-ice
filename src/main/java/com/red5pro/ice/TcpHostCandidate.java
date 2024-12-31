/* See LICENSE.md for license information */
package com.red5pro.ice;

import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;

import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;

/**
 * Extends {@link com.red5pro.ice.HostCandidate} allowing the instance to have
 * a list of Sockets instead of just one socket. This is needed,
 * because with TCP, connections from different remote addresses result in
 * different Socket instances.
 *
 * @author Boris Grozev
 */
public class TcpHostCandidate extends HostCandidate {
    /**
     * List of accepted sockets for this TcpHostCandidate.
     */
    private final List<IceSocketWrapper> sockets = new LinkedList<>();

    /**
     * Initializes a new TcpHostCandidate.
     *
     * @param transportAddress the transport address of this
     * TcpHostCandidate.
     * @param parentComponent the Component that this candidate
     * belongs to.
     */
    public TcpHostCandidate(TransportAddress transportAddress, Component parentComponent) {
        super(transportAddress, parentComponent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IceSocketWrapper getCandidateIceSocketWrapper(SocketAddress remoteAddress) {
        for (IceSocketWrapper socket : sockets) {
            if (socket.getRemoteTransportAddress().equals(remoteAddress)) {
                return socket;
            }
        }
        return null;
    }

    public void addSocket(IceSocketWrapper socket) {
        sockets.add(socket);
    }

    @Override
    protected void free() {
        StunStack stunStack = getStunStack();
        TransportAddress localAddr = getTransportAddress();
        for (IceSocketWrapper socket : sockets) {
            // get the transport / acceptor id
            String id = (String) socket.getTransportId();
            // remove our sockets from the stack
            stunStack.removeSocket(id, localAddr, socket.getRemoteTransportAddress());
            socket.close();
        }
        super.free();
    }

}
