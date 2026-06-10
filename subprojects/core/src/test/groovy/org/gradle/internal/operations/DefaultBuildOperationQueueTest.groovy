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
import org.gradle.internal.concurrent.ExecutorPolicy
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ManagedExecutorImpl
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.internal.work.ResourceLockStatistics
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

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

    void setupQueue(int threads) {
        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(threads), ResourceLockStatistics.NO_OP) {}
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(Executors.newFixedThreadPool(threads), new ExecutorPolicy.CatchAndRecordFailures()),
            threads,
            true
        )
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, executionContext, new SimpleWorker(), null)
    }

    def "cleanup"() {
        lease?.leaseFinish()
        workerRegistry.stop()
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

    def "does not submit more work processors than #threads threads"() {
        given:
        setupQueue(threads)
        lease.leaseFinish() // Release worker lease to allow operation to run, when there is max 1 worker thread

        def expectedWorkerCount = Math.min(runs, threads)
        def workersStarted = new AtomicInteger()
        def startedLatch = new CountDownLatch(expectedWorkerCount)
        def releaseLatch = new CountDownLatch(1)
        def operationAction = {
            if (workersStarted.get() < expectedWorkerCount) {
                println "started worker in thread ${Thread.currentThread().id} (waiting for ${expectedWorkerCount - workersStarted.incrementAndGet()}).."
            }
        }
        def executor = Mock(ManagedExecutor)
        def delegateExecutor = Executors.newFixedThreadPool(threads)
        def executionContext = new BuildOperationExecutionContext(
            executor,
            threads,
            true
        )
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, executionContext, new SimpleWorker(), null)

        println "expecting ${expectedWorkerCount} concurrent work processors to be started..."

        when:
        def waitForCompletionThread = new Thread({
            workerRegistry.runAsWorkerThread {
                runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
                operationQueue.waitForCompletion()
            }
        })
        waitForCompletionThread.start()

        and:
        startedLatch.await(30, TimeUnit.SECONDS)
        releaseLatch.countDown()

        and:
        waitForCompletionThread.join(30000)

        then:
        !waitForCompletionThread.alive
        // The main thread sometimes processes items when there are more items than threads available, so
        // we may only submit workerCount - 1 work processors, but we should never submit more than workerCount
        ((expectedWorkerCount-1)..expectedWorkerCount) * executor.execute(_) >> { args -> delegateExecutor.execute(args[0]) }

        where:
        runs | threads
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
        20   | 10
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
        lease.leaseFinish() // Release worker lease to allow operation to run, when there is max 1 worker thread

        when:
        def waitForCompletionThread = new Thread({
            workerRegistry.runAsWorkerThread {
                runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
                operationQueue.waitForCompletion()
            }
        })
        waitForCompletionThread.start()

        and:
        // wait for operations to begin running
        startedLatch.await(30, TimeUnit.SECONDS)

        and:
        operationQueue.cancel()

        and:
        // release the running operations to complete
        releaseLatch.countDown()
        waitForCompletionThread.join(30000)

        then:
        !waitForCompletionThread.alive
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

    @Issue("https://github.com/gradle/gradle/issues/37613")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    def "workers do not pull operations without a lease, and main thread can progress the queue"() {
        given:
        // Slightly modified from setupQueue to allow certain injection points.
        def mainThread = Thread.currentThread()
        def workerStartedRetrying = new CountDownLatch(1)
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
            <T> Optional<T> tryRunAsWorkerThread(Factory<T> action) {
                // Called repeatedly from the retry loop; CountDownLatch tolerates extra countDown() calls.
                if (Thread.currentThread() !== mainThread) {
                    workerStartedRetrying.countDown()
                }
                return super.tryRunAsWorkerThread(action)
            }
        }
        workerRegistry.startProjectExecution(true)
        // Keep the lease on the main thread so the spawned worker starves on runAsWorkerThread.
        lease = workerRegistry.startWorker()
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(Executors.newFixedThreadPool(1), new ExecutorPolicy.CatchAndRecordFailures()),
            1,
            true
        )
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, executionContext, recordingWorker, null)

        when:
        operationQueue.add(new Success())

        and:
        // Wait until the worker has tried and failed to acquire a lease.
        // This ensures that the main thread will be needed to progress the queue.
        assert workerStartedRetrying.await(10, TimeUnit.SECONDS)

        and:
        operationQueue.waitForCompletion()

        then:
        executedByMain.get() + executedByOther.get() == 1
        executedByMain.get() == 1
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    def "starved worker exits via shouldExitWithExtraWorkerInvalidation when the queue is cancelled"() {
        given:
        def mainThread = Thread.currentThread()
        def workerStartedRetrying = new CountDownLatch(1)
        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(1), ResourceLockStatistics.NO_OP) {
            @Override
            <T> Optional<T> tryRunAsWorkerThread(Factory<T> action) {
                if (Thread.currentThread() !== mainThread) {
                    workerStartedRetrying.countDown()
                }
                return super.tryRunAsWorkerThread(action)
            }
        }
        workerRegistry.startProjectExecution(true)
        // Hold the only lease on the main thread so the spawned worker is starved.
        lease = workerRegistry.startWorker()
        def underlyingExecutor = Executors.newFixedThreadPool(1)
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(underlyingExecutor, new ExecutorPolicy.CatchAndRecordFailures()),
            1,
            true
        )
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, executionContext, new SimpleWorker(), null)

        when:
        operationQueue.add(new Success())
        // Wait for the spawned worker to enter its lease-retry loop.
        assert workerStartedRetrying.await(10, TimeUnit.SECONDS)

        and:
        operationQueue.cancel()

        and:
        underlyingExecutor.shutdown()
        def terminated = underlyingExecutor.awaitTermination(15, TimeUnit.SECONDS)

        then:
        terminated
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    def "waiting for completion #policyDesc project lock changes when allowAccessToProjectState is #allowAccessToProjectState"() {
        given:
        def mainThread = Thread.currentThread()
        def disallowDepth = ThreadLocal.withInitial { 0 }
        def lockChangesDisallowedDuringWait = new AtomicBoolean()
        def blockingCalled = new CountDownLatch(1)
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
                    // Unblock the in-flight operation only once the main thread has reached the wait,
                    // guaranteeing pendingOperations > 0 when waitForWorkToComplete() checks it
                    releaseLatch.countDown()
                }
                super.blocking(action)
            }
        }
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()
        // requiresWorkerLease=false so the main thread skips the self-drain and goes straight to the blocking wait
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(Executors.newFixedThreadPool(2), new ExecutorPolicy.CatchAndRecordFailures()),
            2,
            false
        )
        operationQueue = new DefaultBuildOperationQueue(allowAccessToProjectState, workerRegistry, executionContext, new SimpleWorker(), null)

        when:
        operationQueue.add(new SynchronizedBuildOperation({}, new CountDownLatch(1), releaseLatch))
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

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
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
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(Executors.newFixedThreadPool(1), new ExecutorPolicy.CatchAndRecordFailures()),
            1,
            true
        )
        operationQueue = new DefaultBuildOperationQueue(allowAccessToProjectState, workerRegistry, executionContext, recordingWorker, null)

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

    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    def "runAll does not lend project lock while waiting, avoiding deadlock against a resource held across the queue"() {
        given:
        // Mirrors the CI hang: the waiter owns some resource (there, cache ownership) plus a
        // project lock; the other thread takes the project lock and then wants the resource
        def resource = new ReentrantLock()
        def blockingStarted = new CountDownLatch(1)
        def releaseOperation = new CountDownLatch(1)
        def failure = new AtomicReference<Throwable>()

        setupLockLendingQueue(false, blockingStarted)
        def projectLock = workerRegistry.getProjectLock(Path.path(":build"), Path.path(":build:project"))

        def waiter = lockLendingWaiterThread(projectLock, resource, releaseOperation, failure)
        def other = lockLendingOtherThread(projectLock, resource, new CountDownLatch(1), failure)

        when:
        waiter.start()
        assert blockingStarted.await(10, TimeUnit.SECONDS)
        boolean lockHeldDuringWait = false
        coordinationService.withStateLock({ lockHeldDuringWait = projectLock.locked } as Runnable)
        other.start()
        releaseOperation.countDown()
        waiter.join(10_000)
        other.join(10_000)

        then:
        // The wait kept the project lock, so the other thread could never interleave into it
        lockHeldDuringWait
        !waiter.alive
        !other.alive
        failure.get() == null

        cleanup:
        releaseOperation.countDown()
        other.interrupt()
        waiter.join(10_000)
        other.join(10_000)
    }

    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    def "runAllWithAccessToProjectState lends project lock while waiting, deadlocking against a resource held across the queue"() {
        given:
        def resource = new ReentrantLock()
        def blockingStarted = new CountDownLatch(1)
        def releaseOperation = new CountDownLatch(1)
        def otherHasProjectLock = new CountDownLatch(1)
        def failure = new AtomicReference<Throwable>()

        setupLockLendingQueue(true, blockingStarted)
        def projectLock = workerRegistry.getProjectLock(Path.path(":build"), Path.path(":build:project"))

        def waiter = lockLendingWaiterThread(projectLock, resource, releaseOperation, failure)
        def other = lockLendingOtherThread(projectLock, resource, otherHasProjectLock, failure)

        when:
        waiter.start()
        assert blockingStarted.await(10, TimeUnit.SECONDS)
        boolean lockHeldDuringWait = true
        coordinationService.withStateLock({ lockHeldDuringWait = projectLock.locked } as Runnable)
        other.start()
        // Only possible because the wait lent out the project lock
        assert otherHasProjectLock.await(10, TimeUnit.SECONDS)
        releaseOperation.countDown()
        waiter.join(2_000)

        then:
        // The queue's work is done, but the waiter cannot take its project lock back from the
        // other thread, which in turn cannot acquire the resource the waiter still holds
        !lockHeldDuringWait
        waiter.alive
        other.alive
        failure.get() == null

        cleanup:
        // Break the deadlock so the registry can shut down cleanly
        other.interrupt()
        waiter.join(10_000)
        other.join(10_000)
    }

    private void setupLockLendingQueue(boolean allowAccessToProjectState, CountDownLatch blockingStarted) {
        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(2), ResourceLockStatistics.NO_OP) {
            @Override
            void blocking(Runnable action) {
                // Count down inside the action so the lend/keep decision has already been made
                super.blocking({
                    blockingStarted.countDown()
                    action.run()
                } as Runnable)
            }
        }
        workerRegistry.startProjectExecution(true)
        // requiresWorkerLease=false so the waiter skips the self-drain and goes straight to the blocking wait
        def executionContext = new BuildOperationExecutionContext(
            new ManagedExecutorImpl(Executors.newFixedThreadPool(2), new ExecutorPolicy.CatchAndRecordFailures()),
            2,
            false
        )
        operationQueue = new DefaultBuildOperationQueue(allowAccessToProjectState, workerRegistry, executionContext, new SimpleWorker(), null)
    }

    private Thread lockLendingWaiterThread(projectLock, ReentrantLock resource, CountDownLatch releaseOperation, AtomicReference<Throwable> failure) {
        def waiter = new Thread({
            try {
                workerRegistry.withLocks([projectLock]) {
                    resource.lock()
                    try {
                        operationQueue.add(new SynchronizedBuildOperation({}, new CountDownLatch(1), releaseOperation))
                        operationQueue.waitForCompletion()
                    } finally {
                        resource.unlock()
                    }
                }
            } catch (Throwable t) {
                failure.set(t)
            }
        })
        waiter.daemon = true
        return waiter
    }

    private Thread lockLendingOtherThread(projectLock, ReentrantLock resource, CountDownLatch hasProjectLock, AtomicReference<Throwable> failure) {
        def other = new Thread({
            try {
                workerRegistry.withLocks([projectLock]) {
                    hasProjectLock.countDown()
                    resource.lockInterruptibly()
                    resource.unlock()
                }
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                failure.set(t)
            }
        })
        other.daemon = true
        return other
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
