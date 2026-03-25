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

Update existing tests to verify the new behavior. The tests from PR #35693 should continue to work with the updated commit reference.

## Alternative approaches considered

### Alternative A: Compute at script-generation time (not build time)

Have the script generation task run `git log` itself. **Rejected** because:
- Script generation runs both in Gradle's own build and in user projects (via `CreateStartScripts`)
- User projects don't have Gradle's git repo available
- The value needs to be baked into the Gradle distribution

### Alternative B: Use a hardcoded/manual commit hash

Manually update a constant whenever the template changes. **Rejected** because:
- Error-prone: easy to forget updating after a template change
- Adds maintenance burden

### Alternative C: Use `${gitRef}` for all three URLs

Use the same `${gitRef}` for all three URLs in the template. **Rejected** because:
- The generator and Wrapper.java URLs are in a meta-comment block stripped during template processing — they're only for template readers
- Using `HEAD` for those two is simpler and always shows the latest version, which is more useful for someone reading the template source
- Reduces the number of places where `${gitRef}` substitution matters

## Edge cases

- **Local/dev builds**: When building from a dirty working tree or a fork, `git log` may return a different commit. The fallback to `"HEAD"` (already in place) handles this gracefully.
- **Shallow clones**: If CI uses shallow clones, `git log -1 --format=%H -- <file>` might fail if the last change to the template is outside the shallow history. The existing `"HEAD"` fallback would apply. Alternatively, the build could deepen the clone or fall back to `HEAD`.
- **Build from tarball (no .git)**: Same as today — falls back to `"HEAD"` or `"unknown"`.
