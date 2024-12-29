package com.red5pro.ice.stack;

import com.red5pro.ice.nio.AcceptorStrategy;

public class StackPropertyOverrides {

    /**
     *   Null, or override default strategy.
     */
    public AcceptorStrategy strategy = null;

    /**
     * Null, or override default. Whether or not to prevent the use of IPv6 addresses.
     */
    public Boolean useIPv6 = null;

    /**
     * Null, or override default. Whether or not to use all available IP versions.
     */
    public Boolean useAllBinding = null;

    /**
     * Call this prior to creating the agent to set optional overrides.
     * Must be called with the same java thread that creates the agent.
     * @param overrides
     */
    public static void forNextAgent(StackPropertyOverrides overrides) {
        localOverrides.set(overrides);
    }

    /**
     * StunStack calls this during initiation.
     * @return
     */
    static StackPropertyOverrides getOverrides() {
        StackPropertyOverrides props = localOverrides.get();
        localOverrides.set(null);
        return props;
    }

    private static ThreadLocal<StackPropertyOverrides> localOverrides = new ThreadLocal<>();
}
