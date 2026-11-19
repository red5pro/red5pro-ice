# red5pro-ice

ICE library for the Red5 Pro Server. This library was originally derived from ice4j with a fork being heavily modified by Red5. The I/O layer and much of the logic has been reimagined to fulfill our needs and now lives here as a separate and distinct project.

## Features

I/O via [Mina](https://mina.apache.org/)
mDNS support via [jmdns](https://github.com/jmdns/jmdns)

## Building

```bash
# Build (default goal: clean install, tests skipped by default)
mvn clean install

# Build with tests
mvn clean install -DskipTests=false
```

## Testing

### Unit Tests

Unit tests cover STUN message encoding/decoding, attributes, and core ICE functionality.

```bash
# Run all unit tests via the test suite
mvn test -DskipTests=false

# Run a specific test class
mvn test -DskipTests=false -Dtest=com.red5pro.ice.attribute.AddressAttributeTest
```

### Integration Tests

Integration tests verify STUN/TURN candidate harvesting against a real coturn server running in Docker.

**Prerequisites:**
- Docker must be installed and running
- The `coturn/coturn` image will be pulled automatically if not present

```bash
# Run all integration tests
mvn test -DskipTests=false -Dtest=IntegrationTestSuite

# Run STUN tests only
mvn test -DskipTests=false -Dtest=StunHarvesterIntegrationTest

# Run TURN tests only
mvn test -DskipTests=false -Dtest=TurnHarvesterIntegrationTest
```

Tests will automatically skip if Docker is not available.

## Links

 * [Red5](https://github.com/Red5/ice4j) fork
 * Original project [Jitsi](https://jitsi.org/)

## Red5 AI Skills

Use the **Red5 AI skills** to help your AI coding assistant build real-time video applications with Red5 Pro and Red5 Cloud, with guidance grounded in Red5 documentation. [Download the skill from GitHub](https://github.com/red5pro/red5pro-skills) or install it by running `npx red5pro-skills` in your terminal.
