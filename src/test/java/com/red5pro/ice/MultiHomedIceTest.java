/* See LICENSE.md for license information */
package com.red5pro.ice;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for multi-homed ICE fixes (RED5DEV-2052).
 * Validates that dual-interface servers can successfully run ICE connectivity checks
 * when candidates exist on multiple local addresses.
 */
public class MultiHomedIceTest {

    private static final Logger log = LoggerFactory.getLogger(MultiHomedIceTest.class);

    private Agent agent;

    @Before
    public void setUp() {
        agent = new Agent();
        agent.setControlling(true);
    }

    @After
    public void tearDown() {
        if (agent != null) {
            agent.free();
        }
    }

    /**
     * Verifies that host candidates on different interfaces have different foundations,
     * ensuring they will form independent pairs that are both set to WAITING.
     */
    @Test
    public void testSiblingHostCandidatesHaveDifferentFoundations() throws Exception {
        IceMediaStream stream = agent.createMediaStream("media-0");
        Component component = agent.createComponent(stream, Transport.UDP, 50000, 50000, 50100);
        List<LocalCandidate> candidates = component.getLocalCandidates();
        log.info("Local candidates: {}", candidates.size());
        for (LocalCandidate c : candidates) {
            log.info("  Candidate: {} type={} foundation={}", c.getTransportAddress(), c.getType(), c.getFoundation());
        }
        assertNotNull("Component should have been created", component);
        assertTrue("Should have at least one local candidate", candidates.size() >= 1);
        // Each host candidate on a different IP gets a unique foundation
        long hostCount = candidates.stream().filter(c -> c.getType() == CandidateType.HOST_CANDIDATE).map(Candidate::getFoundation)
                .distinct().count();
        long totalHosts = candidates.stream().filter(c -> c.getType() == CandidateType.HOST_CANDIDATE).count();
        assertEquals("Each host candidate should have a unique foundation", totalHosts, hostCount);
    }

    /**
     * Verifies that all pairs with different foundations start as WAITING,
     * ensuring both candidates on a multi-homed host will be checked.
     */
    @Test
    public void testAllDifferentFoundationPairsStartAsWaiting() throws Exception {
        IceMediaStream stream = agent.createMediaStream("media-0");
        Component component = agent.createComponent(stream, Transport.UDP, 50000, 50000, 50100);
        TransportAddress remoteAddr = new TransportAddress(InetAddress.getByName("198.51.100.1"), 54321, Transport.UDP);
        RemoteCandidate remoteCandidate = new RemoteCandidate(remoteAddr, component, CandidateType.HOST_CANDIDATE, "remote-foundation-1",
                2130706431L, null);
        component.addRemoteCandidate(remoteCandidate);
        stream.initCheckList();
        CheckList checkList = stream.getCheckList();
        log.info("CheckList size: {}", checkList.size());
        checkList.computeInitialCheckListPairStates();
        int waitingCount = 0;
        for (CandidatePair pair : checkList) {
            log.info("  Pair: {} state={} foundation={}", pair.toShortString(), pair.getState(), pair.getFoundation());
            if (pair.getState() == CandidatePairState.WAITING) {
                waitingCount++;
            }
        }
        log.info("Waiting pairs: {}", waitingCount);
        assertTrue("At least one pair should be WAITING", waitingCount >= 1);
        assertEquals("All pairs with unique foundations should be WAITING", checkList.size(), waitingCount);
    }

    /**
     * Verifies that getNextOrdinaryPairToCheck returns all WAITING pairs across
     * consecutive calls (draining behavior needed by the updated PaceMaker).
     */
    @Test
    public void testGetNextOrdinaryPairDrainsAllWaiting() throws Exception {
        IceMediaStream stream = agent.createMediaStream("media-0");
        Component component = agent.createComponent(stream, Transport.UDP, 50000, 50000, 50100);
        TransportAddress remoteAddr = new TransportAddress(InetAddress.getByName("198.51.100.1"), 54321, Transport.UDP);
        RemoteCandidate remoteCandidate = new RemoteCandidate(remoteAddr, component, CandidateType.HOST_CANDIDATE, "remote-foundation-1",
                2130706431L, null);
        component.addRemoteCandidate(remoteCandidate);
        stream.initCheckList();
        CheckList checkList = stream.getCheckList();
        checkList.computeInitialCheckListPairStates();
        int totalPairs = checkList.size();
        log.info("Total pairs in checklist: {}", totalPairs);
        // Drain all WAITING pairs, marking each as FAILED to simulate sendAndUpdateState
        int drained = 0;
        CandidatePair pair = checkList.getNextOrdinaryPairToCheck();
        while (pair != null) {
            log.info("  Drained pair: {} state={}", pair.toShortString(), pair.getState());
            pair.setStateFailed();
            drained++;
            pair = checkList.getNextOrdinaryPairToCheck();
        }
        assertEquals("Should drain all pairs", totalPairs, drained);
    }
}
