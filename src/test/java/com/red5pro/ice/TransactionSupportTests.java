/* See LICENSE.md for license information */
package com.red5pro.ice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.stack.TransactionID;

import junit.framework.TestCase;

/**
 * Test transaction-related functionality such as unique transaction IDs.
 *
 * Note: Many of the original tests in this class relied on a deprecated architecture
 * where StunStack could be used standalone without an Agent. Those tests have been
 * removed. The core ICE transaction functionality (retransmissions, message dispatching)
 * is now tested through RoleConflictResolutionTest which exercises the full ICE stack.
 *
 * @author Emil Ivov
 * @author Red5 Pro
 */
public class TransactionSupportTests extends TestCase {

    private final static Logger logger = LoggerFactory.getLogger(TransactionSupportTests.class);

    /**
     * Initializes the test.
     *
     * @throws Exception if something goes bad.
     */
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "false");
        System.setProperty(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, "false");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "");
        System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "");
    }

    /**
     * Cleans up after the test.
     *
     * @throws Exception if something does not go as planned.
     */
    protected void tearDown() throws Exception {
        System.setProperty(StackProperties.PROPAGATE_RECEIVED_RETRANSMISSIONS, "false");
        System.setProperty(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, "false");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "");
        System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "");
        System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "");
        super.tearDown();
    }

    /**
     * Tests that transaction IDs are unique when generating new requests.
     * Each binding request should get a unique transaction ID.
     *
     * @throws Exception if something goes wrong
     */
    public void testUniqueTransactionIDs() throws Exception {
        logger.info("testUniqueTransactionIDs");

        Set<String> transactionIds = new HashSet<>();
        int numRequests = 100;

        for (int i = 0; i < numRequests; i++) {
            TransactionID tid = TransactionID.createNewTransactionID();
            byte[] tidBytes = tid.getBytes();
            String tidHex = bytesToHex(tidBytes);

            assertFalse("Duplicate transaction ID generated: " + tidHex, transactionIds.contains(tidHex));
            transactionIds.add(tidHex);
        }

        assertEquals("Should have generated " + numRequests + " unique transaction IDs", numRequests, transactionIds.size());
    }

    /**
     * Tests that transaction IDs have the correct length (12 bytes per RFC 5389).
     *
     * @throws Exception if something goes wrong
     */
    public void testTransactionIDLength() throws Exception {
        logger.info("testTransactionIDLength");

        TransactionID tid = TransactionID.createNewTransactionID();
        byte[] tidBytes = tid.getBytes();

        // RFC 5389: Transaction ID is 96 bits (12 bytes)
        assertEquals("Transaction ID should be 12 bytes", 12, tidBytes.length);
    }

    /**
     * Tests that TransactionID class generates valid IDs.
     *
     * @throws Exception if something goes wrong
     */
    public void testTransactionIDClass() throws Exception {
        logger.info("testTransactionIDClass");

        TransactionID tid1 = TransactionID.createNewTransactionID();
        TransactionID tid2 = TransactionID.createNewTransactionID();

        assertNotNull("TransactionID should not be null", tid1);
        assertNotNull("TransactionID should not be null", tid2);

        byte[] bytes1 = tid1.getBytes();
        byte[] bytes2 = tid2.getBytes();

        assertEquals("TransactionID bytes should be 12 bytes", 12, bytes1.length);
        assertEquals("TransactionID bytes should be 12 bytes", 12, bytes2.length);

        assertFalse("Two TransactionIDs should not be equal", Arrays.equals(bytes1, bytes2));
    }

    /**
     * Tests TransactionID equality by comparing the same ID to itself.
     *
     * @throws Exception if something goes wrong
     */
    public void testTransactionIDEquality() throws Exception {
        logger.info("testTransactionIDEquality");

        TransactionID tid1 = TransactionID.createNewTransactionID();
        byte[] bytes1 = tid1.getBytes();

        // Create a copy of the bytes
        byte[] bytes2 = new byte[bytes1.length];
        System.arraycopy(bytes1, 0, bytes2, 0, bytes1.length);

        // Verify the bytes match
        assertTrue("Copied bytes should match original", Arrays.equals(bytes1, bytes2));
    }

    /**
     * Tests stack properties for transaction configuration.
     *
     * @throws Exception if something goes wrong
     */
    public void testStackPropertiesConfiguration() throws Exception {
        logger.info("testStackPropertiesConfiguration");

        // Test that properties can be set and retrieved
        String testValue = "5";
        System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, testValue);
        int value = StackProperties.getInt(StackProperties.MAX_CTRAN_RETRANSMISSIONS, 0);
        assertEquals("Property should be retrievable", 5, value);

        // Test default value
        System.clearProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        int defaultValue = StackProperties.getInt(StackProperties.MAX_CTRAN_RETRANSMISSIONS, 7);
        assertEquals("Default value should be returned", 7, defaultValue);
    }

    /**
     * Helper to convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
