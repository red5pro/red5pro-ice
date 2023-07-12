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
        // attributes
        suite.addTestSuite(com.red5pro.ice.attribute.AddressAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.XorOnlyTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.AttributeDecoderTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.ChangeRequestAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.ErrorCodeAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.UnknownAttributesAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.SoftwareAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.OptionalAttributeAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.ConnectionIdAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.RequestedAddressFamilyAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.UsernameAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.NonceAttributeTest.class);
        suite.addTestSuite(com.red5pro.ice.attribute.RealmAttributeTest.class);
        // messages
        suite.addTestSuite(com.red5pro.ice.message.MessageFactoryTest.class);
        suite.addTestSuite(com.red5pro.ice.message.MessageTest.class);
        // stack
        suite.addTestSuite(com.red5pro.ice.stack.ShallowStackTest.class);
        // event dispatching
        suite.addTestSuite(com.red5pro.ice.MessageEventDispatchingTest.class);
        // transactions
        suite.addTestSuite(com.red5pro.ice.TransactionSupportTests.class);
        // client
        //suite.addTestSuite(com.red5pro.ice.stunclient.StunAddressDiscovererTest.class);
        //suite.addTestSuite(com.red5pro.ice.stunclient.StunAddressDiscovererTest_v6.class);
        //suite.addTestSuite(com.red5pro.ice.stunclient.StunAddressDiscovererTest_v4v6.class);
        return suite;
    }
}
