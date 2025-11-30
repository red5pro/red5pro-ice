# RFC Compliance Analysis for red5pro-ice

Based on a comprehensive review of the red5pro-ice codebase against RFC 8445 (ICE) and RFC 5389/8489 (STUN), this document identifies potential issues and deviations from the specifications.

## 1. Candidate Priority Calculation (RFC 8445 Section 5.1.2)

### Issue: Type Preference Values Deviate from RFC Recommendations

Location: [Candidate.java:404-428](../src/main/java/com/red5pro/ice/Candidate.java#L404-L428)

The RFC recommends:
- Host candidates: **126** (highest)
- Peer-reflexive: **110**
- Server-reflexive: **100**
- Relayed: **0** (lowest)

The implementation uses:
- Host: **40** (low)
- Peer-reflexive: **110**
- Server-reflexive: **100**
- Relayed: **126** (highest!)

This is inverted from the RFC. The comment at line 406 references RFC 5245 but misinterprets the recommendation. The RFC says relayed candidates are recommended as **default** candidates for fallback, not that they should have the highest priority. This could cause the ICE agent to prefer relayed connections over direct host connections, resulting in suboptimal path selection and unnecessary TURN server load.

### Non-Standard Extension: Priority Modifiers (Documented)

Location: [Candidate.java:373-389](../src/main/java/com/red5pro/ice/Candidate.java#L373-L389)

The code adds transport-based priority modifiers (`udpPriorityModifier`, `tcpPriorityModifier`) to the computed priority. This is a **Red5 Pro extension** and is NOT part of RFC 8445.

**Status:** Documented with warnings in:

- [StackProperties.java](../src/main/java/com/red5pro/ice/StackProperties.java) - `UDP_PRIORITY_MODIFIER` and `TCP_PRIORITY_MODIFIER` constants
- [Agent.java](../src/main/java/com/red5pro/ice/Agent.java) - `getUdpPriorityModifier()` and `getTcpPriorityModifier()` methods
- [Candidate.java](../src/main/java/com/red5pro/ice/Candidate.java) - `computePriorityForType()` method

**Behavior:**

- Default value: **0** (RFC-compliant behavior)
- When set to non-zero values, may cause:
  - Priority calculation deviations from RFC formula
  - Interoperability issues with strict RFC implementations

**Use Cases:**

- Force preference for UDP over TCP in mixed environments
- Force preference for TCP over UDP in firewall-heavy environments
- Work around network configurations that favor one transport

---

## 2. Local Preference for IPv6 (RFC 8445 Section 5.1.2.1)

### Issue: IPv6 Preference Logic is Backwards

Location: [Candidate.java:453-465](../src/main/java/com/red5pro/ice/Candidate.java#L453-L465)

The RFC and RFC 3484 recommend IPv6 addresses have **higher** precedence than IPv4. The implementation returns:
- IPv6 link-local: **30**
- IPv6 global: **40**
- IPv4: **10**

While IPv6 global gets a higher value than IPv4, link-local IPv6 gets a **lower** value (30) than IPv4 (10 would be lower, but the issue is the small differential). More importantly, these values are very low compared to the maximum (65535), reducing their impact on final priority.

---

## 3. Role Conflict Handling (RFC 8445 Section 7.3)

### Observation: Implemented Correctly

Location: [ConnectivityCheckServer.java:186-226](../src/main/java/com/red5pro/ice/ConnectivityCheckServer.java#L186-L226)

The role conflict resolution using tie-breaker comparison and 487 error code appears compliant with RFC 8445 Section 7.3.1.1.

---

## 4. Nomination Strategy (RFC 8445 Section 8)

### Issue: Default Strategy Doesn't Follow RFC "Regular" Nomination

Location: [DefaultNominator.java:31](../src/main/java/com/red5pro/ice/DefaultNominator.java#L31)

The default is `NOMINATE_FIRST_VALID`, which is essentially **aggressive nomination** (deprecated in RFC 8445). RFC 8445 recommends "regular nomination" where the controlling agent waits for connectivity checks to complete before nominating. The `NOMINATE_HIGHEST_PRIO` strategy is closer to RFC compliance.

### Issue: Controlled Agent Nomination Bypass

Location: [DefaultNominator.java:71-75](../src/main/java/com/red5pro/ice/DefaultNominator.java#L71-L75)

```java
if (!parentAgent.isControlling() && parentAgent.isTrickling()) {
    return;
}
```

The controlled agent bypass only applies when trickling is enabled. Per RFC 8445, controlled agents should **never** nominate, regardless of trickle mode.

---

## 5. Retransmission Timer (RFC 8445 Section 14.3, RFC 5389 Section 7.2.1)

### Issue: Hardcoded Ta Value

Location: [Agent.java:1855-1859](../src/main/java/com/red5pro/ice/Agent.java#L1855-L1859)

The pacing interval Ta is hardcoded to return **20ms** always. The RFC formula involves calculating based on the number of candidate pairs, which is noted but then ignored.

### Issue: RTO Calculation Simplified

Location: [Agent.java:1882-1886](../src/main/java/com/red5pro/ice/Agent.java#L1882-L1886)

The RTO always returns **100ms** with a comment acknowledging this doesn't follow the RFC formula. The RFC requires:
```
RTO = MAX(100ms, Ta * N * (Num-Waiting + Num-In-Progress))
```

---

## 6. STUN Transaction Retransmission (RFC 5389 Section 7.2.1)

### Potential Concern: Default Values

Location: [StunClientTransaction.java:48-59](../src/main/java/com/red5pro/ice/stack/StunClientTransaction.java#L48-L59)

Defaults:
- `DEFAULT_MAX_RETRANSMISSIONS = 6`
- `DEFAULT_MAX_WAIT_INTERVAL = 1600ms`
- `DEFAULT_ORIGINAL_WAIT_INTERVAL = 100ms`

RFC 5389 specifies 7 retransmissions (Rc=7) for UDP with RTO starting at 500ms (not 100ms) for general STUN. However, RFC 8445 allows connectivity checks to use different values. The values here appear reasonable for ICE but differ from base STUN.

---

## 7. Symmetric Response Check (RFC 8445 Section 7.2.5.2.1)

### Observation: Correctly Implemented

Location: [ConnectivityCheckClient.java:509-524](../src/main/java/com/red5pro/ice/ConnectivityCheckClient.java#L509-L524)

The `checkSymmetricAddresses` method properly validates that request source/destination matches response destination/source.

---

## 8. Pair Priority Calculation (RFC 8445 Section 6.1.2.3)

### Observation: Correctly Implemented

Location: [CandidatePair.java:276-301](../src/main/java/com/red5pro/ice/CandidatePair.java#L276-L301)

The formula `2^32 * MIN(G,D) + 2 * MAX(G,D) + (G>D?1:0)` matches RFC 8445.

---

## 9. ICE-LITE Support (RFC 8445 Section 2.5)

### Status: Implemented

ICE-LITE support has been added to the library. ICE-LITE is a simplified ICE implementation suitable for servers with public IP addresses that don't need NAT traversal.

**Implementation locations:**

- [StackProperties.java](../src/main/java/com/red5pro/ice/StackProperties.java) - `ICE_LITE` configuration property
- [Agent.java](../src/main/java/com/red5pro/ice/Agent.java) - `isIceLite()`, `setIceLite()` methods and role enforcement
- [ConnectivityCheckClient.java](../src/main/java/com/red5pro/ice/ConnectivityCheckClient.java) - Skips initiating checks for ICE-LITE agents
- [ConnectivityCheckServer.java](../src/main/java/com/red5pro/ice/ConnectivityCheckServer.java) - Role conflict handling for ICE-LITE
- [DefaultNominator.java](../src/main/java/com/red5pro/ice/DefaultNominator.java) - ICE-LITE agents don't nominate

**ICE-LITE agent behavior per RFC 8445:**

- MUST always be in the controlled role (never controlling)
- Only gathers host candidates (no STUN/TURN server-reflexive or relayed candidates)
- Does not initiate connectivity checks (only responds to incoming checks)
- Accepts nominations from the full ICE (controlling) agent

**Usage:**

```java
Agent agent = new Agent();
agent.setIceLite(true);  // Enable ICE-LITE mode
// Agent is now in controlled role and won't initiate checks
```

**Configuration:**

Set via system property: `com.red5pro.ice.ICE_LITE=true`

---

## 10. FINGERPRINT Attribute (RFC 5389 Section 15.5)

### Observation: Correctly Implemented

Location: [Agent.java:332-333](../src/main/java/com/red5pro/ice/Agent.java#L332-L333)

The `ALWAYS_SIGN` property enables FINGERPRINT on all messages, which is required for ICE per RFC 8445.

---

## Summary of Priority Issues

| Issue | Severity | Location |
|-------|----------|----------|
| Inverted type preference (relayed=126, host=40) | **High** | Candidate.java:411-426 |
| Default nomination strategy is aggressive | Medium | DefaultNominator.java:31 |
| Controlled agent can nominate when not trickling | Medium | DefaultNominator.java:71-75 |
| Hardcoded Ta=20ms, RTO=100ms | Low | Agent.java:1859, 1886 |
| Non-standard priority modifiers | Low | Candidate.java:373-389 |

The **most critical issue** is the inverted type preference which could cause ICE to prefer relayed paths over direct connections, increasing latency and TURN server costs.

---

## References

- RFC 8445: Interactive Connectivity Establishment (ICE)
- RFC 5389: Session Traversal Utilities for NAT (STUN)
- RFC 8489: Session Traversal Utilities for NAT (STUN) - updated
- RFC 3484: Default Address Selection for IPv6
