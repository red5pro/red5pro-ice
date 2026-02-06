# CGNAT and ICE Traversal (Reference + Implementation Notes)

## Core CGNAT RFCs

- RFC 6888: Common Requirements for Carrier-Grade NATs (CGNs)
- RFC 6598: IANA-Reserved IPv4 Prefix for CGN (`100.64.0.0/10`)
- RFC 6264: Incremental CGN for IPv6 transition

## ICE and NAT Traversal

- RFC 8445: ICE (obsoletes RFC 5245)
- RFC 5389: STUN
- RFC 5766: TURN (relay fallback when STUN/ICE fail behind symmetric NATs)

## Related Standards

- RFC 6887: Port Control Protocol (PCP)
- RFC 6877: 464XLAT
- RFC 7597: MAP-E
- RFC 7599: MAP-T

## Notes for This Library

- CGNAT address space is `100.64.0.0/10` (RFC 6598).
- CGNAT ranges are treated as non-public IPs for filtering when `SKIP_REMOTE_NON_PUBLIC_HOSTS=true`.
- You can also explicitly filter only CGNAT ranges by setting `SKIP_REMOTE_CGNAT=true`.

## Implementation Support Plan

1. **Address classification**
   - Ensure CGNAT range detection (`100.64.0.0/10`) is explicit and reusable.
2. **Configuration**
   - Add a dedicated `SKIP_REMOTE_CGNAT` flag for users who want to filter only CGNAT ranges.
3. **Candidate filtering**
   - Apply CGNAT filtering to remote candidates (both initial and trickle updates).
4. **Tests**
   - Add unit tests for CGNAT range classification and filtering behavior.

