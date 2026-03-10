# Optimistic Execution: Implementation Plan (Immutable Work Only)

## Concept

**Optimistic execution** means Gradle starts executing an immutable work item *in parallel* with downloading its remote cache entry. Whichever finishes first wins — the other result is simply redundant.

The goal: eliminate remote cache latency from the critical path for artifact transforms, build script compilation, and dependency accessor generation.

## Why Immutable-Only Simplifies Everything

Immutable workspaces are **content-addressable**: the workspace path is derived from the hash of inputs. Both execution and cache restore produce *identical* outputs at the *same* location. This eliminates the hardest problems:

- **No output directory conflict.** Both paths write the same content to the same location.
- **No cancellation needed.** Let execution run to completion; if the cache download finishes first (or second), the result is the same. No thread interruption, no partial output cleanup.
- **No interrupt safety risk.** No need to audit whether work items handle `InterruptedException`.
- **No incremental execution concerns.** Immutable work uses `NeverUpToDateStep` — always fully cached or fully executed.
- **Existing concurrency support.** `CacheBasedImmutableWorkspaceProvider.getOrCompute()` already coordinates concurrent threads via `CompletableFuture` and file locks.

### Scope

Immutable work items (`ImmutableUnitOfWork`) include:
- **Artifact transforms** (`ImmutableTransformExecution`)
- **Build script compilation** (`BuildScriptCompilationAndInstrumentation`)
- **Dependency accessors** (`AbstractAccessorUnitOfWork`)

Mutable tasks are explicitly **out of scope** for this plan.

---

## Current Architecture

### Immutable Execution Pipeline (Step Chain)

```
AssignImmutableWorkspaceStep          ← workspace via content-addressable hash
  → MarkSnapshottingInputsStartedStep
    → CaptureImmutableStateBeforeExecutionStep
      → ValidateStep
        → ResolveImmutableCachingStateStep  ← cache key computed here
          → MarkSnapshottingInputsFinishedStep
            → NeverUpToDateStep
              → BuildCacheStep              ← CACHE LOOKUP HERE (sync)
                → CaptureOutputsAfterExecutionStep
                  → BroadcastChangingOutputsStep
                    → PreCreateOutputParentsStep
                      → TimeoutStep
                        → CancelExecutionStep
                          → ExecuteStep     ← EXECUTION HERE
```

### Key Observation

`BuildCacheStep` today:
1. Calls `buildCache.load(cacheKey, cacheableWork)` — **synchronously** (checks local, then remote)
2. If hit → returns cached result, never calls delegate
3. If miss → calls `delegate.execute()` (runs the full sub-pipeline ending in `ExecuteStep`)
4. After execution → stores result in cache

The remote cache load is a blocking HTTP call. This is the latency we want to overlap with execution.

### Immutable Workspace Concurrency

`AssignImmutableWorkspaceStep` and `CacheBasedImmutableWorkspaceProvider` already handle concurrent access:
- Per-workspace file locks via `FineGrainedPersistentCache`
- Double-checked locking: if another thread completed the workspace while waiting for the lock, skip execution
- `CompletableFuture`-based coordination between threads targeting the same workspace
- Output hash verification via `metadata.bin` ensures workspace integrity

---

## Constraints

### 1. Local Cache Short-Circuits the Need
The local cache is a fast filesystem read. Optimistic execution only applies when the **local cache misses** and a remote cache is configured.

### 2. Resource Usage
Optimistic execution consumes a worker thread for work that may be redundant. Should only race when there are idle workers available.

### 3. Cache Key Availability
The cache key is computed in `ResolveImmutableCachingStateStep`, upstream of `BuildCacheStep`. It **is** available before execution starts.

### 4. File Lock Coordination
`AssignImmutableWorkspaceStep` acquires a per-workspace file lock. The async remote download and execution must not deadlock on this lock. Since `CacheBasedImmutableWorkspaceProvider` already handles concurrent completion, this should integrate naturally — but needs verification.

---

## Implementation Plan

### Phase 1: Async Remote Cache Download

**Goal:** Make remote cache lookups non-blocking without changing execution flow.

**Changes:**
1. Add an async remote-only load method to `BuildCacheController`:
   ```java
   CompletableFuture<Optional<BuildCacheLoadResult>> loadRemoteAsync(
       BuildCacheKey cacheKey, CacheableEntity entity);
   ```
2. Implement in `DefaultBuildCacheController`: submit remote lookup to a thread pool. The future completes when the remote response arrives and the entry is downloaded to a temp location (but NOT yet unpacked into the workspace).
3. Local cache check remains synchronous (it's fast).

**Key files:**
- `BuildCacheController.java` — add async API
- `DefaultBuildCacheController.java` — implement async remote load
- `RemoteBuildCacheServiceHandle.java` / `OpFiringRemoteBuildCacheServiceHandle.java` — async download

### Phase 2: Optimistic Execution for Immutable Work

**Goal:** When there's no local cache hit, race remote download against execution for immutable work items.

**Changes:**
1. Modify `BuildCacheStep` (or create a wrapping step) to handle immutable work differently:
   ```
   execute(work, context):
     // 1. Check local cache synchronously
     localHit = checkLocalCache(cacheKey)
     if (localHit) return applyLocalHit()

     // 2. Check worker availability — only race if idle workers exist
     if (!hasIdleWorkers()) return normalFlow()

     // 3. Start remote download in background
     remoteFuture = buildCache.loadRemoteAsync(cacheKey, entity)

     // 4. Execute on current thread (calls delegate)
     result = delegate.execute(work, context)

     // 5. Execution won — cancel or ignore the remote download
     remoteFuture.cancel(false)

     // 6. Store result in cache as normal
     storeInCache(result)
     return result
   ```

   The key insight: since immutable workspaces are content-addressable, if the remote download completes while execution is running, the workspace provider's existing concurrency handling takes care of it. If execution finishes first, the remote result is simply discarded. **No special cleanup needed.**

2. Wire the new behavior into the immutable pipeline only, gated by whether `work instanceof ImmutableUnitOfWork`.

3. Gate on idle worker availability to avoid starving real work.

**Key files:**
- `BuildCacheStep.java` — add optimistic path for immutable work
- `ExecutionBuildServices.java` — wire configuration
- `CacheBasedImmutableWorkspaceProvider.java` — verify concurrent access works correctly

### Phase 3: Configuration & Observability

**Goal:** Make it configurable and observable.

**Changes:**
1. Add a Gradle property to enable/disable:
   ```
   org.gradle.caching.optimistic=true
   ```
2. Add build operation events:
   - "Optimistic execution: execution won" (execution finished before remote cache)
   - "Optimistic execution: remote cache won" (remote result arrived first, execution was redundant)
   - "Optimistic execution: skipped" (local cache hit, no race needed)
   - "Optimistic execution: not attempted" (no idle workers)
3. Metrics: time saved, redundant CPU time spent

### Future: Extend to Mutable Tasks

Once the immutable path is proven, extending to mutable tasks would require addressing:
- Output directory conflicts (tasks and cache write to the same mutable workspace)
- Thread interruption safety for arbitrary task code
- Incremental execution (`InputChanges`) compatibility
- `StoreExecutionStateStep` and VFS consistency after cancellation
- `RemovePreviousOutputsStep` interaction with partial outputs

This is significantly more complex and is deferred.

---

## Open Questions

1. **Worker availability heuristic:** What's the right threshold? "At least one idle worker" or some percentage of `--max-workers`?

2. **File lock interaction:** Verify that starting both remote download and execution concurrently doesn't cause deadlock on the per-workspace file lock in `AssignImmutableWorkspaceStep`. The workspace provider's `CompletableFuture` coordination should handle this, but needs testing.

3. **Build scan integration:** How should optimistic execution appear in build scans? Need new event types.

4. **`--no-parallel` interaction:** Should optimistic execution be disabled when parallel execution is off?

5. **Multiple remote caches (Develocity):** How does optimistic execution interact with cache node selection and failover?

6. **Thread pool sizing:** What thread pool should async remote downloads use? A dedicated pool avoids contention with worker threads.

---

## Summary of Key Files

| File | Role |
|------|------|
| `ExecutionBuildServices.java` | Pipeline assembly — where steps are wired together |
| `BuildCacheStep.java` | Current cache check + execution orchestration |
| `BuildCacheController.java` | Cache controller interface (load/store) |
| `DefaultBuildCacheController.java` | Implementation: local check → remote check → store |
| `RemoteBuildCacheServiceHandle.java` | Remote cache operations interface |
| `OpFiringRemoteBuildCacheServiceHandle.java` | Remote cache ops with build operation tracking |
| `CacheBasedImmutableWorkspaceProvider.java` | Immutable workspace concurrency coordination |
| `AssignImmutableWorkspaceStep.java` | Immutable workspace assignment + file locking |
| `ImmutableUnitOfWork.java` | Marker interface for immutable work items |
| `ExecuteStep.java` | Actual work execution |
| `Step.java` | Step interface — `execute(UnitOfWork, Context)` |
