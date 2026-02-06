# CG-NAT (Carrier-Grade NAT) and ICE Traversal: Key RFCs and Specifications

The primary RFC specification defining the requirements for Carrier-Grade NAT (CGNAT or CGN) is RFC 6888: Common Requirements for Carrier-Grade NATs (CGNs). [1, 2]

Here are the key RFCs and specifications related to CGNAT and ICE (Interactive Connectivity Establishment) traversal: 
Core CGNAT RFCs

• RFC 6888 (Common Requirements for CGNs): Defines the requirements for CGNAT, including behavior, session logging, and port block allocation. 
• RFC 6598 (IANA-Reserved IPv4 Prefix for CGN): Reserves  for use in CGNAT deployments. 
• RFC 6264 (An Incremental Carrier-Grade NAT for IPv6 Transition): Describes the architecture for deploying CGN. [3, 4, 5, 6, 7]  

ICE and NAT Traversal
Because CGNAT often acts like a "Symmetric NAT" (endpoint-dependent mapping), traditional NAT traversal techniques like simple STUN often fail.

• RFC 5245 (ICE): Describes the Interactive Connectivity Establishment protocol used to find the best path between peers.
• RFC 8445 (ICE - Updated): Updates the ICE protocol.
• RFC 5389 (STUN): Session Traversal Utilities for NAT.
• RFC 5766 (TURN): Traversal Using Relays around NAT (used when ICE, STUN, and CGNAT hole punching fail). [8, 9]

Related Technologies and Standards

• RFC 6887 (Port Control Protocol - PCP): Used by clients to map ports on the CGNAT device.
• RFC 6877 (464XLAT): Combines NAT64 and CLAT to allow IPv4-only apps to work over IPv6-only networks.
• RFC 7597 (MAP-E): Mapping of Address and Port using Encapsulation.
• RFC 7599 (MAP-T): Mapping of Address and Port using Translation. [10, 11]  

Key Behaviors of CGNAT

• Address Space: Uses  (RFC 6598). 
• Mapping: Performs endpoint-dependent mapping (symmetric NAT) to maximize port usage.
• Sharing Ratio: A single public IP is often shared among 128 or more subscribers. [4, 8, 12]  

[1] https://www.cisco.com/c/en/us/support/docs/security/umbrella/225267-configure-cgnat-carrier-grade-nat-ips.epub
[2] https://www.rfc-editor.org/rfc/rfc6888.html
[3] https://www.cisco.com/c/en/us/support/docs/security/umbrella/225267-configure-cgnat-carrier-grade-nat-ips.html
[4] https://en.wikipedia.org/wiki/Carrier-grade_NAT
[5] https://datatracker.ietf.org/doc/html/rfc6888
[6] https://datatracker.ietf.org/doc/html/rfc6264
[7] https://datatracker.ietf.org/doc/rfc6888/
[8] https://dev.to/alakkadshaw/nat-traversal-how-it-works-4dnc
[9] https://datatracker.ietf.org/doc/html/rfc5245
[10] https://www.reddit.com/r/ipv6/comments/1cxiow4/in_practice_are_dedicated_cgnat/
[11] https://www.rfc-editor.org/rfc/rfc7269.txt
[12] https://www.youtube.com/watch?v=PeQ4Hkjd4iI
