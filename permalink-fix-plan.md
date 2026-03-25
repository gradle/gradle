# Plan: Fix permalink to point to last-modified commit

## Problem

PRs #35693, #36752, and #36994 introduced GitHub permalink URLs in generated wrapper and application start scripts. These URLs use `${gitRef}`, which is resolved to `DefaultGradleVersion.current().getGitRevision()` — the `commitId` from the build receipt. This commit ID is the **HEAD commit at Gradle build time**, not the commit that last modified the script template file.

This means the permalink may point to a commit where the template wasn't even changed, which defeats the purpose of a stable permalink. The link should point to the commit that last modified the template file so the URL always shows the correct version of the file.

## Affected URLs in the template

`unixStartScript.txt` (lines 60, 66, 93) contains three `${gitRef}` URLs:
1. Link to the template itself (`unixStartScript.txt`)
2. Link to the generator (`UnixStartScriptGenerator.java`)
3. Link to the wrapper invocation (`Wrapper.java`)

Only the first URL (the template itself) appears in generated start/wrapper scripts and needs a proper permalink via `${gitRef}`. The other two URLs (generator and Wrapper.java) are inside a `<% /* ... */ %>` meta-comment block that is stripped during template processing — they exist only for readers of the template source. Those two should simply use `HEAD` instead of `${gitRef}`, which removes them from the permalink concern entirely.

## Current flow

1. **Build time**: `BuildEnvironmentService` runs `git rev-parse HEAD` and stores it in `BuildEnvironmentExtension.gitCommitId`
2. **Build receipt**: `BuildReceipt` task writes `commitId` into `build-receipt.properties`
3. **Runtime**: `DefaultGradleVersion.current().getGitRevision()` reads `commitId` from the build receipt
4. **Script generation**: Both `ApplicationPlugin` and `WrapperGenerator` call `DefaultGradleVersion.current().getGitRevision()` and pass it as `gitRef`
5. **Gradle's own build**: `GradleStartScriptGenerator.kt` also uses `DefaultGradleVersion.current().gitRevision`

## Proposed solution

### Approach: Compute the last-modified commit at build time

Add a new property to the build receipt (or alongside it) that stores the git commit that last modified the template file. This is computed once during the Gradle build and embedded in the distribution.

### Step 0: Simplify `${gitRef}` usage in the template

In `unixStartScript.txt`, replace `${gitRef}` with `HEAD` in the two URLs inside the `<% /* ... */ %>` meta-comment block (lines 66 and 93). Only the first URL (line 60, the link to the template itself) should keep `${gitRef}`, since that's the only one that ends up in generated scripts.

**File**: `platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt`

### Step 1: Add a git query for the template file's last-modified commit

In `BuildEnvironmentService` (or a new build-logic utility), compute:
```
git log -1 --format=%H -- platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
```

This returns the SHA of the last commit that modified the template file.

**Location**: `build-logic-settings/build-environment/src/main/kotlin/gradlebuild/basics/BuildEnvironmentService.kt`

Add a new property like `scriptTemplateCommitId` alongside the existing `gitCommitId`.

### Step 2: Propagate through BuildEnvironmentExtension

Add `scriptTemplateCommitId` to `BuildEnvironmentExtension` and wire it up in `gradlebuild.build-environment.settings.gradle.kts`.

**Files**:
- `build-logic-settings/build-environment/src/main/kotlin/gradlebuild/basics/BuildEnvironmentExtension.kt`
- `build-logic-settings/build-environment/src/main/kotlin/gradlebuild.build-environment.settings.gradle.kts`

### Step 3: Store in the build receipt

Add a new field (e.g., `scriptTemplateCommitId`) to the `BuildReceipt` task output.

**File**: `build-logic-commons/module-identity/src/main/kotlin/gradlebuild/identity/tasks/BuildReceipt.kt`

### Step 4: Read from DefaultGradleVersion

Add a method like `getScriptTemplateGitRevision()` to `DefaultGradleVersion` that reads the new field from the build receipt.

**File**: `platforms/core-runtime/base-services/src/main/java/org/gradle/util/internal/DefaultGradleVersion.java`

### Step 5: Use the new commit ID in script generation

Update the three places that set `gitRef`:

1. **ApplicationPlugin.java** (line 194): Change to use `getScriptTemplateGitRevision()`
2. **WrapperGenerator.java** (line 136): Change to use `getScriptTemplateGitRevision()`
3. **GradleStartScriptGenerator.kt** (line 81): Change to use `scriptTemplateGitRevision`

**Files**:
- `platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java`
- `platforms/software/build-init/src/main/java/org/gradle/api/tasks/wrapper/internal/WrapperGenerator.java`
- `build-logic/jvm/src/main/kotlin/gradlebuild/startscript/tasks/GradleStartScriptGenerator.kt`

### Step 6: Update tests

#### 6a: Unit test — template meta-comment URLs use `HEAD` (new)

In `UnixStartScriptGeneratorTest.groovy`, add a test that verifies the two URLs inside the meta-comment block (`UnixStartScriptGenerator.java` and `Wrapper.java`) always use `HEAD`, regardless of the `gitRef` value. Generate a script with a specific gitRef and assert that those two URLs contain `/blob/HEAD/` while the template's own URL contains the specific commit hash.

**File**: `platforms/jvm/plugins-application/src/test/groovy/org/gradle/api/internal/plugins/UnixStartScriptGeneratorTest.groovy`

#### 6b: Unit test — permalink uses gitRef (existing, update if needed)

The existing test `"uses github permalinks in embedded documentation when gitRef specified"` already verifies that when a gitRef is provided, the generated script contains `/blob/<commit>/` instead of `/blob/HEAD/`. After Step 0, this test should still pass since the only `${gitRef}` URL remaining in generated output is the template link. Verify it still passes; adjust assertions if needed.

**File**: `platforms/jvm/plugins-application/src/test/groovy/org/gradle/api/internal/plugins/UnixStartScriptGeneratorTest.groovy`

#### 6c: Unit test — ApplicationPlugin wires gitRef correctly (existing, update)

The existing test `"adds startScripts task to project"` in `ApplicationPluginTest.groovy` asserts `task.gitRef.get() == DefaultGradleVersion.current().getGitRevision()`. After our change, this should assert against the new `getScriptTemplateGitRevision()` method instead.

**File**: `platforms/jvm/plugins-application/src/test/groovy/org/gradle/api/plugins/ApplicationPluginTest.groovy`

#### 6d: Integration test — generated script contains permalink (existing, verify)

The existing `StartScriptGeneratorIntegrationTest` creates and executes a start script with a hardcoded gitRef. This test doesn't exercise the build receipt flow, but confirms that the gitRef substitution works end-to-end in the template. Verify it still passes after the template change in Step 0.

**File**: `platforms/jvm/plugins-application/src/integTest/groovy/org/gradle/integtests/StartScriptGeneratorIntegrationTest.groovy`

#### 6e: Integration test — wrapper script contains correct permalink (new, if feasible)

Consider adding an integration test that generates a wrapper script and verifies the embedded URL contains a commit hash (not `HEAD`). This would exercise the `WrapperGenerator` → `DefaultGradleVersion.getScriptTemplateGitRevision()` path. However, since this depends on the build receipt being populated correctly (which only happens in a real Gradle distribution build), this may only be practically testable in CI as part of the full distribution build.

**Run tests with**:
```
./gradlew :plugins-application:test --tests '*UnixStartScriptGeneratorTest*' --tests '*ApplicationPluginTest*'
./gradlew :plugins-application:forkingIntegTest --tests '*StartScriptGeneratorIntegrationTest*'
./gradlew :plugins-application:configCacheIntegTest --tests '*StartScriptGeneratorIntegrationTest*'
```

## Edge cases

- **Local/dev builds**: When building from a dirty working tree or a fork, `git log` may return a different commit. The fallback to `"HEAD"` (already in place) handles this gracefully.
- **Shallow clones**: If CI uses shallow clones, `git log -1 --format=%H -- <file>` might fail if the last change to the template is outside the shallow history. The existing `"HEAD"` fallback would apply. Alternatively, the build could deepen the clone or fall back to `HEAD`.
- **Build from tarball (no .git)**: Same as today — falls back to `"HEAD"` or `"unknown"`.
