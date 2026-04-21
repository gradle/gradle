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

With the fix, a worker with no available lease **blocks before polling** any
operation. Operations therefore remain in the queue and are drained by the
initiating thread (which already holds a lease) during its own `runOperations`
pass inside `waitForCompletion`. There are no "polled but unrunnable"
operations in the new model — a worker either holds a lease and makes progress,
or is blocked in `runAsWorkerThread` and has not touched the queue.

When leases are available, workers run essentially as before; the only
difference is that lease acquisition is lifted outside the per-batch loop.

### Interaction with `OWNING_WORKER_THREAD_POOL`

The initiating thread calls `new WorkerRunnable(null, parent).runOperations()`
directly from `waitForCompletion()` — it does **not** go through
`WorkerRunnable.run()`, so `OWNING_WORKER_THREAD_POOL` is never set on that
thread. This is a pre-existing property of the code and is preserved by the
fix. It is safe because:

- The initiating thread already holds a worker lease, so
  `runAsWorkerThread`'s "already a worker" short-circuit
  (`DefaultWorkerLeaseService` line ~125) fires and the body runs
  synchronously without entering the lease-acquisition path that consults
  `OWNING_WORKER_THREAD_POOL`.
- `withoutLocksBlocking` (which reads `OWNING_WORKER_THREAD_POOL` to call
  `notifyBlockingWorkStarting`) is triggered by `workerLeases.blocking(...)`
  inside `waitForWorkToComplete`. Before the fix, the initiating thread
  relied on releasing its lease here to let queue workers make progress.
  **After the fix, the initiating thread should no longer need to release
  its lease in the common case** — it drains the queue itself. The call to
  `blocking(...)` remains as a safety net for the degenerate case where a
  spawned worker did manage to grab the lease and is running an operation
  while the initiating thread waits; in that case the null
  `OWNING_WORKER_THREAD_POOL` means no extra worker will be spawned via
  `notifyBlockingWorkStarting`, which is the same behaviour as before
  PR #36897.

Spawned workers (the `WorkerRunnable.run()` path) continue to set
`OWNING_WORKER_THREAD_POOL` as they do today.

### Tradeoff

A worker that acquires a lease and then finds the queue empty but still in
`Working` state will wait on `workAvailable.await()` while holding the lease.
In the current code, the lease is released between batches. This could cause
minor under-utilization when the `schedulingAction` adds work slowly AND there
is lease contention from other subsystems.

This is acceptable for `DefaultBuildOperationQueue` because:
- The queue is short-lived (one `runAll` call, one completion).
- The common pattern is `runAll(schedulingAction)` where the action adds all
  work items synchronously before `waitForCompletion` is called; workers rarely
  observe the queue empty while the state is still `Working`.
- Non-blocking under-utilization is preferable to a deterministic deadlock.

Any follow-up that releases the lease while idle (for example, via
`workerLeases.blocking(...)` inside `waitForNextOperation`) must first ensure
that `OWNING_WORKER_THREAD_POOL` is set on every thread that reaches the
wait — including the initiating thread — otherwise the `notifyBlockingWorkStarting`
compensation would silently no-op and risk reintroducing the original
deadlock. That refinement is deferred.

### Not changing `DefaultConditionalExecutionQueue`

`DefaultConditionalExecutionQueue` has a superficially similar `runBatch`
pattern (lease acquired inside `runBatch`, after polling), but we are
deliberately **not** applying the same fix in this change:

- There is no confirmed deadlock report for that queue.
- It is a long-lived service (2-second keep-alive) rather than a short-lived
  per-`runAll` queue, so holding a lease through idle waits would have a
  materially different impact.
- The `isExtraWorker()` / worker-count-reduction logic interacts with the
  inner-loop `runAsWorkerThread` call in ways that need separate analysis.

If symmetry is desired, it should be tracked as a follow-up investigation.

## Testing Strategy

### Unit test (red-first)

Add a regression test in `DefaultBuildOperationQueueTest` (existing class, in
`subprojects/core/src/test/groovy/org/gradle/internal/operations/`). Use a
real `DefaultWorkerLeaseService` saturated with leases held by external
threads — **not** a fake `WorkerLeaseService`. A Spock mock cannot naturally
replicate the "already a worker" short-circuit in `runAsWorkerThread` because
that short-circuit depends on whether the calling thread holds a lock in
`workerLeaseLockRegistry`.

Pattern (modelled on existing `SynchronizedBuildOperation` usage and
`MaxWorkersTest`):

- Create a `DefaultWorkerLeaseService` with `maxWorkers=2`.
- Start two external threads that each call `runAsWorkerThread` and block on
  a latch, saturating the lease pool.
- From a third thread that already holds a lease, create a
  `DefaultBuildOperationQueue` with `requiresWorkerLease=true`, submit N
  operations, and call `waitForCompletion` with a generous test timeout
  (`@Timeout` or `PollingConditions`).
- Release the external-thread latches only after `waitForCompletion` returns.
- Assert: all N operations executed (the count / side-effect list is complete)
  and `waitForCompletion` returned before the timeout.

Before the fix this test should deadlock (caught by the timeout). After the
fix it should pass because the initiating thread drains every queued
operation itself.

### Integration test

Add a forking integration test that mirrors the real scenario but without
relying on the fragile shaded-jar cache path (the cache is aggressive and
would hit on second run). A simpler and stable approach:

- Location: `subprojects/core/src/integTest/groovy/org/gradle/internal/operations/`
  (not `base-services`; `DefaultBuildOperationQueue` lives in `:core`).
- Use a build that deliberately saturates worker leases from one path while
  another path invokes `BuildOperationRunner.runAll` with
  `requiresWorkerLease=true`. Model on `MaxWorkersTest` structure.
- Assert the build completes within a timeout (use a Spock `@Timeout` or the
  integration-test harness equivalent) rather than relying on an overall
  build hang to fail the test.
- Run under both `forkingIntegTest` and `configCacheIntegTest` to verify CC
  compatibility.
- Link with `@Issue("https://github.com/gradle/gradle/issues/<N>")` once a
  public issue is filed (DV issue 64898 is internal).

### Manual verification

Reproduce on a composite build that was previously failing the nightly DV
test `BuildCacheActivityPluginFuncTest.build cache identifier of remote build
cache disabled event is properly captured`. If re-running the exact
composite-build repro is impractical locally, confirm via a developer build
against a composite project with many included builds at `--max-workers=3`.

### Known residual gaps to watch for

These are flagged by the plan critique and addressed by existing tests or
by careful review rather than by adding dedicated coverage in this change:

- Cancellation mid-run: the existing cancel path in `doRunBatch` (discards a
  polled operation when `queueState == Cancelled`) is preserved because the
  worker still polls via the same `waitForNextOperation`/`doRunBatch` path.
  Verify via existing cancellation tests in `DefaultBuildOperationQueueTest`.
- `isExtraWorker()` early-exit: unchanged — still fires inside
  `executePendingWork`. Covered by the existing parallel tests; worth a
  review pass rather than a new test in this change.
- Configuration-cache callers of `runAll(..., requiresWorkerLease=true)`:
  the `configCacheIntegTest` run on the new integration test exercises one
  path; broader callers are already covered by CC integration tests.

## Rollback Strategy

If CI reveals lease-starvation or worker-underutilization regressions after
this change, the fix can be reverted independently. The fallback is a
partial revert of PR #36897: set `requiresWorkerLease=false` (or remove the
lease-acquisition call entirely) in `DefaultBuildOperationQueue`, which
restores pre-#36897 behaviour at the cost of losing the lease-pool
integration. That fallback is a separate, larger decision; this plan's
change is the minimal, targeted fix.

## Implementation Checklist

- [ ] Write failing unit test reproducing stuck-operation scenario (real
      `DefaultWorkerLeaseService` + saturated external threads + latches)
- [ ] Modify `DefaultBuildOperationQueue.runOperations` / `runBatch` to
      acquire the worker lease around the full worker loop
- [ ] Re-run existing `DefaultBuildOperationQueueTest`, `MaxWorkersTest`,
      `DefaultBuildOperationExecutorParallelExecutionTest`
- [ ] Write failing integration test (`:core` integTest, `MaxWorkersTest`
      style) reproducing worker-lease starvation during `runAll`
- [ ] Verify both tests pass after the fix
- [ ] Run `./gradlew :core:test`
- [ ] Run `./gradlew :core:forkingIntegTest --tests "*BuildOperation*"` and
      `./gradlew :core:configCacheIntegTest --tests "*BuildOperation*"`
- [ ] Run `./gradlew sanityCheck`

## Out of Scope

- Applying a similar change to `DefaultConditionalExecutionQueue` — deferred
  as a separate investigation (different lifetime, no confirmed bug).
- Redesigning the cache lock / shaded-jar generation interaction
  (`DefaultCacheCoordinator.useCache` holds its lock across the full
  computation). This is a broader concern that predates PR #36897.
- Adding `tryRunAsWorkerThread` or priority-inversion-aware lease
  scheduling. Larger changes, not required for the minimal fix.
- Releasing the lease during idle waits in `waitForNextOperation`. Would
  require also ensuring `OWNING_WORKER_THREAD_POOL` is set on the
  initiating thread; deferred.
