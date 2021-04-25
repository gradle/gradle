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
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Unroll

class DefaultBuildOperationExecutorParallelExecutionTest extends ConcurrentSpec {

    WorkerLeaseRegistry workerRegistry
    BuildOperationExecutor buildOperationExecutor
    WorkerLeaseRegistry.WorkerLeaseCompletion outerOperationCompletion
    WorkerLeaseRegistry.WorkerLease outerOperation
    BuildOperationListener operationListener = Mock(BuildOperationListener)
    private ExecutorFactory executorFactory = new DefaultExecutorFactory()

    def setupBuildOperationExecutor(int maxThreads) {
        def parallelismConfiguration = new DefaultParallelismConfiguration(true, maxThreads)
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), parallelismConfiguration)
        buildOperationExecutor = new DefaultBuildOperationExecutor(
            operationListener, Mock(Clock), new NoOpProgressLoggerFactory(),
            new DefaultBuildOperationQueueFactory(workerRegistry), executorFactory, parallelismConfiguration, new DefaultBuildOperationIdFactory())
        outerOperationCompletion = workerRegistry.getWorkerLease().start()
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

    @Unroll
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

    @Unroll
    def "all work run to completion for multiple queues when using multiple threads #maxThreads"() {
        given:
        def amountOfWork = 10
        setupBuildOperationExecutor(maxThreads)
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
                start {
                    def cl = outerOperation.startChild()
                    buildOperationExecutor.runAll(worker, { queue ->
                        amountOfWork.times {
                            queue.add(operations[i])
                        }
                    })
                    cl.leaseFinish()
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
            start {
                def cl = outerOperation.startChild()
                buildOperationExecutor.runAll(worker, { queue ->
                    amountOfWork.times {
                        queue.add(success)
                    }
                })
                cl.leaseFinish()
                successfulQueueCompleted = true
            }
            // Failure queue
            start {
                def cl = outerOperation.startChild()
                try {
                    buildOperationExecutor.runAll(worker, { queue ->
                        amountOfWork.times {
                            queue.add(failure)
                        }
                    })
                } catch (MultipleBuildOperationFailures e) {
                    exceptionInFailureQueue = true
                } finally {
                    cl.leaseFinish()
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
            buildOperationQueueFactory, Stub(ExecutorFactory), new DefaultParallelismConfiguration(true, 1), new DefaultBuildOperationIdFactory())
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

    def "can be used on unmanaged threads"() {
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
        5 * operation.run(_)
    }

    def "unmanaged thread operation is started and stopped when created by run"() {
        given:
        setupBuildOperationExecutor(2)
        BuildOperationRef operationState
        BuildOperationRef unmanaged
        operationListener.started(_, _) >> { args ->
            BuildOperationDescriptor descriptor = args[0]
            if (descriptor.id.id < 0) {
                unmanaged = buildOperationExecutor.getCurrentOperation()
            }
        }

        when:
        async {
            buildOperationExecutor.run(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                void run(BuildOperationContext context) {
                    operationState = DefaultBuildOperationExecutorParallelExecutionTest.this.buildOperationExecutor.getCurrentOperation()
                    assert operationState.running
                    assert unmanaged.running
                    assert operationState.description.parentId.id < 0
                }
            })
        }

        then:
        unmanaged.class.simpleName == 'UnmanagedThreadOperation'
        unmanaged.parentId == null
        operationState != null
        !operationState.running
        !unmanaged.running
    }

    def "unmanaged thread operation is started and stopped when created by call"() {
        given:
        setupBuildOperationExecutor(2)
        BuildOperationRef operationState
        BuildOperationRef unmanaged
        operationListener.started(_, _) >> { args ->
            BuildOperationDescriptor descriptor = args[0]
            if (descriptor.id.id < 0) {
                unmanaged = buildOperationExecutor.getCurrentOperation()
            }
        }


        when:
        async {
            buildOperationExecutor.call(new CallableBuildOperation() {
                Object call(BuildOperationContext context) {
                    operationState = buildOperationExecutor.getCurrentOperation()
                    assert operationState.running
                    assert unmanaged.running
                    assert operationState.description.parentId.id < 0
                    return null
                }

                BuildOperationDescriptor.Builder description() {
                    BuildOperationDescriptor.displayName("test operation")
                }
            })
        }

        then:
        unmanaged.class.simpleName == 'UnmanagedThreadOperation'
        unmanaged.parentId == null
        operationState != null
        !operationState.running
        !unmanaged.running
    }

    def "a single unmanaged thread operation is started and stopped when created by runAll"() {
        given:
        setupBuildOperationExecutor(2)
        BuildOperationRef operationState
        OperationIdentifier parentOperationId

        when:
        async {
            buildOperationExecutor.runAll({ queue ->
                5.times {
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        void run(BuildOperationContext context) {
                            def myOperationState = DefaultBuildOperationExecutorParallelExecutionTest.this.buildOperationExecutor.getCurrentOperation()
                            assert parentOperationId == null || parentOperationId == myOperationState.description.parentId
                            parentOperationId = myOperationState.description.parentId
                            assert parentOperationId.id < 0
                            assert myOperationState.running
                            operationState = myOperationState
                        }
                    })
                }
            })
        }

        then:
        operationState != null
        !operationState.running
    }
}
