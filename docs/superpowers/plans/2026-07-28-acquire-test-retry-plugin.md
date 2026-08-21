# Acquire test-retry-gradle-plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import the source of https://github.com/gradle/test-retry-gradle-plugin into `platforms/software/test-retry` and ship it as a bundled Gradle plugin under the id `org.gradle.test-retry-bundled`, included in `distributions-jvm`.

**Architecture:** New subproject at `platforms/software/test-retry` using the standard `gradlebuild.distribution.api-java` + `gradlebuild.distribution.api-kotlin` conventions. Plugin sources copied verbatim from `gradle/test-retry-gradle-plugin@main`. Bundled via a `META-INF/gradle-plugins/*.properties` descriptor and a `pluginsRuntimeOnly` dep in `distributions-jvm`. No shading, no publishing pipeline, no cross-version tests.

**Tech Stack:** Kotlin DSL build script, Java 17 (per gradle/gradle central conventions), Groovy Spock unit tests, ASM (unshaded, from `libs.asm`), gradle/gradle's internal `projects.*` type-safe project accessors.

**Reference spec:** `docs/superpowers/specs/2026-07-28-acquire-test-retry-plugin-design.md`

---

## Preflight: fetch the upstream source into a scratch clone

Every task copies files out of the upstream `test-retry-gradle-plugin` repo. Cloning it once up front and reading from a stable path is simpler and more reliable than re-fetching each file via `gh api`.

- [ ] **Step 0.1: Clone the upstream plugin repo to /tmp**

```bash
rm -rf /tmp/test-retry-upstream
git clone --depth 1 https://github.com/gradle/test-retry-gradle-plugin.git /tmp/test-retry-upstream
```

Expected: clone completes; `/tmp/test-retry-upstream/plugin/src/main/java/org/gradle/testretry/TestRetryPlugin.java` exists.

- [ ] **Step 0.2: Verify the source tree looks as expected**

```bash
ls /tmp/test-retry-upstream/plugin/src/main/java/org/gradle/testretry/
ls /tmp/test-retry-upstream/plugin/src/main/kotlin/org/gradle/kotlin/dsl/
ls /tmp/test-retry-upstream/plugin/src/test/groovy/org/gradle/testretry/internal/
```

Expected: first command shows `TestRetryPlugin.java`, `TestRetryTaskExtension.java`, and an `internal/` directory. Second shows `testRetry.kt`. Third shows `executer/` and `filter/`.

**No commit for the preflight — nothing changes in the repo yet.**

---

## Task 1: Scaffold the subproject and register it in settings

Get Gradle to recognize `:test-retry` as a valid, empty subproject. This is the smallest possible checkpoint — no sources, no deps, just a valid project.

**Files:**
- Create: `platforms/software/test-retry/build.gradle.kts`
- Modify: `settings.gradle.kts` (add one line inside the `software` platform block)

- [ ] **Step 1.1: Create the subproject directory**

```bash
mkdir -p platforms/software/test-retry
```

- [ ] **Step 1.2: Create the minimal build.gradle.kts**

Write `platforms/software/test-retry/build.gradle.kts` with exactly this content:

```kotlin
plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.api-kotlin")
}

description = "Bundled Gradle plugin that mitigates flaky tests by retrying them when they fail"

dependencies {
}
```

- [ ] **Step 1.3: Register the subproject in settings.gradle.kts**

Open `settings.gradle.kts` and locate the `software` platform block that starts at approximately line 205:

```kotlin
val software = platform("software") {
    uses(core)
    ...
    subproject("test-suites-base")
    ...
}
```

Add a new line `subproject("test-retry")` inside that block, next to the other `test*` entries. The block is not strictly alphabetized; placing it immediately before `subproject("test-suites-base")` is a reasonable spot.

- [ ] **Step 1.4: Verify Gradle sees the new project**

```bash
./gradlew :test-retry:help
```

Expected: `BUILD SUCCESSFUL`. The output shows help text for the `:test-retry` project. If `settings.gradle.kts` didn't get updated correctly the command will fail with "Project 'test-retry' not found".

- [ ] **Step 1.5: Commit**

```bash
git add settings.gradle.kts platforms/software/test-retry/build.gradle.kts
git commit -m "Scaffold platforms/software/test-retry subproject"
```

---

## Task 2: Copy the plugin's Java main sources

Copy every Java file under `plugin/src/main/java/`. Keep the package structure identical. Do not modify any file contents — every source file is imported verbatim.

**Files:**
- Create: `platforms/software/test-retry/src/main/java/org/gradle/testretry/**` (30 Java files, verbatim copies)

- [ ] **Step 2.1: Copy the entire Java source tree**

```bash
mkdir -p platforms/software/test-retry/src/main/java
cp -R /tmp/test-retry-upstream/plugin/src/main/java/org platforms/software/test-retry/src/main/java/
```

- [ ] **Step 2.2: Verify the expected files are present**

```bash
find platforms/software/test-retry/src/main/java -name '*.java' | wc -l
find platforms/software/test-retry/src/main/java -name '*.java' | sort
```

Expected: exactly 30 `.java` files. The list should include `TestRetryPlugin.java`, `TestRetryTaskExtension.java`, `internal/config/DefaultTestRetryTaskExtension.java`, `internal/executer/RetryTestExecuter.java`, `internal/executer/framework/JunitTestFrameworkStrategy.java`, `internal/filter/RetryFilter.java`, `internal/testsreader/TestsReader.java`, and the rest of the internal packages.

- [ ] **Step 2.3: Try to compile — this will fail; note what's missing**

```bash
./gradlew :test-retry:compileJava 2>&1 | tail -40
```

Expected: BUILD FAILED with `package org.X.Y does not exist` errors. Capture the list of unresolved packages — you'll use it in the next step to pick dependencies.

- [ ] **Step 2.4: Add the minimum dependencies to build.gradle.kts**

Edit `platforms/software/test-retry/build.gradle.kts` and replace its `dependencies { }` block with:

```kotlin
dependencies {
    api(projects.coreApi)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.messaging)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jspecify)
}
```

- [ ] **Step 2.5: Recompile — iterate until clean**

```bash
./gradlew :test-retry:compileJava 2>&1 | tail -40
```

Expected outcome: BUILD SUCCESSFUL. If any `package ... does not exist` errors remain, look up which subproject owns the missing package (search `platforms/**/src/main/java/<package-path>` with Grep) and add it to the `implementation` block. Rerun until clean. Common candidates you may need to add: `projects.processServices`, `projects.processServicesApi`, `projects.enterpriseLogging`, `projects.enterpriseOperations`, `projects.problemsApi`, `projects.native`, `projects.serialization`, `projects.buildOperations`, `projects.fileCollections`, `projects.files`, `projects.functional`, `projects.workerProcessServices`.

Do NOT paper over failures with `@SuppressWarnings` or by editing plugin source. If a source file references something not present in gradle/gradle, STOP and escalate before continuing.

- [ ] **Step 2.6: Commit**

```bash
git add platforms/software/test-retry/
git commit -m "Copy test-retry plugin Java sources and add compile deps"
```

---

## Task 3: Copy the Kotlin DSL extension

Add the `testRetry.kt` Kotlin DSL accessor. The `gradlebuild.distribution.api-kotlin` convention plugin (already applied in Task 1) handles Kotlin compilation.

**Files:**
- Create: `platforms/software/test-retry/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt`

- [ ] **Step 3.1: Copy the Kotlin file**

```bash
mkdir -p platforms/software/test-retry/src/main/kotlin/org/gradle/kotlin/dsl
cp /tmp/test-retry-upstream/plugin/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt \
   platforms/software/test-retry/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt
```

- [ ] **Step 3.2: Verify the file is present and untouched**

```bash
diff /tmp/test-retry-upstream/plugin/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt \
     platforms/software/test-retry/src/main/kotlin/org/gradle/kotlin/dsl/testRetry.kt
```

Expected: no output (files identical).

- [ ] **Step 3.3: Compile Kotlin**

```bash
./gradlew :test-retry:compileKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. If it fails with `unresolved reference`, note what's missing — the Kotlin DSL extension references `TestRetryTaskExtension`, which is in `src/main/java` and should already be on the compile classpath since Java and Kotlin share the same sourceset.

- [ ] **Step 3.4: Commit**

```bash
git add platforms/software/test-retry/src/main/kotlin/
git commit -m "Copy test-retry Kotlin DSL extension"
```

---

## Task 4: Register the bundled plugin descriptor

Create the `META-INF/gradle-plugins/*.properties` file that maps the plugin id `org.gradle.test-retry-bundled` to the implementation class. This is the mechanism every other bundled Gradle plugin uses (see `platforms/jvm/plugins-jvm-test-suite/src/main/resources/META-INF/gradle-plugins/org.gradle.jvm-test-suite.properties` as a reference).

**Files:**
- Create: `platforms/software/test-retry/src/main/resources/META-INF/gradle-plugins/org.gradle.test-retry-bundled.properties`

- [ ] **Step 4.1: Create the descriptor**

```bash
mkdir -p platforms/software/test-retry/src/main/resources/META-INF/gradle-plugins
```

Write `platforms/software/test-retry/src/main/resources/META-INF/gradle-plugins/org.gradle.test-retry-bundled.properties` with exactly this content:

```properties
implementation-class=org.gradle.testretry.TestRetryPlugin
```

- [ ] **Step 4.2: Build the jar and verify the descriptor is packaged**

```bash
./gradlew :test-retry:jar
unzip -p platforms/software/test-retry/build/libs/gradle-test-retry-*.jar \
    META-INF/gradle-plugins/org.gradle.test-retry-bundled.properties
```

Expected: BUILD SUCCESSFUL, and the `unzip -p` command prints `implementation-class=org.gradle.testretry.TestRetryPlugin`. If the jar name differs, use `ls platforms/software/test-retry/build/libs/` to find the actual name.

- [ ] **Step 4.3: Commit**

```bash
git add platforms/software/test-retry/src/main/resources/
git commit -m "Register bundled plugin id org.gradle.test-retry-bundled"
```

---

## Task 5: Copy the four unit tests

Copy only the pure unit tests. Do NOT copy any `*FuncTest.groovy` or the `AbstractPluginFuncTest` family — those are deferred per the spec.

**Files:**
- Create: `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/executer/TestNamesTest.groovy`
- Create: `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/GlobPatternTest.groovy`
- Create: `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/RetryFilterTest.groovy`
- Create: `platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/AnnotationInspectorImplTest.groovy`

- [ ] **Step 5.1: Copy the four test files**

```bash
mkdir -p platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/executer
mkdir -p platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter

cp /tmp/test-retry-upstream/plugin/src/test/groovy/org/gradle/testretry/internal/executer/TestNamesTest.groovy \
   platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/executer/

cp /tmp/test-retry-upstream/plugin/src/test/groovy/org/gradle/testretry/internal/filter/GlobPatternTest.groovy \
   platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/

cp /tmp/test-retry-upstream/plugin/src/test/groovy/org/gradle/testretry/internal/filter/RetryFilterTest.groovy \
   platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/

cp /tmp/test-retry-upstream/plugin/src/test/groovy/org/gradle/testretry/internal/filter/AnnotationInspectorImplTest.groovy \
   platforms/software/test-retry/src/test/groovy/org/gradle/testretry/internal/filter/
```

- [ ] **Step 5.2: Verify exactly four test files present**

```bash
find platforms/software/test-retry/src/test -name '*.groovy'
```

Expected: exactly four files, matching the four listed above. No `*FuncTest.groovy`.

- [ ] **Step 5.3: Try to compile the tests**

```bash
./gradlew :test-retry:compileTestGroovy 2>&1 | tail -30
```

Expected outcome: BUILD FAILED with unresolved Spock references (`spock.lang.Specification` not found). Note the specific missing symbols.

- [ ] **Step 5.4: Add test dependencies**

Edit `platforms/software/test-retry/build.gradle.kts` and add a test section to the dependencies block. The full `dependencies { }` block should now look like:

```kotlin
dependencies {
    api(projects.coreApi)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.messaging)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jspecify)

    testImplementation(libs.groovy)
    testImplementation(libs.spock)
    testImplementation(libs.asm)
}
```

(Keep any additional `implementation(projects.*)` entries you added during Task 2.5.)

- [ ] **Step 5.5: Compile the tests**

```bash
./gradlew :test-retry:compileTestGroovy 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. If unresolved references remain, check the imports in each test file and add the corresponding `testImplementation` dependency (search `platforms/**/build.gradle.kts` with Grep for how another subproject depends on the same lib).

- [ ] **Step 5.6: Run the tests**

```bash
./gradlew :test-retry:test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. All four test classes execute and pass. If any test fails, STOP — do NOT modify the copied test source. Diagnose whether the failure is a genuine environmental difference (e.g. classpath variance) or a real bug, and escalate before modifying either the test or the plugin source.

- [ ] **Step 5.7: Verify the test-results XML**

```bash
ls platforms/software/test-retry/build/test-results/test/
```

Expected: four `TEST-*.xml` files, one per copied test class. Read one and confirm `failures="0" errors="0"`.

- [ ] **Step 5.8: Commit**

```bash
git add platforms/software/test-retry/
git commit -m "Copy test-retry unit tests and enable :test-retry:test"
```

---

## Task 6: Bundle the plugin into distributions-jvm

Wire the plugin into the JVM distribution so it ships alongside `testing-base`.

**Files:**
- Modify: `platforms/jvm/distributions-jvm/build.gradle.kts`

- [ ] **Step 6.1: Read the current distributions-jvm build script**

```bash
cat platforms/jvm/distributions-jvm/build.gradle.kts
```

Expected: you see the `dependencies { }` block with a series of `pluginsRuntimeOnly(projects.<name>)` calls.

- [ ] **Step 6.2: Add pluginsRuntimeOnly(projects.testRetry)**

Edit `platforms/jvm/distributions-jvm/build.gradle.kts`. Inside the `dependencies { }` block, add:

```kotlin
    pluginsRuntimeOnly(projects.testRetry)
```

Place it next to the other `pluginsRuntimeOnly` entries. Alphabetical ordering is a stylistic choice; between `pluginsRuntimeOnly(projects.signing)` and `pluginsRuntimeOnly(projects.scala)` or at the end of the block are both fine.

- [ ] **Step 6.3: Build the JVM distribution**

```bash
./gradlew :distributions-jvm:build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.4: Verify the plugin jar and descriptor are in the distribution**

```bash
find packaging/distributions-full/build/gradle-*/lib/plugins -name 'gradle-test-retry-*.jar' 2>/dev/null
```

If that path doesn't exist yet, run the full distribution build first:

```bash
./gradlew :distributions-full:buildDists 2>&1 | tail -10
find packaging/distributions-full/build/distributions -name 'gradle-*-bin.zip'
```

Then extract and inspect one distribution:

```bash
BIN_ZIP=$(find packaging/distributions-full/build/distributions -name 'gradle-*-bin.zip' | head -1)
unzip -l "$BIN_ZIP" | grep test-retry
```

Expected: the listing shows a `lib/plugins/gradle-test-retry-*.jar` entry. If not, review the `pluginsRuntimeOnly` wiring — the entry must be inside the `dependencies` block.

- [ ] **Step 6.5: Commit**

```bash
git add platforms/jvm/distributions-jvm/build.gradle.kts
git commit -m "Bundle test-retry plugin into distributions-jvm"
```

---

## Task 7: Sanity check + architecture tests

Run gradle/gradle's cross-cutting checks to confirm the new subproject doesn't violate anything (package cycles, missing runtime targets, etc).

- [ ] **Step 7.1: Run sanity check**

```bash
./gradlew sanityCheck 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. If `PackageCycleTest` fails because it finds `org.gradle.testretry` classes forming a cycle with something else, add an entry to `testing/architecture-test/src/test/java/org/gradle/architecture/test/PackageCycleTest.java`:

```java
entry("test-retry", List.of("org/gradle/testretry/**")),
```

And re-run.

- [ ] **Step 7.2: Run the architecture-test module explicitly**

```bash
./gradlew :architecture-test:test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.3: Confirm computedRuntimes doesn't need adjustment**

```bash
./gradlew :test-retry:checkTargetRuntimes 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, no fix suggested. If it suggests `--fix`, run:

```bash
./gradlew :test-retry:checkTargetRuntimes --fix
```

This writes a `gradleModule { computedRuntimes { ... } }` block into `platforms/software/test-retry/build.gradle.kts`. Review the diff — a `daemon = true` line is expected for a plugin used inside the daemon.

- [ ] **Step 7.4: Commit any changes from steps 7.1 or 7.3**

```bash
git add -A
git status
```

If `git status` shows modifications, commit:

```bash
git commit -m "Wire test-retry into cross-cutting checks (architecture, target runtimes)"
```

If nothing changed, skip the commit.

---

## Task 8: End-to-end smoke test of the bundled plugin

Install the distribution locally and confirm a scratch project can apply `org.gradle.test-retry-bundled` and configure a `Test` task with retries.

- [ ] **Step 8.1: Install the local distribution**

```bash
./gradlew :distributions-full:install -Pgradle_installPath=/tmp/gradle-test-retry-check
```

Expected: BUILD SUCCESSFUL. A working Gradle distribution is installed at `/tmp/gradle-test-retry-check/`.

- [ ] **Step 8.2: Confirm the plugin jar landed in the distribution**

```bash
ls /tmp/gradle-test-retry-check/lib/plugins/ | grep test-retry
```

Expected: exactly one `gradle-test-retry-<version>.jar` file.

- [ ] **Step 8.3: Set up a scratch project**

```bash
rm -rf /tmp/tr-scratch && mkdir /tmp/tr-scratch && cd /tmp/tr-scratch
cat > settings.gradle.kts <<'EOF'
rootProject.name = "tr-scratch"
EOF
cat > build.gradle.kts <<'EOF'
plugins {
    `java-library`
    id("org.gradle.test-retry-bundled")
}

repositories { mavenCentral() }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    retry {
        maxRetries = 2
        maxFailures = 5
    }
}
EOF
mkdir -p src/test/java
cat > src/test/java/AlwaysFailTest.java <<'EOF'
import org.junit.Test;
public class AlwaysFailTest {
    @Test public void fails() { throw new AssertionError("boom"); }
}
EOF
```

- [ ] **Step 8.4: Run the scratch project's tests using the local distribution**

```bash
cd /tmp/tr-scratch
/tmp/gradle-test-retry-check/bin/gradle test --info 2>&1 | grep -E '(retry|attempt|BUILD )' | head -20
```

Expected: BUILD FAILED (tests fail — the goal is not for tests to pass, it is for the retry plugin to try each test 3 times before giving up). The output should show the retry plugin ran the test class multiple times (search for `AlwaysFailTest` appearing more than once in the info log, or for `> 3 tests completed, 3 failed`).

- [ ] **Step 8.5: Return to the gradle/gradle worktree**

```bash
cd /Users/ttresansky/Projects/gradle-worktrees/master-worktrees/tt/98/acquire-test-retry-plugin
```

- [ ] **Step 8.6: No commit needed for the smoke test itself**

The smoke test creates files under `/tmp/` only — nothing to commit.

---

## Task 9: Clean up the preflight clone

- [ ] **Step 9.1: Remove the scratch upstream clone**

```bash
rm -rf /tmp/test-retry-upstream /tmp/tr-scratch /tmp/gradle-test-retry-check
```

No commit.

---

## Acceptance criteria (from the spec, re-verified)

Before declaring the plan complete, confirm all of these pass:

- [ ] `./gradlew :test-retry:build` succeeds (Task 2, 3, 4, 5).
- [ ] `./gradlew :test-retry:test` runs the four ported unit tests and all pass (Task 5.6).
- [ ] `./gradlew :distributions-jvm:build` succeeds (Task 6.3).
- [ ] `./gradlew sanityCheck` passes including `PackageCycleTest` (Task 7.1).
- [ ] A scratch project can apply `id("org.gradle.test-retry-bundled")` against the locally installed distribution and configure `test { retry { ... } }` (Task 8.4).
