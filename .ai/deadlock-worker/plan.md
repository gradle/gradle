# Fix Deadlock Between Cache Lock and Worker Leases

Related issue: https://github.com/gradle/dv/issues/64898
Regression introduced by: https://github.com/gradle/gradle/pull/36897 ("Implement WorkerThreadPool for DefaultBuildOperationQueue")

## Problem

A deadlock can occur in composite builds with many included builds when multiple
threads concurrently trigger the generation of the shaded Gradle API JAR
(`RuntimeShadedJarFactory`).

### Scenario (from DV issue 64898 thread dump, `--max-workers=3`, 10 included builds)

1. Thread 9 acquires the `DefaultCacheCoordinator` lock for the shaded-jar cache
   and, inside the locked region, calls
   `RuntimeShadedJarCreator.processFiles → buildOperationExecutor.runAll`.
2. `runAll` creates a `DefaultBuildOperationQueue` with
   `requiresWorkerLease = true` (`MAX_WORKERS` constraint).
3. Thread 9 adds the shading work items and spawns "Build operations" worker
   threads (up to `maxWorkerTasks = max-workers − 1 = 2`).
4. Each spawned worker calls `runBatch → workerLeases.runAsWorkerThread(...)`,
   after having already polled an operation from the queue, and blocks waiting
   for a worker lease.
5. Threads 2 and 3 hold two of the three worker leases and are blocked waiting
   for Thread 9's cache lock.
6. Thread 9 enters `waitForWorkToComplete → workerLeases.blocking(...)` which
   releases its worker lease.
7. The released lease is handed out by `Object.notifyAll()` racing — it can be
   granted to any of: the "Build operations" workers (desirable) or one of the
   other 7 included-build threads waiting on `runAsWorkerThread` for their build
   (which will then try to acquire the same cache lock and block).
8. When the lease goes to an included-build thread, all 3 leases end up held by
   threads blocked on Thread 9's cache lock. The "Build operations" workers
   never acquire a lease. Thread 9 waits forever for `pendingOperations == 0`.
   **Deadlock.**

### Why this is new

Before PR #36897, `DefaultBuildOperationQueue` workers did not acquire worker
leases to run operations. They ran immediately on the "Build operations"
executor, regardless of lease pressure. The deadlock could not happen because
the shading workers could always make progress.

After PR #36897, queue workers poll an operation first, then call
`runAsWorkerThread` to acquire a lease. If the lease cannot be acquired, the
polled operation is stuck in the worker — the queue initiator (Thread 9)
cannot pick it up because it was already removed from the queue.

## Proposed Fix

Move the worker-lease acquisition to wrap the entire worker loop
(`runOperations`), not just each batch. The worker acquires its lease **before
polling any work**; it never holds operations it cannot run.

### Diff sketch (`subprojects/core/src/main/java/org/gradle/internal/operations/DefaultBuildOperationQueue.java`)

```java
private void runOperations() {
    CurrentBuildOperationRef.instance().with(parent, () -> {
        try {
            if (context.requiresWorkerLease()) {
                workerLeases.runAsWorkerThread((Runnable) this::pollAndRunOperations);
            } else {
                pollAndRunOperations();
            }
        } catch (Throwable t) {
            addFailure(t);
        } finally {
            invalidateIfNeeded();
        }
    });
}

private void pollAndRunOperations() {
    T operation;
    while ((operation = waitForNextOperation()) != null) {
        runBatch(operation);
    }
}

private void runBatch(final T firstOperation) {
    int operationsExecuted = executePendingWork(firstOperation);
    completeOperations(operationsExecuted);
}
```

### Why this works

With the fix:
- If a lease is not available, the worker blocks in `runAsWorkerThread` **before**
  calling `pollWork`. Operations remain in the queue and can be picked up by
  the queue initiator (which already holds a lease) in its own `runOperations`
  pass during `waitForCompletion`.
- The initiator drains what it can, then `waitForWorkToComplete` sees
  `pendingOperations == 0` (since every polled op was either completed by the
  initiator or never polled by a stuck worker) and returns without needing to
  release its lease at all in the common case.
- When the lease IS available, workers run as before, just with the lease
  acquisition lifted outside the per-batch loop.

### Tradeoff

A worker that acquires a lease and then finds the queue empty but still in
`Working` state will wait on `workAvailable.await()` while holding the lease.
In the current code, the lease is released between batches. This could cause
minor under-utilization when the `schedulingAction` adds work slowly AND there
is lease contention from other subsystems.

This is considered acceptable because:
- The common pattern is `runAll(schedulingAction)` where the action adds all
  work items synchronously before `waitForCompletion` is called; workers rarely
  observe the queue empty while the state is still `Working`.
- Non-blocking under-utilization is preferable to a deterministic deadlock.
- If the tradeoff proves problematic, a follow-up can refine `waitForNextOperation`
  to release the lease via `workerLeases.blocking(...)` while awaiting new work.

### Apply the same change to `DefaultConditionalExecutionQueue`

`DefaultConditionalExecutionQueue.ExecutionRunner.runBatch` has the same
pattern (lease inside `runBatch`, after the op has been polled). Same fix
should be applied there for symmetry, though there is no confirmed deadlock
report for that queue.

## Testing Strategy

### Unit test (red-first)

Add a test in `DefaultBuildOperationQueueTest` (or a new test class) that
reproduces the invariant: when all worker leases are unavailable, operations
must not become stuck in worker threads. Specifically:
- Spin up a `DefaultBuildOperationQueue` with a mock `WorkerLeaseService` that
  causes `runAsWorkerThread` to block indefinitely from the spawned worker
  threads (but returns immediately for the caller thread that already holds a
  lease).
- Add N operations.
- Call `waitForCompletion` from the main thread.
- Expect: all operations run on the main thread; `waitForCompletion` returns
  without deadlock.

### Integration test

Add an integration test that triggers the exact scenario:
- A composite build with N >= 10 included builds, each resolving a
  classpath that triggers shaded-jar generation.
- `--max-workers=3` (or equivalent).
- Assert the build completes (rather than hanging).

The test should live in `platforms/core-runtime/base-services/src/integTest/` or
in composite-builds integration tests, using the existing
`ToBeFixedForConfigurationCache`/standard harness. Include both `forkingIntegTest`
and `configCacheIntegTest` runs to verify CC compatibility.

Use `@Issue("https://github.com/gradle/gradle/issues/<N>")` to link to the
public Gradle issue (if/when filed; DV issue 64898 is internal).

### Manual verification

Reproduce on a composite build that was previously failing the nightly DV test
`BuildCacheActivityPluginFuncTest.build cache identifier of remote build cache
disabled event is properly captured`.

## Implementation Checklist

- [ ] Write failing unit test reproducing stuck-operation scenario
- [ ] Modify `DefaultBuildOperationQueue.runOperations` / `runBatch` to acquire
      lease around the full loop
- [ ] Modify `DefaultConditionalExecutionQueue.ExecutionRunner` analogously
- [ ] Re-run existing `DefaultBuildOperationQueueTest`, `MaxWorkersTest`,
      `DefaultBuildOperationExecutorParallelExecutionTest`,
      `DefaultConditionalExecutionQueueTest`
- [ ] Write failing integration test reproducing the composite-build deadlock
- [ ] Verify both tests pass after the fix
- [ ] Run `./gradlew :core:test` and `./gradlew :base-services:test`
- [ ] Run `./gradlew :core:forkingIntegTest --tests "*BuildOperation*"` and
      `./gradlew :core:configCacheIntegTest --tests "*BuildOperation*"`
- [ ] Run `./gradlew sanityCheck`

## Out of Scope

- Redesigning the cache lock / shaded-jar generation interaction
  (`DefaultCacheCoordinator.useCache` holds its lock across the full
  computation). This is a broader concern that predates PR #36897.
- Adding a `tryRunAsWorkerThread` or priority-inversion-aware lease scheduling.
  These are larger changes and not required for the minimal fix.
