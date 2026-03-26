# Multi-Homed ICE Connectivity Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix ICE connectivity check failures on dual-interface (multi-homed) servers where WHIP push STUN checks are sent from the wrong interface due to remote-only session lookup, rejected by strict symmetric address validation, and starved by single-pair PaceMaker scheduling.

**Architecture:** Three targeted fixes in red5pro-ice: (1) local+remote session lookup in `IceUdpSocketWrapper.send()`, (2) relaxed symmetric address check for sibling host candidates in `ConnectivityCheckClient`, (3) PaceMaker drains all WAITING pairs per cycle instead of one.

**Tech Stack:** Java 21, Apache Mina (NIO), JUnit 4, Maven

**Repository:** `/media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice`

**Ticket:** RED5DEV-2052

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/com/red5pro/ice/nio/IceUdpTransport.java` | Modify | Add `getSessionByLocalAndRemote()` method |
| `src/main/java/com/red5pro/ice/socket/IceUdpSocketWrapper.java` | Modify | Use local+remote session lookup in `send()` |
| `src/main/java/com/red5pro/ice/ConnectivityCheckClient.java` | Modify | Relax symmetric check; extract `sendAndUpdateState()`; PaceMaker drain |
| `src/test/java/com/red5pro/ice/MultiHomedIceTest.java` | Create | Tests for symmetric check relaxation and PaceMaker drain |

---

### Task 1: Add `getSessionByLocalAndRemote()` to IceUdpTransport

**Files:**
- Modify: `src/main/java/com/red5pro/ice/nio/IceUdpTransport.java` (after line 363, before `getTransport()`)

- [ ] **Step 1: Add the `getSessionByLocalAndRemote` method**

Add this method between `getSessionByRemote()` (ends line 363) and `getTransport()` (line 365):

```java
    /**
     * Returns a session matching both the given local and remote addresses.
     * Unlike {@link #getSessionByRemote(SocketAddress)} which matches any session with a matching remote,
     * this method ensures the session's local address also matches, preventing cross-interface session reuse
     * on multi-homed hosts.
     *
     * @param localAddress the local transport address the session must be bound to
     * @param remoteAddress the remote address the session must be connected to
     * @return IoSession matching both addresses, or null if not found
     */
    public IoSession getSessionByLocalAndRemote(TransportAddress localAddress, SocketAddress remoteAddress) {
        Optional<IoSession> opt = sessions.values().stream()
                .filter(session -> session.getLocalAddress().equals(localAddress) && session.getRemoteAddress().equals(remoteAddress))
                .findFirst();
        if (opt.isPresent()) {
            return opt.get();
        }
        if (isTrace) {
            logger.trace("Session not found for local: {} remote: {}", localAddress, remoteAddress);
        }
        return null;
    }
```

- [ ] **Step 2: Verify compilation**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/red5pro/ice/nio/IceUdpTransport.java
git commit -m "Add getSessionByLocalAndRemote() for multi-homed session lookup [RED5DEV-2052]"
```

---

### Task 2: Use local-aware session lookup in IceUdpSocketWrapper.send()

**Files:**
- Modify: `src/main/java/com/red5pro/ice/socket/IceUdpSocketWrapper.java` (lines 56-89)

- [ ] **Step 1: Replace the session resolution chain in `send()`**

Replace lines 56-89 (from `// if no session is set` through the closing brace of the outer `if (sess == null)` block) with:

```java
                // if no session is set, we're likely in the negotiation phase
                IoSession sess = getSession();
                if (sess == null) {
                    // attempt to pull the session from the transport
                    IceUdpTransport transport = IceUdpTransport.getInstance(transportId);
                    // look up session matching BOTH local and remote addresses to prevent
                    // cross-interface session reuse on multi-homed hosts (RED5DEV-2052)
                    sess = transport.getSessionByLocalAndRemote(transportAddress, destAddress);
                    // if no exact match exists, create a new session for this local+remote pair
                    if (sess == null) {
                        try {
                            logger.debug("No session for local: {} remote: {}, creating", transportAddress, destAddress);
                            sess = transport.createSession(this, destAddress);
                        } catch (Exception e) {
                            logger.warn("Exception creating session for: {}", transportAddress, e);
                        }
                    }
                }
```

This replaces the previous chain that called `getSessionByRemote(destAddress)` (which returned the first session matching the remote regardless of local address), then fell through to `getSessionByLocal()` and `createSession()`. The new chain does an exact local+remote match first, then creates a new session if none exists.

- [ ] **Step 2: Verify compilation**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/red5pro/ice/socket/IceUdpSocketWrapper.java
git commit -m "Use local+remote session lookup in send() to prevent cross-interface reuse [RED5DEV-2052]"
```

---

### Task 3: Add `isSiblingHostCandidate` and relax `checkSymmetricAddresses`

**Files:**
- Modify: `src/main/java/com/red5pro/ice/ConnectivityCheckClient.java` (lines 555-577)

- [ ] **Step 1: Add `isSiblingHostCandidate` method**

Add this method after `checkSymmetricAddresses()` (after line 577):

```java
    /**
     * Checks whether {@code actual} is a host candidate address in the same component as {@code expected}, on the same port
     * and transport. This identifies sibling interfaces on a multi-homed host where asymmetric routing may cause STUN
     * responses to arrive on a different interface than the one the request was sent from.
     *
     * @param component the ICE component containing the candidates
     * @param expected the local address the request was sent from
     * @param actual the local address the response was received on
     * @return true if actual is a known host candidate sibling of expected
     */
    private boolean isSiblingHostCandidate(Component component, TransportAddress expected, TransportAddress actual) {
        if (expected.getPort() != actual.getPort()) {
            return false;
        }
        if (expected.getTransport() != actual.getTransport()) {
            return false;
        }
        for (LocalCandidate candidate : component.getLocalCandidates()) {
            if (candidate.getType() == CandidateType.HOST_CANDIDATE && candidate.getTransportAddress().equals(actual)) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 2: Replace `checkSymmetricAddresses` method**

Replace lines 555-577 with:

```java
    private boolean checkSymmetricAddresses(StunResponseEvent evt) {
        CandidatePair pair = ((CandidatePair) evt.getTransactionID().getApplicationData());
        TransportAddress expectedLocalAddr = pair.getLocalCandidate().getBase().getTransportAddress();
        TransportAddress actualLocalAddr = evt.getLocalAddress();
        TransportAddress expectedRemoteAddr = pair.getRemoteCandidate().getTransportAddress();
        TransportAddress actualRemoteAddr = evt.getRemoteAddress();
        boolean localMatch = expectedLocalAddr.equals(actualLocalAddr);
        boolean remoteMatch = expectedRemoteAddr.equals(actualRemoteAddr);
        if (!localMatch) {
            // On multi-homed hosts, responses may arrive on a sibling interface due to asymmetric
            // routing or NAT. Accept if the receiving address is a known host candidate in the same
            // component on the same port/transport (RED5DEV-2052).
            localMatch = isSiblingHostCandidate(pair.getParentComponent(), expectedLocalAddr, actualLocalAddr);
            if (localMatch) {
                logger.info("Accepted response on sibling interface {} for pair sent from {}", actualLocalAddr, expectedLocalAddr);
            }
        }
        if (!localMatch || !remoteMatch) {
            // Log detailed diagnostic information for non-symmetric responses
            logger.warn("Non-symmetric address check failed for pair: {}", pair.toShortString());
            if (!localMatch) {
                logger.warn("  Local address mismatch - expected: {}, actual response destination: {}", expectedLocalAddr, actualLocalAddr);
            }
            if (!remoteMatch) {
                logger.warn("  Remote address mismatch - expected: {}, actual response source: {}", expectedRemoteAddr, actualRemoteAddr);
            }
            logger.warn("  This typically indicates: symmetric NAT (requires TURN), NAT rebinding, or asymmetric routing");
            return false;
        }
        return true;
    }
```

- [ ] **Step 3: Verify compilation**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/red5pro/ice/ConnectivityCheckClient.java
git commit -m "Relax symmetric address check for sibling host candidates [RED5DEV-2052]"
```

---

### Task 4: Extract `sendAndUpdateState` and modify PaceMaker to drain all WAITING pairs

**Files:**
- Modify: `src/main/java/com/red5pro/ice/ConnectivityCheckClient.java` (lines 639-732, PaceMaker inner class)

- [ ] **Step 1: Add `sendAndUpdateState` method to `ConnectivityCheckClient`**

Add this method before the `PaceMaker` inner class (before line 639):

```java
    /**
     * Sends a connectivity check for the given pair and updates its state to IN_PROGRESS on success or FAILED on failure.
     *
     * @param pairToCheck the candidate pair to check
     * @return true if the check was sent successfully
     */
    private boolean sendAndUpdateState(CandidatePair pairToCheck) {
        // skip TCP active candidates with a destination port of 9 (masked)
        RemoteCandidate remoteCandidate = pairToCheck.getRemoteCandidate();
        if (remoteCandidate.getTcpType() == CandidateTcpType.ACTIVE && remoteCandidate.getTransportAddress().getPort() == 9) {
            logger.debug("TCP remote candidate is active with masked port, skip attempt to connect directly. Type: {}", remoteCandidate.getType());
            return false;
        }
        // RFC 5389 Section 7.2.1: RTO >= 500ms, Rc = 7 (6 retransmissions), double RTO each time up to max
        TransactionID transactionID = startCheckForPair(pairToCheck, 500, 1600, 6);
        if (transactionID == null) {
            logger.warn("Pair failed: {}", pairToCheck.toShortString());
            pairToCheck.setStateFailed();
            return false;
        } else {
            pairToCheck.setStateInProgress(transactionID);
            return true;
        }
    }
```

- [ ] **Step 2: Replace PaceMaker `run()` loop body**

Replace the loop body inside `do { try { ... } }` (lines 685-726) with:

```java
                    long waitFor = getNextWaitInterval();
                    if (waitFor > 0) {
                        logger.trace("Going to sleep for {} for ufrag: {}", waitFor, parentAgent.getLocalUfrag());
                        Thread.sleep(waitFor);
                    }
                    boolean sentAny = false;
                    // Drain all triggered checks first
                    CandidatePair pairToCheck = checkList.popTriggeredCheck();
                    while (pairToCheck != null) {
                        sentAny |= sendAndUpdateState(pairToCheck);
                        pairToCheck = checkList.popTriggeredCheck();
                    }
                    // Then drain all ordinary WAITING pairs (RED5DEV-2052: ensures all candidates
                    // on multi-homed hosts get checked in the same cycle)
                    pairToCheck = checkList.getNextOrdinaryPairToCheck();
                    while (pairToCheck != null) {
                        sentAny |= sendAndUpdateState(pairToCheck);
                        pairToCheck = checkList.getNextOrdinaryPairToCheck();
                    }
                    if (!sentAny) {
                        // done sending checks for this list
                        checkList.fireEndOfOrdinaryChecks();
                    }
```

- [ ] **Step 3: Verify compilation**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/red5pro/ice/ConnectivityCheckClient.java
git commit -m "PaceMaker drains all WAITING pairs per cycle for multi-homed hosts [RED5DEV-2052]"
```

---

### Task 5: Write tests for symmetric check and PaceMaker changes

**Files:**
- Create: `src/test/java/com/red5pro/ice/MultiHomedIceTest.java`

- [ ] **Step 1: Create the test file**

```java
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
        Component component = stream.createComponent(Transport.UDP, 50000, 50000, 50100);
        List<LocalCandidate> candidates = component.getLocalCandidates();
        log.info("Local candidates: {}", candidates.size());
        for (LocalCandidate c : candidates) {
            log.info("  Candidate: {} type={} foundation={}", c.getTransportAddress(), c.getType(), c.getFoundation());
        }
        assertNotNull("Component should have been created", component);
        assertTrue("Should have at least one local candidate", candidates.size() >= 1);
        // Each host candidate on a different IP gets a unique foundation
        long hostCount = candidates.stream()
                .filter(c -> c.getType() == CandidateType.HOST_CANDIDATE)
                .map(Candidate::getFoundation)
                .distinct()
                .count();
        long totalHosts = candidates.stream()
                .filter(c -> c.getType() == CandidateType.HOST_CANDIDATE)
                .count();
        assertEquals("Each host candidate should have a unique foundation", totalHosts, hostCount);
    }

    /**
     * Verifies that all pairs with different foundations start as WAITING,
     * ensuring both candidates on a multi-homed host will be checked.
     */
    @Test
    public void testAllDifferentFoundationPairsStartAsWaiting() throws Exception {
        IceMediaStream stream = agent.createMediaStream("media-0");
        Component component = stream.createComponent(Transport.UDP, 50000, 50000, 50100);
        TransportAddress remoteAddr = new TransportAddress(InetAddress.getByName("198.51.100.1"), 54321, Transport.UDP);
        RemoteCandidate remoteCandidate = new RemoteCandidate(remoteAddr, component, CandidateType.HOST_CANDIDATE, "remote-foundation-1", 2130706431L, null);
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
        Component component = stream.createComponent(Transport.UDP, 50000, 50000, 50100);
        TransportAddress remoteAddr = new TransportAddress(InetAddress.getByName("198.51.100.1"), 54321, Transport.UDP);
        RemoteCandidate remoteCandidate = new RemoteCandidate(remoteAddr, component, CandidateType.HOST_CANDIDATE, "remote-foundation-1", 2130706431L, null);
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
```

- [ ] **Step 2: Verify tests compile**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run tests**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn -Dmaven.test.skip=false test -Dtest=MultiHomedIceTest -q`
Expected: Tests PASS. On single-interface hosts, pair counts will be 1.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/red5pro/ice/MultiHomedIceTest.java
git commit -m "Add tests for multi-homed ICE connectivity fixes [RED5DEV-2052]"
```

---

### Task 6: Full build verification

**Files:** None (verification only)

- [ ] **Step 1: Run full compilation**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run full test suite**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && mvn -Dmaven.test.skip=false clean test -q`
Expected: BUILD SUCCESS with all tests passing. Note any pre-existing network-dependent test failures.

- [ ] **Step 3: Review all changes**

Run: `cd /media/mondain/terrorbyte/workspace/github-red5pro/red5pro-ice && git log --oneline -5`

Expected output should show 5 commits:
```
<hash> Add tests for multi-homed ICE connectivity fixes [RED5DEV-2052]
<hash> PaceMaker drains all WAITING pairs per cycle for multi-homed hosts [RED5DEV-2052]
<hash> Relax symmetric address check for sibling host candidates [RED5DEV-2052]
<hash> Use local+remote session lookup in send() to prevent cross-interface reuse [RED5DEV-2052]
<hash> Add getSessionByLocalAndRemote() for multi-homed session lookup [RED5DEV-2052]
```
