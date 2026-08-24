# Acquire test-retry-gradle-plugin into gradle/gradle

Date: 2026-07-28
Status: Design approved, pending implementation plan

## Goal

Import the source of https://github.com/gradle/test-retry-gradle-plugin into the gradle/gradle repository as a new subproject at `platforms/software/test-retry`, and ship it as a bundled Gradle plugin under a new plugin id `org.gradle.test-retry-bundled`. The bundled plugin must appear in the same Gradle distributions as `platforms/software/testing-base`.

The change should touch the imported plugin source as little as possible. Plumbing (toolchain, publishing, license enforcement, cross-version testing, shading, signing, etc.) is delegated to gradle/gradle's central build conventions.

## Non-goals for this change

- Porting `samples/` and `sample-tests/` from the standalone repo. Deferred.
- Porting the Spock TestKit functional tests. Deferred.
- Deprecating or removing the standalone `test-retry-gradle-plugin` published to the Plugin Portal. The bundled version uses a distinct id so both can coexist.

## Design decisions

### 1. Role: bundled Gradle plugin with a renamed id

test-retry is registered as a Gradle plugin bundled into the distribution. Users of a Gradle build containing this change can apply it with:

```kotlin
plugins {
    id("org.gradle.test-retry-bundled")
}
```

The id is deliberately `org.gradle.test-retry-bundled` (not the standalone plugin's `org.gradle.test-retry`) so that existing builds and tests applying the external plugin continue to resolve it from the Plugin Portal without being shadowed by the bundled variant.

### 2. Subproject location and platform registration

- Directory: `platforms/software/test-retry/`
- Registered in `settings.gradle.kts` by adding `subproject("test-retry")` inside the `software` platform block. Place it near the other `test*` entries (`test-suites-base`, `testing-base`, `testing-base-infrastructure`); the block is not strictly alphabetized so exact position is a stylistic choice at implementation time.

### 3. Source imported from the standalone plugin

Copied verbatim, no source rewrites:
- `plugin/src/main/java/**` → `src/main/java/**`
- `plugin/src/main/kotlin/**` → `src/main/kotlin/**` (the `testRetry.kt` Kotlin DSL extension)

Copied selectively — only the pure unit tests:
- `plugin/src/test/groovy/org/gradle/testretry/internal/executer/TestNamesTest.groovy`
- `plugin/src/test/groovy/org/gradle/testretry/internal/filter/GlobPatternTest.groovy`
- `plugin/src/test/groovy/org/gradle/testretry/internal/filter/RetryFilterTest.groovy`
- `plugin/src/test/groovy/org/gradle/testretry/internal/filter/AnnotationInspectorImplTest.groovy`

Not copied:
- All `*FuncTest.groovy` files and the `AbstractPluginFuncTest` family under `plugin/src/test/groovy/org/gradle/testretry/` (TestKit-driven; deferred to a follow-up).
- `TestFrameworkVersionData.groovy` (only referenced by func tests).
- `plugin/src/main/resources/NOTICE.txt` and `plugin/src/main/resources/licenses/` (ASM notice is redundant when ASM is not shaded).
- Everything outside `plugin/src/`: `.github/`, `.idea/`, `.teamcity/`, `config/`, `gradle/`, `buildSrc/`, `docs/`, `samples/`, `sample-tests/`, top-level `README.adoc`, `LICENSE`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `gradle.properties`, root `build.gradle.kts`, `plugin/build.gradle.kts`.

### 4. New plugin descriptor

Create `src/main/resources/META-INF/gradle-plugins/org.gradle.test-retry-bundled.properties`:

```properties
implementation-class=org.gradle.testretry.TestRetryPlugin
```

The implementation class `org.gradle.testretry.TestRetryPlugin` is imported without modification.

### 5. build.gradle.kts

Minimal, following the existing convention (mixed Java + Kotlin subproject):

```kotlin
plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.api-kotlin")
}

description = "Bundled Gradle plugin that mitigates flaky tests by retrying them when they fail"

dependencies {
    api(projects.coreApi)
    api(projects.core)

    implementation(projects.baseServices)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)
    implementation(projects.messaging)

    implementation(libs.asm)
    implementation(libs.jspecify)
    implementation(libs.inject)
    implementation(libs.guava)
}
```

The dependency list is a first draft. During implementation, adjust to match what the source actually imports (`gradle :test-retry:compileJava` will fail fast on missing deps).

### 6. Bundling in the distribution

Modify `platforms/jvm/distributions-jvm/build.gradle.kts` to add:

```kotlin
pluginsRuntimeOnly(projects.testRetry)
```

Rationale: `testing-base` ships in `distributions-jvm` transitively (via `api(projects.testingBase)` from JVM plugin subprojects). `distributions-jvm` is a dependency of `distributions-full`. Adding test-retry as `pluginsRuntimeOnly` in `distributions-jvm` therefore places the plugin jar in the JVM and full distributions — the same distributions that carry `testing-base`.

### 7. ASM: no shading

The standalone plugin uses `com.gradleup.shadow` to relocate `org.objectweb.asm.*` → `org.gradle.testretry.org.objectweb.asm.*` in the published jar. This was defensive against classpath conflicts when the plugin is resolved onto a user buildscript.

In the bundled context the plugin runs in Gradle's own classloader alongside all other bundled plugins, and ASM is already on the runtime classpath as `libs.asm`. The shading is dropped; source imports of `org.objectweb.asm.*` remain unchanged and resolve directly against `libs.asm`.

### 8. Deviations from the standalone plugin (accepted)

- No shaded ASM (see §7).
- JVM target is whatever gradle/gradle's central convention chooses (likely JVM 17), not JVM 8. The bundled plugin runs on the same JVM as the rest of the distribution.
- No `testGradle<N>Releases` matrix tasks. Cross-Gradle-version testing of a plugin bundled inside Gradle itself is not meaningful.
- No `checkstyle`, `codenarc`, or license-header enforcement carried over — governed centrally.
- No `maven-publish`, `plugin-publish`, `signing`, or `shadow` plugins applied — the standalone plugin's publishing pipeline does not apply to a bundled distribution artifact.
- No `java-gradle-plugin` plugin — plugin discovery is via the `META-INF/gradle-plugins/*.properties` descriptor (§4), which is the mechanism used by every other bundled Gradle plugin (e.g. `plugins-jvm-test-suite`).

### 9. Architecture tests

test-retry classes live under `org.gradle.testretry.**`, a package root not currently referenced in `testing/architecture-test/src/test/java/org/gradle/architecture/test/PackageCycleTest.java`. No preemptive entry is required. If `PackageCycleTest` flags a cycle during a real build, add an entry then.

## Files to create

- `platforms/software/test-retry/build.gradle.kts`
- `platforms/software/test-retry/src/main/java/org/gradle/testretry/**` (copied)
- `platforms/software/test-retry/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt` (copied)
- `platforms/software/test-retry/src/main/resources/META-INF/gradle-plugins/org.gradle.test-retry-bundled.properties` (new)
- `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/executer/TestNamesTest.groovy` (copied)
- `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/GlobPatternTest.groovy` (copied)
- `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/RetryFilterTest.groovy` (copied)
- `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/AnnotationInspectorImplTest.groovy` (copied)

## Files to modify

- `settings.gradle.kts` — add `subproject("test-retry")` under the `software` platform.
- `platforms/jvm/distributions-jvm/build.gradle.kts` — add `pluginsRuntimeOnly(projects.testRetry)`.

## Acceptance criteria

- `./gradlew :test-retry:build` succeeds.
- `./gradlew :test-retry:test` runs and passes the four ported unit tests.
- `./gradlew :distributions-jvm:build` succeeds and the resulting distribution jar layout contains the test-retry plugin jar and its descriptor.
- `./gradlew :architecture-test:test` passes (in particular `PackageCycleTest`).
- A trivial smoke check: from a scratch project's `settings.gradle.kts` pointing at a locally installed distribution, `plugins { id("org.gradle.test-retry-bundled") }` resolves and `test { retry { maxRetries = 2 } }` configures the extension.

## Follow-ups (out of scope for this change)

- Port the Spock TestKit funcTests, either as `integTest` sources verbatim (mixing TestKit with gradle/gradle's internal fixtures) or by rewriting to `AbstractIntegrationSpec`. Decision deferred.
- Port `samples/` and `sample-tests/`.
- Decide whether the standalone `org.gradle.test-retry` plugin on Plugin Portal is deprecated in favor of the bundled variant.
