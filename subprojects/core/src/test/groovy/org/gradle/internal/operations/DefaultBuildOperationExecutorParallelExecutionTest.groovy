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
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ExecutorPolicy
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ManagedExecutorImpl
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.internal.work.NoAvailableWorkerLeaseException
import org.gradle.internal.work.ResourceLockStatistics
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Issue
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DefaultBuildOperationExecutorParallelExecutionTest extends ConcurrentSpec {
    WorkerLeaseService workerRegistry
    BuildOperationExecutor buildOperationExecutor

    def setupBuildOperationExecutor(int maxWorkers) {
        def workerLimits = new DefaultWorkerLimits(maxWorkers)
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), workerLimits, ResourceLockStatistics.NO_OP)
        workerRegistry.startProjectExecution(true)
        buildOperationExecutor = BuildOperationExecutorSupport.builder(workerLimits).withWorkerLeaseService(workerRegistry).build()
    }

    static class SimpleWorker implements BuildOperationWorker<DefaultBuildOperationQueueTest.TestBuildOperation> {
        void execute(DefaultBuildOperationQueueTest.TestBuildOperation run, BuildOperationContext context) { run.run(context) }
    }

    def "all #operations operations run to completion when using #maxWorkers threads"() {
        given:
        setupBuildOperationExecutor(maxWorkers)
        def operation = Spy(DefaultBuildOperationQueueTest.Success)
        def worker = new SimpleWorker()

        when:
        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll(worker, { queue ->
                operations.times { queue.add(operation) }
            })
        }

        then:
        operations * operation.run(_)

        where:
        // Where operations < maxWorkers
        // operations = maxWorkers
        // operations >> maxWorkers
        operations | maxWorkers
        0          | 1
        1          | 1
        20         | 1
        1          | 4
        4          | 4
        20         | 4
    }

    @Timeout(20)
    def "unbounded executions to not block worker executions"() {
        int maxWorkers = 4 // Some arbitrary number
        setupBuildOperationExecutor(maxWorkers)
        CountDownLatch started = new CountDownLatch(maxWorkers - 1)
        CountDownLatch constrainedExecuted = new CountDownLatch(2)

        // Start `maxWorkers - 1` number of blocking operations
        workerThread {
            buildOperationExecutor.runAll(queue -> { // Block until all operations have executed
                (maxWorkers - 1).times {
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        void run(BuildOperationContext context) throws Exception {
                            started.countDown() // We've captured a worker lease
                            constrainedExecuted.await() // Wait until the constrained operation has executed
                        }

                        @Override
                        BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("foo")
                        }
                    })
                }
            }, BuildOperationConstraint.UNCONSTRAINED)
        }

        started.await() // We've started `maxWorkers - 1` unconstrained operations

        // Try to run 2 more constrained operations
        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll(queue -> {
            2.times {
                queue.add(new RunnableBuildOperation() {
                    @Override
                    void run(BuildOperationContext context) throws Exception {
                        constrainedExecuted.countDown()
                        constrainedExecuted.await()
                    }

                    @Override
                    BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("bar")
                    }
                })
            }
            }, BuildOperationConstraint.MAX_WORKERS)
        }

        expect:
        constrainedExecuted.await()
    }

    def "can concurrently execute more unconstrained operations than there are worker leases"() {
        int maxWorkers = 4 // Some arbitrary number
        int numUnconstrainedOperations = new DefaultWorkerLimits(maxWorkers).maxUnconstrainedWorkerCount
        assert numUnconstrainedOperations > maxWorkers

        setupBuildOperationExecutor(maxWorkers)
        CountDownLatch started = new CountDownLatch(numUnconstrainedOperations)

        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll(queue -> {
                // Add more operations than there are worker leases
                numUnconstrainedOperations.times {
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        void run(BuildOperationContext context) throws Exception {
                            started.countDown() // We've captured a worker lease
                            started.await() // Wait until all operations have started
                        }

                        @Override
                        BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("foo")
                        }
                    })
                }
            }, BuildOperationConstraint.UNCONSTRAINED)
        }

        expect:
        started.await()
    }

    def "all work run to completion for multiple queues when using multiple threads #maxWorkers"() {
        given:
        def amountOfWork = 10
        setupBuildOperationExecutor(maxWorkers)
        def worker = new SimpleWorker()
        def numberOfQueues = 5
        def operations = [
            Spy(DefaultBuildOperationQueueTest.Success),
            Spy(DefaultBuildOperationQueueTest.Success),
            Spy(DefaultBuildOperationQueueTest.Success),
            Spy(DefaultBuildOperationQueueTest.Success),
            Spy(DefaultBuildOperationQueueTest.Success)
        ]

        when:
        async {
            numberOfQueues.times { i ->
                workerThread {
                    buildOperationExecutor.runAll(worker, { queue ->
                        amountOfWork.times {
                            queue.add(operations[i])
                        }
                    })
                }
            }
        }

        then:
        operations.each { operation ->
            amountOfWork * operation.run(_)
        }

        where:
        maxWorkers << [1, 4, 10]
    }

    def "failures in one queue do not cause failures in other queues"() {
        given:
        def amountOfWork = 10
        def maxWorkers = 4
        setupBuildOperationExecutor(maxWorkers)
        def success = new DefaultBuildOperationQueueTest.Success()
        def failure = new DefaultBuildOperationQueueTest.Failure()
        def worker = new SimpleWorker()
        boolean successfulQueueCompleted = false
        boolean exceptionInFailureQueue = false

        when:
        async {
            // Successful queue
            workerThread {
                buildOperationExecutor.runAll(worker, { queue ->
                    amountOfWork.times {
                        queue.add(success)
                    }
                })
                successfulQueueCompleted = true
            }
            // Failure queue
            workerThread {
                try {
                    buildOperationExecutor.runAll(worker, { queue ->
                        amountOfWork.times {
                            queue.add(failure)
                        }
                    })
                } catch (MultipleBuildOperationFailures e) {
                    exceptionInFailureQueue = true
                }
            }
        }

        then:
        exceptionInFailureQueue

        and:
        successfulQueueCompleted
    }

    def "multiple failures get reported"() {
        given:
        def threadCount = 4
        setupBuildOperationExecutor(threadCount)
        def worker = new SimpleWorker()
        def operation = Stub(DefaultBuildOperationQueueTest.TestBuildOperation) {
            run(_) >> {
                throw new GradleException("always fails")
            }
        }

        when:
        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll(worker, { queue ->
                threadCount.times { queue.add(operation) }
            })
        }

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e instanceof MultipleBuildOperationFailures
        ((MultipleBuildOperationFailures) e).getCauses().size() == 4
    }

    def "operations are canceled when the generator fails"() {
        def buildQueue = Mock(BuildOperationQueue)
        def buildOperationQueueFactory = Mock(BuildOperationQueueFactory) {
            create(_, _, _, _) >> { buildQueue }
        }

        def buildOperationExecutor = BuildOperationExecutorSupport.builder(1).withQueueFactory(buildOperationQueueFactory).build()
        def worker = Stub(BuildOperationWorker)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)

        when:
        buildOperationExecutor.runAll(worker, { queue ->
            4.times { queue.add(operation) }
            throw new Exception("Failure in generator")
        })

        then:
        thrown(BuildOperationQueueFailure)

        and:
        4 * buildQueue.add(_)
        1 * buildQueue.cancel()
    }

    def "multi-cause error when there are failures both enqueuing and running operations"() {
        def operationFailures = [new Exception("failed operation 1"), new Exception("failed operation 2")]
        def buildQueue = Mock(BuildOperationQueue) {
            waitForCompletion() >> { throw new MultipleBuildOperationFailures(operationFailures, null) }
        }
        def buildOperationQueueFactory = Mock(BuildOperationQueueFactory) {
            create(_, _, _, _) >> { buildQueue }
        }
        def buildOperationExecutor = BuildOperationExecutorSupport.builder(1).withQueueFactory(buildOperationQueueFactory).build()
        def worker = Stub(BuildOperationWorker)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)

        when:
        buildOperationExecutor.runAll(worker, { queue ->
            4.times { queue.add(operation) }
            throw new Exception("Failure in generator")
        })

        then:
        def e = thrown(DefaultMultiCauseException)
        e.message.startsWith("There was a failure while populating the build operation queue:")
        e.message.contains("operations failed")
        e.message.contains("failed operation 1")
        e.message.contains("failed operation 2")
        e.causes.size() == 2
        e.causes.any { it instanceof BuildOperationQueueFailure && it.message.startsWith("There was a failure while populating the build operation queue:") }
        e.causes.any { it instanceof MultipleBuildOperationFailures && it.causes.collect { it.message }.sort() == ["failed operation 1", "failed operation 2"] }

        and:
        4 * buildQueue.add(_)
        1 * buildQueue.cancel()
    }

    def "can provide only runnable build operations to the processor"() {
        given:
        setupBuildOperationExecutor(2)
        def operation = Spy(DefaultBuildOperationQueueTest.Success)

        when:

        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll({ queue ->
                5.times { queue.add(operation) }
            })
        }

        then:
        5 * operation.run(_)
    }

    def "cannot be used on unmanaged threads"() {
        given:
        setupBuildOperationExecutor(2)
        def operation = Spy(DefaultBuildOperationQueueTest.Success)

        when:
        buildOperationExecutor.runAll({ queue ->
            5.times { queue.add(operation) }
        })

        then:
        thrown(NoAvailableWorkerLeaseException)
    }

    @Issue("https://github.com/gradle/gradle-private/issues/5272")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    def "main thread drains queued work even when blocking work has left the pool over-subscribed"() {
        given:
        def mainThread = Thread.currentThread()
        def setupDone = new CountDownLatch(1)
        def releaseBlockingWorker = new CountDownLatch(1)
        def opForMainRanOnMain = new AtomicBoolean()

        // Limit 2 so the main thread and exactly one spawned worker can hold the leases. The
        // executor has a single thread, which the first worker holds while it parks below. The
        // compensating worker that blocking() spawns is queued behind that parked thread, so it
        // stays counted but never starts to drain the queue. That is what leaves the main thread
        // looking like an "extra" worker.
        def workerLimits = new DefaultWorkerLimits(2)
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), workerLimits, ResourceLockStatistics.NO_OP)
        workerRegistry.startProjectExecution(true)
        buildOperationExecutor = BuildOperationExecutorSupport.builder(workerLimits)
            .withWorkerLeaseService(workerRegistry)
            .withExecutorFactory(new SingleThreadExecutorFactory())
            .build()

        // The op on the first worker.
        def blockingOperation = runnableOperation("blocking") {
            // Block to spawn a compensating worker that will never run
            workerRegistry.blocking({} as Runnable)
            // Stop blocking to decrement the max worker count to 1, but keep the worker count at 2.

            // Signal that the compensating worker has been spawned and we're ready to execute the problematic behavior.
            setupDone.countDown()
            // Wait for the end of the test, holding this worker thread to keep the worker count at 2.
            releaseBlockingWorker.await(30, TimeUnit.SECONDS)
        }
        // The op that should run on the main thread, as the first worker is blocked and the compensating worker never starts.
        def opForMain = runnableOperation("opForMain") {
            opForMainRanOnMain.set(Thread.currentThread() === mainThread)
            releaseBlockingWorker.countDown()
        }

        when:
        workerRegistry.runAsWorkerThread {
            buildOperationExecutor.runAll({ queue ->
                queue.add(blockingOperation)
                queue.add(opForMain)
                // Wait until the spawned worker has done its blocking work and is parked, so the
                // main thread only starts draining once it is already "extra" with work queued.
                setupDone.await(10, TimeUnit.SECONDS)

                // On return we will start draining the queue on the main thread, which should run opForMain() despite the worker count being too high.
                // If the bug is present, it will never run and the test will time out.
            }, BuildOperationConstraint.MAX_WORKERS)
        }

        then:
        // Ensure that the main op really executed on the main thread, and not on the spawned worker that was blocked and never started.
        opForMainRanOnMain.get()
    }

    private static RunnableBuildOperation runnableOperation(String name, Closure body) {
        return new RunnableBuildOperation() {
            @Override
            void run(BuildOperationContext context) {
                body.call()
            }

            @Override
            BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(name)
            }
        }
    }

    /**
     * Creates single-threaded executors, so that a second worker submitted while the first is
     * still running is queued behind it rather than run concurrently.
     */
    static class SingleThreadExecutorFactory implements ExecutorFactory {
        @Delegate
        private final ExecutorFactory delegate = new DefaultExecutorFactory()

        @Override
        ManagedExecutor create(String displayName) {
            return new ManagedExecutorImpl(Executors.newSingleThreadExecutor(), new ExecutorPolicy.CatchAndRecordFailures())
        }

        @Override
        ManagedExecutor create(String displayName, int fixedSize) {
            return new ManagedExecutorImpl(Executors.newSingleThreadExecutor(), new ExecutorPolicy.CatchAndRecordFailures())
        }
    }

    def workerThread(Closure cl) {
        start {
            workerRegistry.runAsWorkerThread(cl)
        }
    }
}
