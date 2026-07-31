/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations

import org.gradle.api.GradleException
import org.gradle.internal.Factory
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.internal.work.ResourceLockStatistics
import org.gradle.internal.work.WorkerLeaseQueueProcessor
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DefaultBuildOperationQueueTest extends Specification {

    public static final String LOG_LOCATION = "<log location>"
    abstract static class TestBuildOperation implements RunnableBuildOperation {
        BuildOperationDescriptor.Builder description() { BuildOperationDescriptor.displayName(toString()) }

        String toString() { getClass().simpleName }
    }

    static class Success extends TestBuildOperation {
        void run(BuildOperationContext buildOperationContext) {
            // do nothing
        }
    }

    static class Failure extends TestBuildOperation {
        void run(BuildOperationContext buildOperationContext) {
            throw new BuildOperationFailure(this, "always fails")
        }
    }

    static class SimpleWorker implements BuildOperationQueue.QueueWorker<TestBuildOperation> {
        void execute(TestBuildOperation run) {
            run.run(null)
        }

        String getDisplayName() {
            return getClass().simpleName
        }
    }

    ResourceLockCoordinationService coordinationService
    BuildOperationQueue operationQueue
    WorkerLeaseService workerRegistry
    WorkerLeaseRegistry.WorkerLeaseCompletion lease
    WorkerLeaseQueueProcessor workerLeaseProcessor
    ExecutorService backingExecutor
    ExecutorService unconstrainedExecutor

    void setupQueue(int threads) {
        setupQueue(threads, Executors.newCachedThreadPool())
    }

    void setupQueue(int threads, ExecutorService unconstrainedPool) {
        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(threads), ResourceLockStatistics.NO_OP) {}
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()

        backingExecutor = Executors.newCachedThreadPool()
        workerLeaseProcessor = new WorkerLeaseQueueProcessor(coordinationService, workerRegistry, backingExecutor, threads, threads * 2)
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, workerLeaseProcessor.createSubmissionQueue(), newUnconstrainedExecutor(unconstrainedPool), new SimpleWorker(), null)
    }

    /**
     * A pool for unconstrained work, mirroring how DefaultBuildOperationExecutor wires one up.
     * Deliberately independent of the worker leases.
     */
    ExecutorService newUnconstrainedExecutor(ExecutorService pool = Executors.newCachedThreadPool()) {
        unconstrainedExecutor = pool
        return pool
    }

    def cleanup() {
        // ORDER MATTERS. shutdown() flips shouldContinue but does not call notifyStateChange,
        // so any starved worker still blocked acquiring a lease will not wake from shutdown alone.
        // Releasing the lease next triggers notifyStateChange, the worker re-evaluates
        // shouldContinue (now false), and exits its lock-acquisition retry loop.
        workerLeaseProcessor?.shutdown()
        lease?.leaseFinish()
        backingExecutor?.shutdown()
        backingExecutor?.awaitTermination(15, TimeUnit.SECONDS)
        unconstrainedExecutor?.shutdown()
        unconstrainedExecutor?.awaitTermination(15, TimeUnit.SECONDS)
        workerRegistry?.stop()
    }

    def "executes all #runs operations in #threads threads"() {
        given:
        setupQueue(threads)
        def success = Mock(TestBuildOperation)

        when:
        runs.times { operationQueue.add(success) }

        and:
        operationQueue.waitForCompletion()

        then:
        runs * success.run(_)

        where:
        runs | threads
        0    | 1
        0    | 4
        0    | 10
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
    }

    def "cannot use operation queue once it has completed"() {
        given:
        setupQueue(1)
        operationQueue.waitForCompletion()

        when:
        operationQueue.add(Mock(TestBuildOperation))

        then:
        thrown IllegalStateException
    }

    def "cannot add an unconstrained operation once the queue has completed"() {
        given:
        setupQueue(1)
        operationQueue.waitForCompletion()

        when:
        operationQueue.addUnconstrained(Mock(TestBuildOperation))

        then:
        thrown IllegalStateException
    }

    def "queue completes when a constrained operation cannot be handed off"() {
        given:
        setupQueue(1)
        workerLeaseProcessor.shutdown()

        when:
        operationQueue.add(new Success())

        then:
        thrown IllegalStateException

        when:
        operationQueue.cancel()
        operationQueue.waitForCompletion()

        then:
        noExceptionThrown()
    }

    def "queue completes when an unconstrained operation cannot be handed off"() {
        given:
        setupQueue(1)
        unconstrainedExecutor.shutdown()

        when:
        operationQueue.addUnconstrained(new Success())

        then:
        thrown RejectedExecutionException

        when:
        operationQueue.cancel()
        operationQueue.waitForCompletion()

        then:
        noExceptionThrown()
    }

    def "queue completes when the processor is shut down with operations still queued"() {
        given:
        // The main thread holds the only lease, so the spawned worker starves and the operations
        // are still queued when the processor is shut down.
        setupQueue(1)
        def executed = new AtomicInteger()

        when:
        3.times { operationQueue.add(operation { executed.incrementAndGet() }) }
        workerLeaseProcessor.shutdown()
        operationQueue.waitForCompletion()

        then:
        noExceptionThrown()
        executed.get() == 3
    }

    def "constrained operation stays queued for the waiting thread when no worker can be spawned"() {
        given:
        setupQueue(1)
        backingExecutor.shutdown()
        def executed = new AtomicInteger()

        when:
        operationQueue.add(operation { executed.incrementAndGet() })
        operationQueue.waitForCompletion()

        then:
        executed.get() == 1
    }

    def "unconstrained operations do not run on a worker thread"() {
        given:
        // Exactly one lease, held by the main thread, so a constrained operation could only ever
        // run via the main thread's self-drain -- which is a worker thread.
        setupQueue(1)
        def ranOnWorkerThread = new AtomicBoolean(true)

        when:
        operationQueue.addUnconstrained(operation { ranOnWorkerThread.set(workerRegistry.isWorkerThread()) })
        operationQueue.waitForCompletion()

        then:
        !ranOnWorkerThread.get()
    }

    def "unconstrained operations execute concurrently beyond the max worker count"() {
        given:
        setupQueue(1)
        int operations = 4
        def peakConcurrency = new AtomicInteger()
        def currentConcurrency = new AtomicInteger()
        def allStarted = new CountDownLatch(operations)

        when:
        operations.times {
            operationQueue.addUnconstrained(operation {
                int running = currentConcurrency.incrementAndGet()
                peakConcurrency.updateAndGet { Math.max(it, running) }
                allStarted.countDown()
                allStarted.await(2, TimeUnit.SECONDS)
                currentConcurrency.decrementAndGet()
            })
        }
        operationQueue.waitForCompletion()

        then:
        peakConcurrency.get() == operations
    }

    def "queue mixing constrained and unconstrained operations routes each kind to its own pool"() {
        given:
        setupQueue(2)
        def constrainedOnWorkerThread = new CopyOnWriteArrayList<Boolean>()
        def unconstrainedOnWorkerThread = new CopyOnWriteArrayList<Boolean>()

        when:
        3.times {
            operationQueue.add(operation { constrainedOnWorkerThread.add(workerRegistry.isWorkerThread()) })
            operationQueue.addUnconstrained(operation { unconstrainedOnWorkerThread.add(workerRegistry.isWorkerThread()) })
        }
        operationQueue.waitForCompletion()

        then:
        constrainedOnWorkerThread.size() == 3
        unconstrainedOnWorkerThread.size() == 3
        constrainedOnWorkerThread.every()
        !unconstrainedOnWorkerThread.any()
    }

    def "failures from constrained and unconstrained operations propagate together"() {
        given:
        setupQueue(2)

        when:
        operationQueue.add(new Success())
        operationQueue.add(new Failure())
        operationQueue.addUnconstrained(new Success())
        operationQueue.addUnconstrained(new Failure())
        operationQueue.waitForCompletion()

        then:
        MultipleBuildOperationFailures e = thrown()
        e.causes.size() == 2
        e.causes.every { it instanceof GradleException }
    }

    def "failures propagate to caller regardless of when it failed #operations with #threads threads"() {
        given:
        setupQueue(threads)
        operations.each { operation ->
            operationQueue.add(operation)
        }
        def failureCount = operations.findAll({ it instanceof Failure }).size()

        when:
        operationQueue.waitForCompletion()

        then:
        // assumes we don't fail early
        MultipleBuildOperationFailures e = thrown()
        e.getCauses().every({ it instanceof GradleException })
        e.getCauses().size() == failureCount

        where:
        [operations, threads] << [
            [[new Success(), new Success(), new Failure()],
             [new Success(), new Failure(), new Success()],
             [new Failure(), new Success(), new Success()],
             [new Failure(), new Failure(), new Failure()],
             [new Failure(), new Failure(), new Success()],
             [new Failure(), new Success(), new Failure()],
             [new Success(), new Failure(), new Failure()]],
            [1, 4, 10]].combinations()
    }

    def "when log location is set value is propagated in exceptions"() {
        given:
        setupQueue(1)
        operationQueue.setLogLocation(LOG_LOCATION)
        operationQueue.add(Stub(TestBuildOperation) {
            run(_) >> { throw new RuntimeException("first") }
        })

        when:
        operationQueue.waitForCompletion()

        then:
        MultipleBuildOperationFailures e = thrown()
        e.message.contains(LOG_LOCATION)
    }

    def "when queue is canceled, unstarted operations do not execute (#runs runs, #threads threads)"() {
        def expectedInvocations = Math.min(runs, threads)
        CountDownLatch startedLatch = new CountDownLatch(expectedInvocations)
        CountDownLatch releaseLatch = new CountDownLatch(1)
        def operationAction = Mock(Runnable)

        given:
        setupQueue(threads)
        lease.leaseFinish()
        lease = null

        when:
        runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
        startedLatch.await(30, TimeUnit.SECONDS)
        operationQueue.cancel()
        releaseLatch.countDown()
        // Cancelled operations still have to be pulled off the queue, so the waiting thread may have
        // to drain them itself, which it can only do while holding a lease.
        workerRegistry.runAsWorkerThread { operationQueue.waitForCompletion() }

        then:
        expectedInvocations * operationAction.run()

        where:
        runs | threads
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
    }

    def "when queue is canceled, unstarted operations of both kinds do not execute"() {
        CountDownLatch startedLatch = new CountDownLatch(2)
        CountDownLatch releaseLatch = new CountDownLatch(1)
        def executedAfterCancel = new AtomicInteger()

        given:
        // A single lease and a single unconstrained thread, so the operations queued behind the
        // blocking ones below are still unstarted when cancel() is called.
        setupQueue(1, Executors.newSingleThreadExecutor())
        lease.leaseFinish()
        lease = null

        when:
        operationQueue.add(new SynchronizedBuildOperation({}, startedLatch, releaseLatch))
        operationQueue.addUnconstrained(new SynchronizedBuildOperation({}, startedLatch, releaseLatch))
        assert startedLatch.await(30, TimeUnit.SECONDS)

        and:
        5.times { operationQueue.add(operation { executedAfterCancel.incrementAndGet() }) }
        5.times { operationQueue.addUnconstrained(operation { executedAfterCancel.incrementAndGet() }) }
        operationQueue.cancel()
        releaseLatch.countDown()
        workerRegistry.runAsWorkerThread { operationQueue.waitForCompletion() }

        then:
        executedAfterCancel.get() == 0
    }

    @Issue("https://github.com/gradle/gradle/issues/37613")
    def "workers do not pull operations without a lease, and main thread can progress the queue"() {
        given:
        // Slightly modified from setupQueue to allow certain injection points.
        def mainThread = Thread.currentThread()
        def workerStartedLoop = new CountDownLatch(1)
        def executedByMain = new AtomicInteger()
        def executedByOther = new AtomicInteger()
        def recordingWorker = { TestBuildOperation op ->
            if (Thread.currentThread() === mainThread) {
                executedByMain.incrementAndGet()
            } else {
                executedByOther.incrementAndGet()
            }
            op.run(null)
        } as BuildOperationQueue.QueueWorker<TestBuildOperation>

        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(1), ResourceLockStatistics.NO_OP) {
            @Override
            void tryWhileConditionToRunAsWorkerThread(Runnable action, BooleanSupplier shouldContinue) {
                // Called once per spawned worker; signal that the worker thread has entered the loop
                // and is about to block trying to acquire a lease (since the main thread holds the only one).
                if (Thread.currentThread() !== mainThread) {
                    workerStartedLoop.countDown()
                }
                super.tryWhileConditionToRunAsWorkerThread(action, shouldContinue)
            }
        }
        workerRegistry.startProjectExecution(true)
        // Keep the lease on the main thread so the spawned worker starves waiting for one.
        lease = workerRegistry.startWorker()
        backingExecutor = Executors.newCachedThreadPool()
        workerLeaseProcessor = new WorkerLeaseQueueProcessor(coordinationService, workerRegistry, backingExecutor, 1, 2)
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, workerLeaseProcessor.createSubmissionQueue(), newUnconstrainedExecutor(), recordingWorker, null)

        when:
        operationQueue.add(new Success())

        and:
        // Wait until the worker has started and is blocked acquiring a lease.
        // This ensures that the main thread will be needed to progress the queue.
        assert workerStartedLoop.await(10, TimeUnit.SECONDS)

        and:
        operationQueue.waitForCompletion()

        then:
        executedByMain.get() + executedByOther.get() == 1
        executedByMain.get() == 1
    }

    @Issue("https://github.com/gradle/gradle/issues/38154")
    def "waiting for completion #policyDesc project lock changes when allowAccessToProjectState is #allowAccessToProjectState"() {
        given:
        def mainThread = Thread.currentThread()
        def disallowDepth = ThreadLocal.withInitial { 0 }
        def lockChangesDisallowedDuringWait = new AtomicBoolean()
        def blockingCalled = new CountDownLatch(1)
        def startedLatch = new CountDownLatch(1)
        def releaseLatch = new CountDownLatch(1)

        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(2), ResourceLockStatistics.NO_OP) {
            @Override
            <T> T whileDisallowingProjectLockChanges(Factory<T> action) {
                disallowDepth.set(disallowDepth.get() + 1)
                try {
                    return super.whileDisallowingProjectLockChanges(action)
                } finally {
                    disallowDepth.set(disallowDepth.get() - 1)
                }
            }

            @Override
            void blocking(Runnable action) {
                if (Thread.currentThread() === mainThread) {
                    lockChangesDisallowedDuringWait.set(disallowDepth.get() > 0)
                    blockingCalled.countDown()
                    // Let the in-flight operation finish only once the main thread has reached the wait.
                    releaseLatch.countDown()
                }
                super.blocking(action)
            }
        }
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()
        backingExecutor = Executors.newCachedThreadPool()
        workerLeaseProcessor = new WorkerLeaseQueueProcessor(coordinationService, workerRegistry, backingExecutor, 2, 4)
        operationQueue = new DefaultBuildOperationQueue(allowAccessToProjectState, workerRegistry, workerLeaseProcessor.createSubmissionQueue(), newUnconstrainedExecutor(), new SimpleWorker(), null)

        when:
        operationQueue.add(new SynchronizedBuildOperation({}, startedLatch, releaseLatch))
        // Ensure a worker thread has picked up the operation, so the main thread reaches the blocking
        // wait instead of running the operation itself during the self-drain.
        assert startedLatch.await(10, TimeUnit.SECONDS)
        operationQueue.waitForCompletion()

        then:
        blockingCalled.await(10, TimeUnit.SECONDS)
        lockChangesDisallowedDuringWait.get() == lockChangesDisallowed

        where:
        allowAccessToProjectState | lockChangesDisallowed
        false                     | true
        true                      | false
        policyDesc = lockChangesDisallowed ? "disallows" : "allows"
    }

    @Issue("https://github.com/gradle/gradle/issues/38154")
    def "executing work #policyDesc project lock changes when allowAccessToProjectState is #allowAccessToProjectState"() {
        given:
        def disallowDepth = ThreadLocal.withInitial { 0 }
        def lockChangesDisallowedDuringWork = new CopyOnWriteArrayList<Boolean>()
        def recordingWorker = { TestBuildOperation op ->
            lockChangesDisallowedDuringWork.add(disallowDepth.get() > 0)
            op.run(null)
        } as BuildOperationQueue.QueueWorker<TestBuildOperation>

        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(1), ResourceLockStatistics.NO_OP) {
            @Override
            <T> T whileDisallowingProjectLockChanges(Factory<T> action) {
                disallowDepth.set(disallowDepth.get() + 1)
                try {
                    return super.whileDisallowingProjectLockChanges(action)
                } finally {
                    disallowDepth.set(disallowDepth.get() - 1)
                }
            }
        }
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()
        backingExecutor = Executors.newCachedThreadPool()
        workerLeaseProcessor = new WorkerLeaseQueueProcessor(coordinationService, workerRegistry, backingExecutor, 1, 2)
        operationQueue = new DefaultBuildOperationQueue(allowAccessToProjectState, workerRegistry, workerLeaseProcessor.createSubmissionQueue(), newUnconstrainedExecutor(), recordingWorker, null)

        when:
        operationQueue.add(new Success())
        operationQueue.waitForCompletion()

        then:
        lockChangesDisallowedDuringWork == [lockChangesDisallowed]

        where:
        allowAccessToProjectState | lockChangesDisallowed
        false                     | true
        true                      | false
        policyDesc = lockChangesDisallowed ? "disallows" : "allows"
    }

    private static TestBuildOperation operation(Closure<?> body) {
        return new TestBuildOperation() {
            @Override
            void run(BuildOperationContext context) {
                body.call()
            }
        }
    }

    static class SynchronizedBuildOperation extends TestBuildOperation {
        final Runnable operationAction
        final CountDownLatch startedLatch
        final CountDownLatch releaseLatch

        SynchronizedBuildOperation(Runnable operationAction, CountDownLatch startedLatch, CountDownLatch releaseLatch) {
            this.operationAction = operationAction
            this.startedLatch = startedLatch
            this.releaseLatch = releaseLatch
        }

        @Override
        void run(BuildOperationContext context) {
            operationAction.run()
            startedLatch.countDown()
            releaseLatch.await()
        }
    }
}
