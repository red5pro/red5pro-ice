# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven-based Java library (red5pro-ice) derived from ice4j with Mina-based I/O. The main code lives under `src/main/java/com/red5pro/ice`, with major areas such as `harvest`, `nio`, `socket`, `stack`, and `attribute`. Tests are under `src/test/java`, with unit tests alongside integration tests in `src/test/java/com/red5pro/ice/integration`. Test resources are in `src/test/resources`. Documentation and reference material live in `doc/` and `docs/` (RFC notes and compliance analysis).

## Build, Test, and Development Commands
Use Maven for all builds:

```bash
mvn clean install
```
Builds the library; tests are skipped by default.

```bash
mvn clean install -DskipTests=false
```
Builds with unit tests enabled.

```bash
mvn test -DskipTests=false
```
Runs the unit test suite.

```bash
mvn test -DskipTests=false -Dtest=IntegrationTestSuite
```
Runs integration tests against a Docker-based coturn server (Docker required).

```bash
mvn net.revelc.code.formatter:formatter-maven-plugin:format
```
Formats source using `Red5Pro-formatter.xml`.

## Coding Style & Naming Conventions
Java 11, 4-space indentation, LF line endings, and 140-character line width. Follow existing package naming under `com.red5pro.ice`. Use descriptive class names (`*Harvester`, `*Attribute`, `*Test`). Formatting is handled by the Maven formatter plugin; prefer running it before committing.

## Testing Guidelines
Unit tests are JUnit 4 (see `pom.xml` and `src/test/java`). Test classes use the `*Test` or `*Tests` suffix (e.g., `NetworkUtilsTest`, `TransactionSupportTests`). Integration tests live under `com.red5pro.ice.integration` and can skip automatically if Docker is unavailable.

## Commit & Pull Request Guidelines
Recent commits use short, imperative summaries (e.g., "Fix candidate comparator...", "Update version to 1.2.10"). Keep commit subjects concise and specific. For PRs, include a clear description, list relevant tests run (`mvn test...`), and call out any Docker requirements for integration tests. Link related issues when available.

## Configuration & Debugging Tips
Logging is configured via `src/test/resources/logback-test.xml` and `src/test/resources/logging.properties`. When diagnosing ICE flow, the `Agent`, `ConnectivityCheckClient/Server`, and `CheckList` classes are key entry points.
