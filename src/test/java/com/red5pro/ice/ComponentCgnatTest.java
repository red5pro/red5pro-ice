/* See LICENSE.md for license information */
package com.red5pro.ice;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.net.InetAddress;

import org.junit.Test;

public class ComponentCgnatTest {

    @Test
    public void testIsCgnatAddress() throws Exception {
        Method isCgnat = Component.class.getDeclaredMethod("isCgnatAddress", InetAddress.class);
        isCgnat.setAccessible(true);

        assertTrue("100.64.0.1 should be CGNAT", (boolean) isCgnat.invoke(null, InetAddress.getByName("100.64.0.1")));
        assertTrue("100.127.255.255 should be CGNAT", (boolean) isCgnat.invoke(null, InetAddress.getByName("100.127.255.255")));
        assertFalse("100.63.255.255 should not be CGNAT", (boolean) isCgnat.invoke(null, InetAddress.getByName("100.63.255.255")));
        assertFalse("100.128.0.1 should not be CGNAT", (boolean) isCgnat.invoke(null, InetAddress.getByName("100.128.0.1")));
    }

    @Test
    public void testIsNonPublicAddressIncludesCgnat() throws Exception {
        Method isNonPublic = Component.class.getDeclaredMethod("isNonPublicAddress", InetAddress.class);
        isNonPublic.setAccessible(true);

        assertTrue("CGNAT should be non-public", (boolean) isNonPublic.invoke(null, InetAddress.getByName("100.64.0.10")));
    }
}
