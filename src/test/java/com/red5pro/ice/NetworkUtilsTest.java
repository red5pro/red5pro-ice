/* See LICENSE.md for license information */
package com.red5pro.ice;

import static org.junit.Assert.*;

import org.junit.*;

public class NetworkUtilsTest {
    @Test
    public void testIpv6StringToBytes() {
        byte[] addr = NetworkUtils.strToIPv6("::12");
        assertNotNull(addr);
        assertEquals(18, addr[15]);

        addr = NetworkUtils.strToIPv6("[::12]");
        assertNotNull(addr);
        assertEquals(18, addr[15]);

        addr = NetworkUtils.strToIPv6("::12%1");
        assertNotNull(addr);
        assertEquals(18, addr[15]);

        addr = NetworkUtils.strToIPv6("[::12%1]");
        assertNotNull(addr);
        assertEquals(18, addr[15]);

        assertNull(NetworkUtils.strToIPv6("[::12]%1"));
    }
}
