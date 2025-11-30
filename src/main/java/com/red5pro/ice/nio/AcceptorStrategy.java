package com.red5pro.ice.nio;

/**
 * IceTransport creation policy. For each Transport type utilized(TCP and UDP), this enum represents the three basic ways to manage IceTransports for users.
 * Create an IceTransport for every IceSocketWrapper. Create a single IceTransport for each end user.
 * Create one IceTransport for all users.
 *
 * @author Andy Shaules
 */
public enum AcceptorStrategy {

    DiscretePerSocket, // One acceptor per Socket Wrapper
    DiscretePerSession, // One acceptor per user session
    Shared; // One shared Acceptor per Transport type udp or tcp. Same as sharedAcceptor = true

    /** Cached results of call values() to avoid repeated array.clone allocations */
    private static AcceptorStrategy[] cachedvalues = values();

    public static AcceptorStrategy valueOf(int ordinal) {
        if (ordinal < 0 || ordinal >= cachedvalues.length) {
            return DiscretePerSocket;
        }
        return cachedvalues[ordinal];
    }

    public static boolean isShared(String id) {
        return Shared.toString().equals(id);
    }

    public static boolean isNew(String id) {
        return DiscretePerSocket.toString().equals(id) || DiscretePerSession.toString().equals(id);
    }
}
