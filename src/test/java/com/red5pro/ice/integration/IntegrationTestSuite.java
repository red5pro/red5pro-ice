/* See LICENSE.md for license information */
package com.red5pro.ice.integration;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Integration test suite for STUN/TURN harvesting against a real coturn server.
 *
 * These tests require Docker to be installed and running. Tests will be skipped
 * automatically if Docker is not available.
 *
 * Run with: mvn test -DskipTests=false -Dtest=IntegrationTestSuite
 *
 * @author Red5 Pro
 */
@RunWith(Suite.class)
@SuiteClasses({ StunHarvesterIntegrationTest.class, TurnHarvesterIntegrationTest.class })
public class IntegrationTestSuite {
    // Suite configuration only - no test methods needed
}
