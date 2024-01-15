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
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.time.Clock
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.NoAvailableWorkerLeaseException
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultBuildOperationExecutorParallelExecutionTest extends ConcurrentSpec {
    WorkerLeaseService workerRegistry
    BuildOperationExecutor buildOperationExecutor
    WorkerLeaseRegistry.WorkerLeaseCompletion outerOperationCompletion
    WorkerLeaseRegistry.WorkerLease outerOperation
    BuildOperationListener operationListener = Mock(BuildOperationListener)
    private ExecutorFactory executorFactory = new DefaultExecutorFactory()

    def setupBuildOperationExecutor(int maxThreads) {
        def parallelismConfiguration = new DefaultParallelismConfiguration(true, maxThreads)
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), parallelismConfiguration)
        workerRegistry.startProjectExecution(true)
        buildOperationExecutor = new DefaultBuildOperationExecutor(
            operationListener, Mock(Clock), new NoOpProgressLoggerFactory(),
            new DefaultBuildOperationQueueFactory(workerRegistry), executorFactory, parallelismConfiguration, new DefaultBuildOperationIdFactory())
        outerOperationCompletion = workerRegistry.startWorker()
        outerOperation = workerRegistry.getCurrentWorkerLease()
    }

    static class SimpleWorker implements BuildOperationWorker<DefaultBuildOperationQueueTest.TestBuildOperation> {
        void execute(DefaultBuildOperationQueueTest.TestBuildOperation run, BuildOperationContext context) { run.run(context) }
    }

    def "cleanup"() {
        if (outerOperationCompletion) {
            outerOperationCompletion.leaseFinish()
            workerRegistry.stop()
        }
    }

    def "all #operations operations run to completion when using #maxThreads threads"() {
        given:
        setupBuildOperationExecutor(maxThreads)
        def operation = Spy(DefaultBuildOperationQueueTest.Success)
        def worker = new SimpleWorker()

        when:
        buildOperationExecutor.runAll(worker, { queue ->
            operations.times { queue.add(operation) }
        })

        then:
        operations * operation.run(_)

        where:
        // Where operations < maxThreads
        // operations = maxThreads
        // operations >> maxThreads
        operations | maxThreads
        0          | 1
        1          | 1
        20         | 1
        1          | 4
        4          | 4
        20         | 4
    }

    def "all work run to completion for multiple queues when using multiple threads #maxThreads"() {
        given:
        def amountOfWork = 10
        setupBuildOperationExecutor(maxThreads)
        outerOperationCompletion.leaseFinish() // The work of this test is done by other threads
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
        maxThreads << [1, 4, 10]
    }

    def "failures in one queue do not cause failures in other queues"() {
        given:
        def amountOfWork = 10
        def maxThreads = 4
        setupBuildOperationExecutor(maxThreads)
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
        buildOperationExecutor.runAll(worker, { queue ->
            threadCount.times { queue.add(operation) }
        })

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e instanceof MultipleBuildOperationFailures
        ((MultipleBuildOperationFailures) e).getCauses().size() == 4
    }

    def "operations are canceled when the generator fails"() {
        def buildQueue = Mock(BuildOperationQueue)
        def buildOperationQueueFactory = Mock(BuildOperationQueueFactory) {
            create(_, _, _) >> { buildQueue }
        }

        def buildOperationExecutor = new DefaultBuildOperationExecutor(operationListener, Mock(Clock), new NoOpProgressLoggerFactory(),
            buildOperationQueueFactory, Stub(ExecutorFactory), new DefaultParallelismConfiguration(true, 1),
            new DefaultBuildOperationIdFactory())
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
            create(_, _, _) >> { buildQueue }
        }
        def buildOperationExecutor = new DefaultBuildOperationExecutor(
            operationListener, Mock(Clock), new NoOpProgressLoggerFactory(),
            buildOperationQueueFactory, Stub(ExecutorFactory), new DefaultParallelismConfiguration(true, 1), new DefaultBuildOperationIdFactory())
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
        buildOperationExecutor.runAll({ queue ->
            5.times { queue.add(operation) }
        })

        then:
        5 * operation.run(_)
    }

    def "cannot be used on unmanaged threads"() {
        given:
        setupBuildOperationExecutor(2)
        def operation = Spy(DefaultBuildOperationQueueTest.Success)

        when:
        async {
            buildOperationExecutor.runAll({ queue ->
                5.times { queue.add(operation) }
            })
        }

        then:
        thrown(NoAvailableWorkerLeaseException)
    }

    def workerThread(Closure cl) {
        start {
            workerRegistry.runAsWorkerThread(cl)
        }
    }
}
