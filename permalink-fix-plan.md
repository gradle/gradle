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

1. **Build time**: `BuildEnvironmentService` runs `git rev-parse HEAD` and stores it in `BuildEnvironmentExtension.gitCommitId`. Note: `BuildEnvironmentService` is build-logic source code compiled fresh each build by the Gradle version the wrapper downloads. Adding a new `git log` call uses the same `ProviderFactory.exec` API already in use, so no wrapper version upgrade is required.
2. **Build receipt**: `BuildReceipt` task writes `commitId` into `build-receipt.properties`
3. **Runtime**: `DefaultGradleVersion.current().getGitRevision()` reads `commitId` from the build receipt
4. **Script generation**: Both `ApplicationPlugin` and `WrapperGenerator` call `DefaultGradleVersion.current().getGitRevision()` and pass it as `gitRef`
5. **Gradle's own build**: `GradleStartScriptGenerator.kt` also uses `DefaultGradleVersion.current().gitRevision`

## Proposed solution

### Approach: Compute the last-modified commit at build time

Add a new property to the build receipt (or alongside it) that stores the git commit that last modified the template file. This is computed once during the Gradle build and embedded in the distribution.

### Step 6: Update tests

#### 6c: Unit test — ApplicationPlugin wires gitRef correctly (existing, update)

The existing test `"adds startScripts task to project"` in `ApplicationPluginTest.groovy` asserts `task.gitRef.get() == DefaultGradleVersion.current().getGitRevision()`. After our change, this should assert against the new `getScriptTemplateGitRevision()` method instead.

**File**: `platforms/jvm/plugins-application/src/test/groovy/org/gradle/api/plugins/ApplicationPluginTest.groovy`

#### 6d: Integration test — generated script contains permalink (existing, verify)

The existing `StartScriptGeneratorIntegrationTest` creates and executes a start script with a hardcoded gitRef. This test doesn't exercise the build receipt flow, but confirms that the gitRef substitution works end-to-end in the template. Verify it still passes after the template change in Step 0.

**File**: `platforms/jvm/plugins-application/src/integTest/groovy/org/gradle/integtests/StartScriptGeneratorIntegrationTest.groovy`

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
