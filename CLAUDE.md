# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

red5pro-ice is an ICE library for Red5 Pro Server, derived from ice4j with I/O via Apache Mina.

## Build Commands

```bash
mvn clean install                                    # Build (tests skipped by default)
mvn clean install -DskipTests=false                  # Build with tests
mvn test -DskipTests=false -Dtest=IntegrationTestSuite  # Integration tests (requires Docker)
mvn net.revelc.code.formatter:formatter-maven-plugin:format  # Format code
```

## Architecture

**Core ICE Flow:** Agent creation → Candidate harvesting → Connectivity checks (`ConnectivityCheckClient`/`Server`) → Pair validation → Nomination (`DefaultNominator`, controlling agent only) → CheckList COMPLETED

**Key Classes:**

- `Agent`: Main orchestrator. Key methods: `startConnectivityEstablishment()`, `setControlling()`, `nominate()`
- `ConnectivityCheckClient/Server`: STUN binding requests. Role conflict in `repairRoleConflict()`
- `DefaultNominator`: Nominates valid pairs. Strategy: `NOMINATE_HIGHEST_PRIO`
- `CheckList`: Pair states (FROZEN, WAITING, IN_PROGRESS, SUCCEEDED, FAILED)

**ICE Roles:** Controlling agent sends USE-CANDIDATE to nominate. Role conflict resolved via tie-breaker comparison.

**Candidate Types:** Host, ServerReflexive (STUN), PeerReflexive, Relayed (TURN)

## Debugging

Key log patterns: `setControlling:`, `Pair succeeded:`, `CheckList...COMPLETED`, `ICE state changed`, `Nomination confirmed`, `IceControlAttribute`

## Code Style

Red5Pro-formatter.xml, 4-space indent, 140 char width, LF endings, Java 11
