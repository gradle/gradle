# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **Gradle Build Tool** repository, a large open-source project with a modular architecture spanning millions of lines of code. Gradle is a highly scalable build automation tool supporting Java, Kotlin, Android, C++, Swift, and other platforms.

## Build and Development Commands

### Building
- **Full build**: Don't run this locally. Use `./gradlew sanityCheck` for validation instead.
  - The full build requires CI-scale parallelization and is impractical on local machines
- **Sanity check**: `./gradlew sanityCheck` - Validates code style and basic functionality

### Testing
- **Run tests for a specific subproject**: `./gradlew :<subproject>:quickTest`
  - Example: `./gradlew :launcher:quickTest`
  - This is the primary way to test your changes locally
- **Run build-logic tests**: `./gradlew :build-logic:check`
  - Use this when modifying build logic in the `build-logic` included build
- **Integration tests**: Gradle's test suite includes both unit tests (Spock) and integration tests that exercise full Gradle builds

### Local Installation
- **Install Gradle locally**: `./gradlew install -Pgradle_installPath=/any/path`
- **Use the installed build**: `/any/path/bin/gradle taskName`

### Documentation
- **Build documentation**: `./gradlew :docs:docs`
  - This builds the full documentation locally in `platforms/documentation`
  - See `platforms/documentation/docs/README.md` for more details

### Gradle Properties
Key gradle.properties settings (located at root):
- `org.gradle.parallel=true` - Parallel execution enabled
- `org.gradle.caching=true` - Build caching enabled
- `org.gradle.configuration-cache=true` - Configuration cache enabled
- JVM args: `-Xmx3200m -XX:MaxMetaspaceSize=768m`
- Requires **Java 17** (Adoptium JDK)

## Architecture Overview

### Platform Architecture

Gradle is organized into **platforms** (coarse-grained components under `platforms/`):

- **core-runtime**: Runtimes/containers (Gradle client, daemon, worker processes). Base dependency for everything.
- **core-configuration**: Build structure definition (project model, DSL, configuration).
- **core-execution**: Work execution (scheduling, caching, task execution).
- **software**: General software development automation (compiling, testing, publishing, dependency management).
- **jvm**: JVM-specific support (Java, Kotlin, Scala, Groovy, foojay toolchain).
- **extensibility**: Plugin development and publication support.
- **native**: Native software support (C++, Swift, C).
- **ide**: IDE integration (IntelliJ, Eclipse, VS Code support).
- **documentation**: User Manual, DSL Reference, samples, and related infrastructure.
- **enterprise**: Cross-cutting integration with Gradle's commercial product.

See `architecture/platforms.md` for detailed architecture documentation and diagrams.

### Build State Model

Gradle maintains a **build state model** that tracks state of build pieces (projects, tasks, etc.) and coordinates state transitions. Most source code is organized around which parts of the build state model it acts on. This affects code lifecycle and available services.

See `architecture/build-state-model.md` for details.

### Gradle Runtimes

Several processes run to execute a build:
- **Gradle daemon**: Long-running process executing build requests
- **Client processes**: `gradlew` command and tooling API clients
- **Worker processes**: Run actual build work in isolation

Each runtime has different supported JVMs and available services for dependency injection. When working on source code, be aware of which runtime(s) it runs in.

See `architecture/runtimes.md` for details.

### Build Execution Model

Gradle responds to client requests (run tasks, query tooling model, stop daemon). Each daemon runs one request at a time. Some background actions occur (memory monitoring, file watching, cache cleanup) but never user code.

See `architecture/build-execution-model.md` for details.

## Build Infrastructure

### Build Logic Organization

- **build-logic**: Plugins and conventions for the Gradle build itself (in-process included build)
  - Contains: root-build, lifecycle, packaging, jvm, kotlin-dsl, integration-testing, performance-testing, buildquality
- **build-logic-commons**: Shared code for build logic (in-process included build)
  - Contains: basics, build-platform, publishing, module-identity, code-quality-rules, gradle-plugin
- **build-logic-settings**: Build environment and settings setup (in-process included build)

### Source Organization

Under `platforms/`:
- Each platform/module has its own source directory
- Projects are organized by platform (e.g., `platforms/jvm/`, `platforms/core-runtime/`)
- Platforms and modules defined in `settings.gradle.kts`

## Testing Framework and Practices

- **Test framework**: [Spock](https://spockframework.org/) (Groovy-based)
- **Test types**:
  - **Unit tests**: Small isolated pieces of code with varied inputs
  - **Integration tests**: Full Gradle build execution with verification of external state (files, output)
- **Best practices**:
  - Look at existing tests for similar functionality before writing new tests
  - Integration tests should verify effects by examining resulting files/output, not internal state
  - Use unit tests for internal state verification and mocks
  - Use data-driven tables for testing multiple scenarios
  - Avoid assertions in Gradle build scripts under test; print via stdout instead
  - Link bug-related tests to GitHub issues using `@Issue` annotation
  - See `contributing/Testing.md` for comprehensive testing guidance

## Code Style and Contributions

### Before Starting

Before working on a feature or bug fix, open or comment on a GitHub issue to discuss:
- Why the change is needed (use case)
- For features: what the API will look like
- What test cases should exist, what could go wrong
- Rough implementation approach

### Code Guidelines

- Javadoc is **required** for all new public, top-level types (classes, interfaces)
- Use **American English** spelling in code, comments, and documentation (see ADR-0009)
- Cover code with tests
- Add documentation to User Manual and DSL Reference under `platforms/documentation/docs/src/docs/`
- For error messages, follow `contributing/ErrorMessages.md`
- Follow Javadoc Style Guide in `contributing/JavadocStyleGuide.md`
- Add feature mentions to Release Notes at `platforms/documentation/docs/src/docs/release/notes.md`
- For Java version compatibility: Some Gradle parts run on Java 8; be careful with Java 9+ features
- Normalize file paths in tests using `org.gradle.util.internal.TextUtil`

### Commits and PRs

- **Sign off commits**: Required for DCO (Developer Certificate of Origin). Use `git commit -m "message" --signoff`
- **Commit message quality**: Follow [git commit guidelines](https://cbea.ms/git-commit/#seven-rules)
- **Discrete commits**: Each commit should be self-contained and make sense in isolation
- **Copyright headers**: Required for source files (.java, .kt, .groovy) and documentation files (.adoc, .md)
  - See `CONTRIBUTING.md` for required header format
  - Exempt: build scripts (.kts), auto-generated files, config files (.gitignore), samples, release notes, READMEs

### IDE Setup (IntelliJ)

1. Open `build.gradle.kts` with IntelliJ → "Open as Project"
2. Select **Adoptium Java 17** as "Gradle JVM"
3. Revert Git changes to `.idea` folder
4. Disable IntelliJ's `org.gradle` stack trace folding (Preferences → Editor → General → Console)
5. Consider installing the [Develocity IntelliJ plugin](https://plugins.jetbrains.com/plugin/27471-develocity)

**Note**: First import can take a while; IntelliJ may be unresponsive temporarily.

## Architecture Decision Records (ADRs)

Gradle team uses ADRs to document architectural decisions. These are technical and may be complex, but provide valuable context. Located in `architecture/standards/`.

Key ADRs:
- ADR-0004: Platform architecture model
- ADR-0009: Use American English spelling

## Security Vulnerabilities

Do not report security vulnerabilities to the public issue tracker. Follow the [Security Vulnerability Disclosure Policy](https://github.com/gradle/gradle/security/policy).

## Community and Support

- **Slack**: [Join Gradle Community Slack](https://gradle.org/slack-invite) - use `#contributing` channel for questions
- **Forum**: [Gradle Forum](https://discuss.gradle.org/)
- **Issues**: Label with `good first issue` or `help wanted` for contribution opportunities
- **Documentation**: [Gradle User Guide](https://docs.gradle.org/)

## Key Files and Directories

- `build.gradle.kts` - Root build script
- `settings.gradle.kts` - Project structure and platform definitions
- `gradle.properties` - Build environment properties
- `CONTRIBUTING.md` - Comprehensive contribution guide
- `contributing/` - Testing, debugging, error messages, and Javadoc guides
- `architecture/` - Architecture documentation and ADRs
- `platforms/` - All platform and module source code
- `build-logic/`, `build-logic-commons/`, `build-logic-settings/` - Build infrastructure
- `.teamcity/` - CI/CD configuration

## Important Notes

- **Do NOT run `gradle build` locally** without caching/parallelization enabled - the repo is too large
- Full CI testing runs on the CI infrastructure for multiple configurations; rely on `./gradlew sanityCheck` locally
- The project uses Gradle's configuration cache and build cache extensively - these must remain enabled
- All code paths must support the Java versions and operating systems Gradle supports (see compatibility matrix)