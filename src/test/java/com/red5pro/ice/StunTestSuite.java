/* See LICENSE.md for license information */
package com.red5pro.ice;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Contains all tests in the ice4j project.
 *
 * @author Emil Ivov
 */
public class StunTestSuite extends TestCase {

    /**
     * Creates a new instance of the suite
     *
     * @param s test name
     */
    public StunTestSuite(String s) {
        super(s);
    }

    /**
     * Returns the suite of tests to run.
     * @return the suite of tests to run.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite();
        // RFC 5389 STUN attributes
        suite.addTestSuite(com.red5pro.ice.attribute.AddressAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.AttributeDecoderTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.ErrorCodeAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.UnknownAttributesAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.SoftwareAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.OptionalAttributeAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.UsernameAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.NonceAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.RealmAttributeTest.class);
        // RFC 8445 ICE attributes
        suite.addTestSuite(com.red5pro.ice.attribute.PriorityAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.UseCandidateAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.IceControlAttributeTest.class);
        // RFC 5389 security attributes
        suite.addTestSuite(com.red5pro.ice.attribute.FingerprintAttributeTest.class);
        // RFC 6062 TURN TCP extension
        suite.addTestSuite(com.red5pro.ice.attribute.ConnectionIdAttributeTest.class);
        // RFC 6156 TURN IPv6 extension
        suite.addTestSuite(com.red5pro.ice.attribute.RequestedAddressFamilyAttributeTest.class);
        // messages
        suite.addTestSuite(com.red5pro.ice.message.MessageFactoryTest.class);
        suite.addTestSuite(com.red5pro.ice.message.MessageTest.class);
        // transactions (simplified tests for transaction ID generation)
        suite.addTestSuite(com.red5pro.ice.TransactionSupportTests.class);
        // Note: ShallowStackTest and MessageEventDispatchingTest have been removed
        // as they relied on deprecated standalone StunStack usage. The core ICE
        // functionality is tested through RoleConflictResolutionTest which exercises
        // the full Agent-based architecture.
        return suite;
    }
}
