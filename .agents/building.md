# Building

## Prerequisites

- The project uses the Gradle wrapper (`./gradlew`). Never use a system-installed `gradle` command.

## Important Warnings

- **NEVER run `./gradlew build`** on the full project. The repository is massive and a full build will take an extremely long time locally.
- Always target specific subprojects when building or testing.
- Always use `-q` argument to reduce build output noise
- **NEVER run a `clean` task** to ensure all changes are applied. Trust the build system. If you can't reason about the results of a build, the problem is elsewhere. 

## Common Build Commands

### Sanity check before submitting changes
```
./gradlew sanityCheck
```

### Build all code - production + tests
```
./gradlew compileAll
```

### Build logic changes
```
./gradlew :build-logic:check
```

### Generate documentation
```
./gradlew :docs:docs
```

### Install Gradle locally for manual testing
```
./gradlew install
```

This installs Gradle into the path specified by `gradle_installPath`, which has to be set on the CLI if not persisted in `~/.gradle/gradle.properties`.

### Build a distribution from source
```
./gradlew binDist
```

Distribution is then under `packaging/distributions-full/build/distributions/gradle-<version>-bin.zip` and can be used for testing or manual installation.

## Language Levels

Production code is compiled to a JVM target based on the `gradleModule.targetRuntimes` flags in the module's `build.gradle.kts`:
- `usedInClient = true` -> Java 8
- `usedInWorkers = true` -> Java 8
- `usedInDaemon = true` -> Java 17

When multiple flags are set, the minimum version wins. 
Constants are in `platforms/core-runtime/build-process-services/src/main/java/org/gradle/internal/jvm/SupportedJavaVersions.java`.
Test code (`src/test`, `src/integTest`, etc.) always compiles to Java 17, regardless of the module's runtime target.

## Build Configuration

The current version being built is defined in `version.txt`.

To disable configuration cache (needed for some tasks like docs): `--no-configuration-cache`

## Project Structure

The project is organized into **platforms** under `platforms/`:
- `core-runtime/` - Core runtime components
- `core-configuration/` - Configuration handling (includes `configuration-cache`, `kotlin-dsl`, `model-core`)
- `core-execution/` - Execution and caching
- `software/` - Software platform (`dependency-management`, `testing-base`, `maven`, `ivy`)
- `jvm/` - JVM platform (`java`, `groovy`, `scala`, `jacoco`)
- `native/` - Native platform support
- `ide/` - IDE integration (`tooling-api`)
- `documentation/` - User docs and samples
- `extensibility/` - Plugin system and test-kit
- `enterprise/` - Develocity/Gradle Enterprise integration

Build logic plugins live in `build-logic/`, `build-logic-commons/`, and `build-logic-settings/`.
