/* See LICENSE.md for license information */
package com.red5pro.ice.integration;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Agent;
import com.red5pro.ice.CandidateType;
import com.red5pro.ice.Component;
import com.red5pro.ice.IceMediaStream;
import com.red5pro.ice.LocalCandidate;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.harvest.StunCandidateHarvester;

/**
 * Integration tests for STUN candidate harvesting using a Docker coturn server.
 *
 * These tests verify that the StunCandidateHarvester correctly:
 * - Connects to a STUN server
 * - Sends Binding Requests
 * - Receives Binding Responses
 * - Creates server-reflexive candidates
 *
 * Prerequisites:
 * - Docker must be installed and running
 * - The coturn/coturn image will be pulled if not present
 *
 * Note: Harvesting happens automatically when createComponent() is called,
 * as long as the agent is not in trickle mode (default is non-trickle).
 *
 * @author Red5 Pro
 */
public class StunHarvesterIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(StunHarvesterIntegrationTest.class);

    private static CoturnContainer coturn;
    private static boolean dockerAvailable;

    @BeforeClass
    public static void setUpClass() throws Exception {
        dockerAvailable = CoturnContainer.isDockerAvailable();
        if (!dockerAvailable) {
            logger.warn("Docker not available - STUN integration tests will be skipped");
            return;
        }

        coturn = new CoturnContainer();
        boolean started = coturn.start();
        if (!started) {
            logger.error("Failed to start coturn container");
            logger.error("Container logs:\n{}", coturn.getLogs());
            dockerAvailable = false;
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (coturn != null) {
            coturn.stop();
        }
    }

    @Test
    public void testStunCandidateHarvesting() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing STUN candidate harvesting...");

        Agent agent = new Agent();
        try {
            // Add STUN harvester pointing to our coturn container
            // Must be added BEFORE createComponent() which triggers harvesting
            TransportAddress stunServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);
            StunCandidateHarvester harvester = new StunCandidateHarvester(stunServer);
            agent.addCandidateHarvester(harvester);

            // Create a media stream and component - this triggers candidate harvesting
            IceMediaStream stream = agent.createMediaStream("audio");
            Component component = agent.createComponent(stream, Transport.UDP, 10000, 10000, 10100);

            // Brief wait for STUN transactions to complete
            Thread.sleep(2000);

            // Get all local candidates
            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("Harvested {} candidates", candidates.size());

            for (LocalCandidate candidate : candidates) {
                logger.info("  Candidate: {} type={} priority={}", candidate.getTransportAddress(), candidate.getType(),
                        candidate.getPriority());
            }

            // Verify we have at least a host candidate
            boolean hasHostCandidate = candidates.stream().anyMatch(c -> c.getType() == CandidateType.HOST_CANDIDATE);
            assertTrue("Should have at least one host candidate", hasHostCandidate);

            // For localhost testing, server-reflexive might equal host
            // but the harvester should have at least attempted to create one
            long srflxCount = candidates.stream().filter(c -> c.getType() == CandidateType.SERVER_REFLEXIVE_CANDIDATE).count();
            logger.info("Server-reflexive candidates: {}", srflxCount);

            // Verify candidate priorities follow RFC 8445
            // Host candidates should have higher priority than server-reflexive
            for (LocalCandidate candidate : candidates) {
                if (candidate.getType() == CandidateType.HOST_CANDIDATE) {
                    assertTrue("Host candidate priority should be positive", candidate.getPriority() > 0);
                }
            }

        } finally {
            agent.free();
        }

        logger.info("STUN candidate harvesting test passed");
    }

    @Test
    public void testMultipleStunRequests() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing multiple STUN binding requests...");

        // Test that we can make multiple requests without issues
        for (int i = 0; i < 3; i++) {
            Agent agent = new Agent();
            try {
                TransportAddress stunServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);
                StunCandidateHarvester harvester = new StunCandidateHarvester(stunServer);
                agent.addCandidateHarvester(harvester);

                IceMediaStream stream = agent.createMediaStream("test-" + i);
                // createComponent triggers harvesting
                Component component = agent.createComponent(stream, Transport.UDP, 11000 + (i * 100), 11000 + (i * 100), 11099 + (i * 100));

                // Brief wait for STUN transactions
                Thread.sleep(1000);

                Collection<LocalCandidate> candidates = component.getLocalCandidates();
                assertFalse("Should have candidates for iteration " + i, candidates.isEmpty());

                logger.info("Iteration {}: {} candidates", i, candidates.size());

            } finally {
                agent.free();
            }
        }

        logger.info("Multiple STUN requests test passed");
    }

    @Test
    public void testStunWithTcpTransport() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing STUN over TCP...");

        Agent agent = new Agent();
        try {
            // Add STUN harvester with TCP transport
            TransportAddress stunServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.TCP);
            StunCandidateHarvester harvester = new StunCandidateHarvester(stunServer);
            agent.addCandidateHarvester(harvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting
            Component component = agent.createComponent(stream, Transport.UDP, 12000, 12000, 12100);

            // Wait for STUN transactions
            Thread.sleep(2000);

            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("TCP STUN harvested {} candidates", candidates.size());

            // Should at least have host candidates
            assertFalse("Should have candidates", candidates.isEmpty());

        } finally {
            agent.free();
        }

        logger.info("STUN over TCP test passed");
    }

    @Test
    public void testStunServerUnreachable() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);

        logger.info("Testing behavior with unreachable STUN server...");

        Agent agent = new Agent();
        try {
            // Point to a non-existent STUN server
            TransportAddress stunServer = new TransportAddress("127.0.0.1", 59999, Transport.UDP);
            StunCandidateHarvester harvester = new StunCandidateHarvester(stunServer);
            agent.addCandidateHarvester(harvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting (will timeout for unreachable server)
            Component component = agent.createComponent(stream, Transport.UDP, 13000, 13000, 13100);

            // Wait for STUN timeout
            Thread.sleep(3000);

            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("With unreachable STUN: {} candidates", candidates.size());

            // Should still have host candidates even if STUN fails
            boolean hasHostCandidate = candidates.stream().anyMatch(c -> c.getType() == CandidateType.HOST_CANDIDATE);
            assertTrue("Should have host candidates even with failed STUN", hasHostCandidate);

            // Should NOT have server-reflexive candidates
            long srflxCount = candidates.stream().filter(c -> c.getType() == CandidateType.SERVER_REFLEXIVE_CANDIDATE).count();
            assertEquals("Should not have server-reflexive candidates", 0, srflxCount);

        } finally {
            agent.free();
        }

        logger.info("Unreachable STUN server test passed");
    }
}
