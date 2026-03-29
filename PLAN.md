# Fast Up-To-Date Check Step — Improved Design

## Context

Gradle's no-op build for a single module takes ~1.0s. The root cause: Gradle runs a ~15-step execution pipeline per task even when UP-TO-DATE, with `CaptureMutableStateBeforeExecutionStep` (fingerprint ALL inputs + snapshot outputs) costing ~50-200ms per task. A previous attempt (PR #36971) added `FastUpToDateCheckStep` but underperformed due to two key design issues.

## Why PR #36971 Didn't Work

**Issue 1: `anyTaskTouched` global kill switch.** A single boolean that disables fast-path for ALL subsequent tasks once ANY task executes. In a 22-module incremental build (1 module changed), ~5 upstream tasks benefit but ~16 downstream/unrelated tasks lose the fast-path even though their inputs are unchanged.

**Issue 2: Step positioned too deep.** Placed after `SkipEmptyMutableWorkStep`, which already fingerprints primary inputs (~10-30ms wasted per task). Steps 1-9 already cost ~30-80ms before the fast-path check runs.

## Plan

### Change 1: Replace `anyTaskTouched` with precise output root tracking

Instead of a global boolean, track the **set of output root paths** produced by tasks that actually executed. For each subsequent task, check if its **input root paths** (from previous execution state) overlap with any produced output root.

Example: Task A outputs to `/projectA/build/classes/`. Task B inputs from `/projectB/build/classes/`. No overlap → B can still fast-path even though A executed.

Use `FileCollectionFingerprint.getRootHashes().keySet()` for input roots and `FileSystemSnapshot` root paths for output roots. Overlap check is O(inputRoots × producedOutputRoots) — typically ~30 string prefix comparisons, <1ms.

### Change 2: Move step before `SkipEmptyMutableWorkStep`

New pipeline position (after `LoadPreviousExecutionStateStep`, before `MarkSnapshottingInputsStartedStep`):

```
AssignMutableWorkspaceStep              (~5-10ms, needed for workspace)
  HandleStaleOutputsStep               (~0-5ms, cheap in common case)
    LoadPreviousExecutionStateStep      (~5-10ms, needed for previous state)
      ★ FastUpToDateCheckStep           <-- NEW POSITION
        MarkSnapshottingInputsStartedStep
          SkipEmptyMutableWorkStep      (~10-30ms, SKIPPED by fast-path)
            CaptureMutableStateBeforeExecutionStep  (~50-200ms, SKIPPED)
              ...rest of pipeline...
```

Per-task overhead before fast-path: ~15-25ms.
Per-task savings from fast-path: ~60-230ms (skipping SkipEmpty + CaptureState + everything below).

### Change 3: Handle `--rerun-tasks`

Check `context.getNonIncrementalReason()` (available via `ExecutionRequestContext` in the context chain). If present, skip fast-path.

## Files to Modify

### New: `FastUpToDateCheckState.java`
`platforms/core-execution/execution/src/main/java/org/gradle/internal/execution/steps/FastUpToDateCheckState.java`

- `@ServiceScope(Scope.UserHome.class)` — persists across builds in daemon
- `changedPaths: Set<String>` (ConcurrentHashMap.newKeySet()) — VFS file changes between builds
- `producedOutputRootPaths: Set<String>` (ConcurrentHashMap.newKeySet()) — output roots from executed tasks in current build
- `configurationCacheHit: volatile boolean`
- `watchingFileSystem: volatile boolean`
- `watchingError: volatile boolean`
- Methods:
  - `recordChange(Path)` — called by FileChangeListener
  - `recordProducedOutputRoots(Set<String>)` — called after task executes
  - `isFastPathEnabled()` → CC hit && watching && !error
  - `hasVfsChangesOverlappingWith(Set<String> rootPaths)` — check changedPaths vs task roots
  - `hasProducedOutputsOverlappingWith(Set<String> inputRootPaths)` — check produced outputs vs task inputs
  - `resetForBuildStart()` — clears producedOutputRootPaths, resets flags
  - `clearChangedPaths()` — called after build completes

Path overlap: `path1.equals(path2) || path2.startsWith(path1 + '/') || path1.startsWith(path2 + '/')`

### New: `FastUpToDateCheckLifecycle.java`
`platforms/core-execution/execution/src/main/java/org/gradle/internal/execution/steps/FastUpToDateCheckLifecycle.java`

Interface with `resetForBuildStart()`, `setConfigurationCacheHit(boolean)`, `clearChangedPaths()`.

### New: `FastUpToDateCheckStep.java`
`platforms/core-execution/execution/src/main/java/org/gradle/internal/execution/steps/FastUpToDateCheckStep.java`

`Step<PreviousExecutionContext, CachingResult>` — generic bounds match position between `LoadPreviousExecutionStateStep` and `MarkSnapshottingInputsStartedStep`.

Fast-path logic:
1. Check `state.isFastPathEnabled()` and `context.getNonIncrementalReason().isEmpty()`
2. Get `previousState` from context, check `isSuccessful()`
3. Extract input roots: `previousState.getInputFileProperties().values() → fp.getRootHashes().keySet()`
4. Extract output roots: `previousState.getOutputFilesProducedByWork().values() → snapshot roots`
5. Check `!state.hasVfsChangesOverlappingWith(inputRoots ∪ outputRoots)`
6. Check `!state.hasProducedOutputsOverlappingWith(inputRoots)`
7. If all pass: return `CachingResult.shortcutResult(...)` mirroring `SkipUpToDateStep.skipExecution()`

After delegate returns (non-fast-path): if task executed (not UP_TO_DATE/SHORT_CIRCUITED), call `state.recordProducedOutputRoots(outputRoots)` using the result's `afterExecutionOutputState`.

Return value on fast-path (mirrors `SkipUpToDateStep.skipExecution()` at line 56-70):
```java
ExecutionOutputState outputState = new DefaultExecutionOutputState(
    true, previousState.getOutputFilesProducedByWork(),
    previousState.getOriginMetadata(), true);
CachingResult.shortcutResult(
    previousState.getOriginMetadata().getExecutionTime(),
    Execution.skipped(UP_TO_DATE, work),
    outputState, null, previousState.getOriginMetadata());
```

### Modify: `ExecutionBuildServices.java:188-210`
`subprojects/core/src/main/java/org/gradle/internal/service/scopes/ExecutionBuildServices.java`

Add `FastUpToDateCheckState` parameter. Insert step into mutable pipeline:
```java
new LoadPreviousExecutionStateStep<>(
new FastUpToDateCheckStep<>(fastUpToDateCheckState,   // ADD
new MarkSnapshottingInputsStartedStep<>(
new SkipEmptyMutableWorkStep(...,
```

### Modify: `GradleUserHomeScopeServices.java`
`subprojects/core/src/main/java/org/gradle/internal/service/scopes/GradleUserHomeScopeServices.java`

Add `@Provides` method creating `FastUpToDateCheckState` and registering a `FileChangeListener` that calls `state.recordChange(path)` and `state.stopWatchingAfterError()`.

### Modify: `ConfigurationCacheAwareBuildTreeWorkController.kt`
`platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/ConfigurationCacheAwareBuildTreeWorkController.kt`

- Inject `FastUpToDateCheckLifecycle`
- Call `resetForBuildStart()` at start
- Call `setConfigurationCacheHit(true)` on CC hit
- Call `clearChangedPaths()` after execution

### Modify: `ConfigurationCacheBuildTreeLifecycleControllerFactory.kt`
Pass `FastUpToDateCheckLifecycle` through to the work controller.

### Modify: `configuration-cache/build.gradle.kts`
Add `api(projects.execution)` dependency (needed for `FastUpToDateCheckLifecycle` type in public API).

## Safety

The fast-path is conservative — any doubt falls through to normal pipeline:
- **CC hit required**: Without CC, task configs might change. CC guarantees value inputs + implementation unchanged.
- **VFS watching required**: Without it, we have no change information.
- **Previous successful execution required**: No fast-path on first build or after failed execution.
- **VFS overflow/error**: Sets `watchingError=true`, disables fast-path entirely.
- **Deleted source files**: VFS reports deletions, which overlap with input roots → no fast-path.
- **`--rerun-tasks`**: `getNonIncrementalReason()` is non-null → no fast-path.
- **Parallel execution**: `producedOutputRootPaths` uses ConcurrentHashMap.newKeySet(). Topological ordering guarantees task A completes (and records outputs) before dependent task B starts.

## Verification

1. Run existing tests: `./gradlew :execution:test` and `:core:test`
2. Manual benchmark on a multi-project build with CC + file watching:
   - No-op: `gradle compileJava` twice, second should be ~0.15-0.20s
   - Incremental: edit 1 file, `gradle compileJava`, check time
3. Run with `--rerun-tasks` to verify fast-path is disabled
4. Run without CC to verify fast-path is disabled
5. Integration test: create a multi-project setup, verify UP-TO-DATE behavior is correct with the fast-path
