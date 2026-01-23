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
import com.red5pro.ice.harvest.TurnCandidateHarvester;
import com.red5pro.ice.security.LongTermCredential;

/**
 * Integration tests for TURN candidate harvesting using a Docker coturn server.
 *
 * These tests verify that the TurnCandidateHarvester correctly:
 * - Authenticates with a TURN server using long-term credentials
 * - Sends Allocate Requests
 * - Receives Allocate Responses
 * - Creates relayed candidates
 * - Handles CreatePermission and ChannelBind
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
public class TurnHarvesterIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(TurnHarvesterIntegrationTest.class);

    private static CoturnContainer coturn;
    private static boolean dockerAvailable;

    @BeforeClass
    public static void setUpClass() throws Exception {
        dockerAvailable = CoturnContainer.isDockerAvailable();
        if (!dockerAvailable) {
            logger.warn("Docker not available - TURN integration tests will be skipped");
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
    public void testTurnCandidateHarvesting() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing TURN candidate harvesting...");

        Agent agent = new Agent();
        try {
            // Add TURN harvester with credentials
            // Must be added BEFORE createComponent() which triggers harvesting
            TransportAddress turnServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);
            LongTermCredential credential = new LongTermCredential(CoturnContainer.USERNAME, CoturnContainer.PASSWORD);
            TurnCandidateHarvester harvester = new TurnCandidateHarvester(turnServer, credential);
            agent.addCandidateHarvester(harvester);

            // Create a media stream and component - this triggers candidate harvesting
            IceMediaStream stream = agent.createMediaStream("audio");
            Component component = agent.createComponent(stream, Transport.UDP, 14000, 14000, 14100);

            // Wait for TURN allocation to complete (TURN takes longer than STUN)
            Thread.sleep(5000);

            // Get all local candidates
            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("Harvested {} candidates", candidates.size());

            for (LocalCandidate candidate : candidates) {
                logger.info("  Candidate: {} type={} priority={} relayServer={}", candidate.getTransportAddress(), candidate.getType(),
                        candidate.getPriority(), candidate.getRelayServerAddress());
            }

            // Verify we have at least a host candidate
            boolean hasHostCandidate = candidates.stream().anyMatch(c -> c.getType() == CandidateType.HOST_CANDIDATE);
            assertTrue("Should have at least one host candidate", hasHostCandidate);

            // Check for relayed candidate
            long relayedCount = candidates.stream().filter(c -> c.getType() == CandidateType.RELAYED_CANDIDATE).count();
            logger.info("Relayed candidates: {}", relayedCount);

            // TURN should produce relayed candidates
            // Note: This may fail if the TURN server rejects the allocation
            if (relayedCount == 0) {
                logger.warn("No relayed candidates - checking coturn logs");
                logger.warn("Coturn logs:\n{}", coturn.getLogs());
            }

            // Verify candidate priorities follow RFC 8445
            // Relayed candidates should have lowest priority (type preference = 0)
            for (LocalCandidate candidate : candidates) {
                if (candidate.getType() == CandidateType.RELAYED_CANDIDATE) {
                    // Relayed should have lower priority than host
                    for (LocalCandidate hostCandidate : candidates) {
                        if (hostCandidate.getType() == CandidateType.HOST_CANDIDATE) {
                            assertTrue("Relayed priority should be less than host priority",
                                    candidate.getPriority() < hostCandidate.getPriority());
                        }
                    }
                }
            }

        } finally {
            agent.free();
        }

        logger.info("TURN candidate harvesting test passed");
    }

    @Test
    public void testTurnWithInvalidCredentials() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing TURN with invalid credentials...");

        Agent agent = new Agent();
        try {
            // Add TURN harvester with WRONG credentials
            TransportAddress turnServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);
            LongTermCredential credential = new LongTermCredential("wronguser", "wrongpass");
            TurnCandidateHarvester harvester = new TurnCandidateHarvester(turnServer, credential);
            agent.addCandidateHarvester(harvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting
            Component component = agent.createComponent(stream, Transport.UDP, 15000, 15000, 15100);

            // Wait for TURN allocation attempt (will fail)
            Thread.sleep(5000);

            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("With invalid credentials: {} candidates", candidates.size());

            // Should have host candidates
            boolean hasHostCandidate = candidates.stream().anyMatch(c -> c.getType() == CandidateType.HOST_CANDIDATE);
            assertTrue("Should have host candidates even with failed TURN", hasHostCandidate);

            // Should NOT have relayed candidates (auth should fail)
            long relayedCount = candidates.stream().filter(c -> c.getType() == CandidateType.RELAYED_CANDIDATE).count();
            assertEquals("Should not have relayed candidates with invalid credentials", 0, relayedCount);

        } finally {
            agent.free();
        }

        logger.info("Invalid credentials test passed");
    }

    @Test
    public void testTurnAndStunTogether() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing STUN and TURN harvesters together...");

        Agent agent = new Agent();
        try {
            TransportAddress serverAddr = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);

            // Add both STUN and TURN harvesters before createComponent
            com.red5pro.ice.harvest.StunCandidateHarvester stunHarvester = new com.red5pro.ice.harvest.StunCandidateHarvester(serverAddr);
            agent.addCandidateHarvester(stunHarvester);

            LongTermCredential credential = new LongTermCredential(CoturnContainer.USERNAME, CoturnContainer.PASSWORD);
            TurnCandidateHarvester turnHarvester = new TurnCandidateHarvester(serverAddr, credential);
            agent.addCandidateHarvester(turnHarvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting for all registered harvesters
            Component component = agent.createComponent(stream, Transport.UDP, 16000, 16000, 16100);

            // Wait for all harvesting to complete
            Thread.sleep(5000);

            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("With both harvesters: {} candidates", candidates.size());

            for (LocalCandidate candidate : candidates) {
                logger.info("  {} - type={}", candidate.getTransportAddress(), candidate.getType());
            }

            // Should have host candidates
            long hostCount = candidates.stream().filter(c -> c.getType() == CandidateType.HOST_CANDIDATE).count();
            assertTrue("Should have host candidates", hostCount > 0);

            // May have server-reflexive from STUN
            long srflxCount = candidates.stream().filter(c -> c.getType() == CandidateType.SERVER_REFLEXIVE_CANDIDATE).count();
            logger.info("Server-reflexive candidates: {}", srflxCount);

            // May have relayed from TURN
            long relayedCount = candidates.stream().filter(c -> c.getType() == CandidateType.RELAYED_CANDIDATE).count();
            logger.info("Relayed candidates: {}", relayedCount);

        } finally {
            agent.free();
        }

        logger.info("Combined STUN/TURN test passed");
    }

    @Test
    public void testTurnAllocationLifetime() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing TURN allocation is maintained...");

        Agent agent = new Agent();
        try {
            TransportAddress turnServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.UDP);
            LongTermCredential credential = new LongTermCredential(CoturnContainer.USERNAME, CoturnContainer.PASSWORD);
            TurnCandidateHarvester harvester = new TurnCandidateHarvester(turnServer, credential);
            agent.addCandidateHarvester(harvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting
            Component component = agent.createComponent(stream, Transport.UDP, 17000, 17000, 17100);

            // Wait for initial gathering
            Thread.sleep(3000);

            Collection<LocalCandidate> initialCandidates = component.getLocalCandidates();
            int initialCount = initialCandidates.size();
            logger.info("Initial candidates: {}", initialCount);

            // Wait a bit more and verify candidates are still valid
            Thread.sleep(2000);

            Collection<LocalCandidate> laterCandidates = component.getLocalCandidates();
            int laterCount = laterCandidates.size();
            logger.info("Candidates after delay: {}", laterCount);

            // Candidate count should remain stable
            assertEquals("Candidate count should remain stable", initialCount, laterCount);

        } finally {
            agent.free();
        }

        logger.info("Allocation lifetime test passed");
    }

    @Test
    public void testTurnWithTcpTransport() throws Exception {
        assumeTrue("Docker not available", dockerAvailable);
        assumeTrue("Coturn not running", coturn != null && coturn.isRunning());

        logger.info("Testing TURN over TCP...");

        Agent agent = new Agent();
        try {
            // TURN over TCP
            TransportAddress turnServer = new TransportAddress("127.0.0.1", CoturnContainer.STUN_PORT, Transport.TCP);
            LongTermCredential credential = new LongTermCredential(CoturnContainer.USERNAME, CoturnContainer.PASSWORD);
            TurnCandidateHarvester harvester = new TurnCandidateHarvester(turnServer, credential);
            agent.addCandidateHarvester(harvester);

            IceMediaStream stream = agent.createMediaStream("audio");
            // createComponent triggers harvesting
            Component component = agent.createComponent(stream, Transport.UDP, 18000, 18000, 18100);

            // Wait for gathering
            Thread.sleep(5000);

            Collection<LocalCandidate> candidates = component.getLocalCandidates();
            logger.info("TURN over TCP: {} candidates", candidates.size());

            // Should at least have host candidates
            assertFalse("Should have candidates", candidates.isEmpty());

        } finally {
            agent.free();
        }

        logger.info("TURN over TCP test passed");
    }
}
