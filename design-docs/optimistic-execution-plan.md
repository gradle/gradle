# Optimistic Execution: Research & Implementation Plan

## Concept

**Optimistic execution** means Gradle starts executing a cacheable task *in parallel* with requesting the cache entry (primarily from the remote cache). If the cache returns a hit before execution finishes, Gradle cancels the in-progress work and restores the cached result instead.

The goal: eliminate remote cache latency from the critical path. Instead of waiting for a remote cache response before deciding whether to execute, we race execution against the cache lookup.

---

## Current Architecture

### Execution Pipeline (Step Chain)

The execution engine uses a chain-of-responsibility pattern. Each `Step<C, R>` wraps the next. The pipeline is assembled in `ExecutionBuildServices.createExecutionEngine()`.

**Immutable pipeline** (e.g., dependency resolution artifacts):
```
AssignImmutableWorkspaceStep
  → MarkSnapshottingInputsStartedStep
    → CaptureImmutableStateBeforeExecutionStep
      → ValidateStep
        → ResolveImmutableCachingStateStep
          → MarkSnapshottingInputsFinishedStep
            → NeverUpToDateStep
              → BuildCacheStep              ← CACHE LOOKUP HERE
                → CaptureOutputsAfterExecutionStep
                  → BroadcastChangingOutputsStep
                    → PreCreateOutputParentsStep
                      → TimeoutStep
                        → CancelExecutionStep
                          → ExecuteStep     ← EXECUTION HERE
```

**Mutable pipeline** (tasks):
```
AssignMutableWorkspaceStep
  → HandleStaleOutputsStep
    → LoadPreviousExecutionStateStep
      → MarkSnapshottingInputsStartedStep
        → SkipEmptyMutableWorkStep
          → CaptureMutableStateBeforeExecutionStep
            → ValidateStep
              → ResolveChangesStep
                → ResolveMutableCachingStateStep
                  → MarkSnapshottingInputsFinishedStep
                    → SkipUpToDateStep
                      → StoreExecutionStateStep
                        → BuildCacheStep              ← CACHE LOOKUP HERE
                          → ResolveInputChangesStep
                            → CaptureOutputsAfterExecutionStep
                              → BroadcastChangingOutputsStep
                                → RemovePreviousOutputsStep
                                  → PreCreateOutputParentsStep
                                    → TimeoutStep
                                      → CancelExecutionStep
                                        → ExecuteStep ← EXECUTION HERE
```

### Key Observation

`BuildCacheStep` is the decision point. Today it:
1. Calls `buildCache.load(cacheKey, cacheableWork)` — **synchronously** (checks local, then remote)
2. If hit → returns cached result, never calls delegate (no execution)
3. If miss → calls `delegate.execute(work, context)` (runs the full sub-pipeline ending in `ExecuteStep`)
4. After execution → stores result in cache

The remote cache load (`DefaultBuildCacheController.loadRemoteAndStoreResultLocally`) is a blocking HTTP call. This is the latency we want to overlap with execution.

### Existing Cancellation Infrastructure

`CancelExecutionStep` already supports cancellation via `BuildCancellationToken`. It:
- Registers a thread interrupt callback before delegating
- Removes the callback after execution
- Checks if cancellation was requested

`TimeoutStep` similarly interrupts the execution thread after a timeout.

Both use **thread interruption** as the mechanism.

---

## Challenges & Constraints

### 1. Output Directory Conflict
The cache load and task execution both write to the **same output directory**. You cannot have both running simultaneously writing to the same files.

**Options:**
- **(a) Execute to a temporary workspace**, then discard if cache hits. Requires workspace indirection.
- **(b) Start execution but defer output writes** — not feasible for most tasks.
- **(c) Only race the remote cache lookup (download to temp file) against execution**, but defer unpacking. This avoids output conflicts since unpacking and execution don't overlap.
- **(d) Execute normally, but if cache hits first, interrupt execution, clean outputs, and restore from cache.**

Option **(c)** is the most practical starting point — race the *download* against execution, not the *unpack*.

### 2. Local Cache Short-Circuits the Need
The local cache is fast (filesystem read). Optimistic execution only makes sense for **remote-only** cache scenarios. If there's a local hit, just use it — no need to race.

### 3. Work That Cannot Be Cancelled
Some tasks may not respond to thread interruption (e.g., native process execution, tasks holding locks). Need a timeout/fallback: if execution doesn't cancel promptly, let it finish and discard the cache result.

### 4. Resource Waste
Optimistic execution consumes a worker thread (and CPU/IO) for work that may be discarded. This trades compute resources for wall-clock time. Should be opt-in or adaptive.

### 5. Cache Key Must Be Known Before Execution
The cache key computation happens in `ResolveImmutableCachingStateStep` / `ResolveMutableCachingStateStep`, which is upstream of `BuildCacheStep`. So the cache key **is** available before execution starts. ✅

### 6. Mutable Work & Incremental Execution
For mutable (incremental) tasks, `RemovePreviousOutputsStep` cleans outputs before execution. If we're racing execution against cache, we need to be careful about the output state. The cache restore would need to clean up whatever partial outputs the cancelled execution produced.

---

## Incremental Implementation Plan

### Phase 0: Async Remote Cache Lookup Infrastructure

**Goal:** Make remote cache lookups non-blocking without changing the execution flow.

**Changes:**
1. Add an async `load` method to `BuildCacheController`:
   ```java
   CompletableFuture<Optional<BuildCacheLoadResult>> loadAsync(
       BuildCacheKey cacheKey, CacheableEntity entity);
   ```
2. Implement in `DefaultBuildCacheController`: check local cache synchronously (fast), then submit remote lookup to a dedicated thread pool. The future completes when the remote response arrives and the entry is downloaded to a temp file (but NOT yet unpacked into the workspace).
3. Introduce `BuildCacheLoadResult` variant that holds a temp file reference and defers unpacking.

**Key files:**
- `BuildCacheController.java` — add async API
- `DefaultBuildCacheController.java` — implement async remote load
- `RemoteBuildCacheServiceHandle.java` / `OpFiringRemoteBuildCacheServiceHandle.java` — async download

### Phase 1: Optimistic Execution Step (Remote-Only Racing)

**Goal:** When there's no local cache hit but a remote cache *might* have the entry, start execution and the remote download concurrently.

**Changes:**
1. Create `OptimisticBuildCacheStep` (new step, replaces or wraps `BuildCacheStep`):
   ```
   execute(work, context):
     // 1. Check local cache synchronously
     localHit = checkLocalCache(cacheKey)
     if (localHit) return applyLocalHit()

     // 2. Start remote download in background
     remoteFuture = buildCache.loadRemoteAsync(cacheKey, cacheableWork)

     // 3. Start execution on current thread
     executionFuture = startExecution(work, context)  // or just call delegate

     // 4. Race: whichever completes first wins
     //    - If remote completes first with a HIT:
     //        cancel execution, unpack cache entry, return cached result
     //    - If remote completes first with a MISS:
     //        let execution continue, store result when done
     //    - If execution completes first:
     //        cancel remote wait (or let it finish for future use), store result
   ```

2. The "race" can be implemented as:
   - Execute the delegate pipeline in the current thread
   - Before calling `delegate.execute()`, register a callback on the remote future that interrupts the execution thread
   - This leverages the existing `CancelExecutionStep` / `TimeoutStep` interrupt-handling infrastructure

3. If execution is interrupted by a cache hit:
   - Clean partial outputs (use `Deleter` on output locations)
   - Unpack the cached entry into the workspace
   - Return the cached result

**Key files:**
- New: `OptimisticBuildCacheStep.java`
- Modified: `ExecutionBuildServices.java` — wire new step into pipeline
- Modified: `BuildCacheController.java` / `DefaultBuildCacheController.java` — split local vs remote loading

### Phase 2: Robust Cancellation & Cleanup

**Goal:** Handle edge cases around cancellation.

**Changes:**
1. Add a method to `UnitOfWork` or a new interface:
   ```java
   default boolean supportsOptimisticExecution() { return true; }
   ```
   Tasks that can't be safely interrupted (e.g., those with side effects beyond outputs) can opt out.

2. Implement cleanup of partial outputs when execution is cancelled mid-flight:
   - Use `work.getAllOutputLocationsForInvalidation()` to find and clean up partial files
   - Invalidate the VFS for those paths via `FileSystemAccess.invalidate()`

3. Add a timeout for cancellation: if the execution thread doesn't respond to interrupt within N seconds, let it finish and discard the cache result.

4. Handle the case where `ExecuteStep` catches `InterruptedException` — ensure the interrupt is distinguishable from build cancellation vs. optimistic cache hit.

### Phase 3: Configuration & Observability

**Goal:** Make it configurable and observable.

**Changes:**
1. Add a Gradle property to enable/disable optimistic execution:
   ```
   org.gradle.caching.optimistic=true
   ```
2. Add build operation / build scan events:
   - "Optimistic execution won" (execution finished before cache)
   - "Cache hit won" (execution cancelled, cache restored)
   - "Optimistic execution skipped" (local cache hit, no race needed)
3. Metrics: time saved by optimistic execution, wasted CPU time

### Phase 4: Adaptive Optimization

**Goal:** Automatically decide when optimistic execution is worthwhile.

**Changes:**
1. Track per-task history: typical execution time vs. typical remote cache latency
2. Only race when expected execution time > expected cache latency (otherwise just wait for cache)
3. Consider worker thread availability — don't optimistically execute if all workers are busy with real work

---

## Open Questions

1. **Workspace isolation:** Should optimistic execution use a separate temp workspace to avoid conflicts with cache unpacking? This would be cleaner but requires changing how outputs are handled in the pipeline (the workspace is assigned much earlier in the step chain).

2. **Worker lease management:** Optimistic execution consumes a worker lease. Should it use a separate "optimistic" lease pool, or count against the normal `--max-workers` limit? Using the normal pool means optimistic execution can starve real work.

3. **Incremental tasks:** For tasks using `InputChanges`, optimistic execution must run as non-incremental (since we might discard the result). Is it acceptable to lose incrementality for the optimistic path?

4. **Build scan integration:** How should optimistic execution appear in build scans? Need new event types.

5. **Interaction with `--no-parallel`:** Should optimistic execution be disabled when parallel execution is off?

6. **Remote cache that always misses:** If the remote cache consistently misses, optimistic execution adds no value but adds complexity. The adaptive approach (Phase 4) addresses this, but what's the default behavior?

7. **Interrupt safety of existing tasks:** Many existing Gradle tasks and plugins may not handle `InterruptedException` correctly. How do we audit/mitigate this?

8. **Cache entry download without unpacking:** The current `BuildCacheController.load()` downloads AND unpacks in one call. Splitting these is necessary for Phase 1 but changes the contract. Need to ensure the temp file is cleaned up in all paths.

9. **Multiple remote caches (Develocity):** With Develocity, there may be multiple remote cache nodes. How does optimistic execution interact with cache node selection and failover?

10. **Output snapshotting:** `CaptureOutputsAfterExecutionStep` snapshots outputs after execution. If execution is cancelled, we need to snapshot the cache-restored outputs instead. The current `BuildCacheStep` already handles this for normal cache hits — verify it works when the step below it was interrupted.

---

## Summary of Key Files

| File | Role |
|------|------|
| `ExecutionBuildServices.java` | Pipeline assembly — where steps are wired together |
| `BuildCacheStep.java` | Current cache check + execution orchestration |
| `BuildCacheController.java` | Cache controller interface (load/store) |
| `DefaultBuildCacheController.java` | Implementation: local check → remote check → store |
| `RemoteBuildCacheServiceHandle.java` | Remote cache operations interface |
| `OpFiringRemoteBuildCacheServiceHandle.java` | Remote cache operations with build operation tracking |
| `ExecuteStep.java` | Actual work execution |
| `CancelExecutionStep.java` | Interrupt-based cancellation infrastructure |
| `TimeoutStep.java` | Interrupt-based timeout infrastructure |
| `UnitOfWork.java` | Work unit interface — `execute()`, output visiting, etc. |
| `Step.java` | Step interface — `execute(UnitOfWork, Context)` |
