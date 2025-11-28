# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

red5pro-ice is an ICE (Interactive Connectivity Establishment) library for Red5 Pro Server. Originally derived from ice4j, it has been heavily reimagined with I/O via Apache Mina and mDNS support via jmdns.

## Build Commands

```bash
# Build (default goal: clean install)
mvn clean install

# Build without running tests (tests are skipped by default)
mvn clean install -DskipTests=true

# Build and run tests
mvn clean install -DskipTests=false

# Run tests only
mvn test -DskipTests=false

# Format code (uses Red5Pro-formatter.xml)
mvn net.revelc.code.formatter:formatter-maven-plugin:format
```

## Testing

Tests use JUnit 4 and are organized as a test suite in `StunTestSuite.java`. The surefire plugin is configured to run only `**/*TestSuite.*` patterns.

```bash
# Run all tests via the test suite
mvn test -DskipTests=false

# Run a specific test class
mvn test -DskipTests=false -Dtest=com.red5pro.ice.attribute.AddressAttributeTest
```

## Architecture

### Core Components

- **Agent** (`com.red5pro.ice.Agent`): Main orchestrator for ICE implementation. Manages media streams, candidate harvesting, and connectivity checks. Each Agent has a unique ID, ufrag/password for authentication, and controls the ICE negotiation process.

- **StunStack** (`com.red5pro.ice.stack.StunStack`): Entry point to the STUN protocol stack. Handles message processing, client/server transactions, and credential management.

- **Component** (`com.red5pro.ice.Component`): Represents a piece of a media stream requiring a single transport address (e.g., RTP component ID=1, RTCP component ID=2).

- **IceMediaStream** (`com.red5pro.ice.IceMediaStream`): Container for components of a media stream.

### Transport Layer (NIO)

- **IceTransport** (`com.red5pro.ice.nio.IceTransport`): Abstract parent for UDP/TCP transports. Manages Mina acceptors and socket bindings.
- **IceUdpTransport** / **IceTcpTransport**: Protocol-specific transport implementations.
- **IceHandler** (`com.red5pro.ice.nio.IceHandler`): Mina IoHandler for ICE connections.
- **IceEncoder/IceDecoder**: Protocol codec filters for STUN message encoding/decoding.

### Candidate Types

- **LocalCandidate** / **RemoteCandidate**: Base candidate classes
- **HostCandidate**: Local interface addresses
- **ServerReflexiveCandidate**: Addresses discovered via STUN
- **PeerReflexiveCandidate**: Addresses discovered during connectivity checks
- **RelayedCandidate**: TURN relay addresses

### Harvesting

- **HostCandidateHarvester**: Gathers local interface candidates
- **StunCandidateHarvester**: Discovers server-reflexive candidates via STUN
- **TurnCandidateHarvester**: Obtains relayed candidates via TURN
- **MappingCandidateHarvester**: For NAT mapping scenarios (AWS, etc.)

### Message Protocol

- **Message** (`com.red5pro.ice.message.Message`): Base STUN message with attributes
- **Request/Response/Indication**: Message subtypes
- **Attribute classes** (`com.red5pro.ice.attribute.*`): Various STUN attributes (MappedAddress, Username, MessageIntegrity, etc.)

## Key Configuration

Stack properties are configured via `StackProperties` class. Common properties:
- `SO_TIMEOUT`: Connection idle timeout (default: 120s)
- `SO_RCVBUF` / `SO_SNDBUF`: Socket buffer sizes (default: 65535)
- `ACCEPTOR_TIMEOUT`: Binding timeout (default: 2s)
- `TERMINATION_DELAY`: Delay after connectivity checks complete (default: 3000ms)

## Code Style

- Uses `Red5Pro-formatter.xml` Eclipse formatter profile
- 4-space indentation (spaces, not tabs)
- Line width: 140 characters
- LF line endings enforced
- Java 11 target
