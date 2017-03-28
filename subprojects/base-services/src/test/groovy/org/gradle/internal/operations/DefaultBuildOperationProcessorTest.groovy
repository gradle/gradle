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
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.progress.TestBuildOperationExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Unroll

class DefaultBuildOperationProcessorTest extends ConcurrentSpec {

    WorkerLeaseRegistry workerRegistry
    BuildOperationProcessor buildOperationProcessor
    WorkerLeaseRegistry.WorkerLeaseCompletion outerOperationCompletion
    WorkerLeaseRegistry.WorkerLease outerOperation

    def setupBuildOperationProcessor(int maxThreads) {
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), true, maxThreads)
        buildOperationProcessor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), new DefaultBuildOperationQueueFactory(workerRegistry), new DefaultExecutorFactory(), maxThreads)
        outerOperationCompletion = workerRegistry.getWorkerLease().start()
        outerOperation = workerRegistry.getCurrentWorkerLease()
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
        setupBuildOperationProcessor(maxThreads)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        buildOperationProcessor.run(worker, { queue ->
            operations.times { queue.add(operation) }
        })

        then:
        operations * operation.run()

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
        setupBuildOperationProcessor(maxThreads)
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        def numberOfQueues = 5
        def operations = [
            Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
            Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
            Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
            Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
            Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
        ]

        when:
        async {
            numberOfQueues.times { i ->
                start {
                    def cl = outerOperation.startChild()
                    buildOperationProcessor.run(worker, { queue ->
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
            amountOfWork * operation.run()
        }

        where:
        maxThreads << [1, 4, 10]
    }

    def "failures in one queue do not cause failures in other queues"() {
        given:
        def amountOfWork = 10
        def maxThreads = 4
        setupBuildOperationProcessor(maxThreads)
        def success = Stub(DefaultBuildOperationQueueTest.TestBuildOperation)
        def failure = Stub(DefaultBuildOperationQueueTest.TestBuildOperation) {
            run() >> { throw new Exception() }
        }
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        boolean successfulQueueCompleted = false
        boolean exceptionInFailureQueue = false

        when:
        async {
            // Successful queue
            start {
                def cl = outerOperation.startChild()
                buildOperationProcessor.run(worker, { queue ->
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
                    buildOperationProcessor.run(worker, { queue ->
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
        setupBuildOperationProcessor(threadCount)
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        def operation = Stub(DefaultBuildOperationQueueTest.TestBuildOperation) {
            run() >> {
                throw new GradleException("always fails")
            }
        }

        when:
        buildOperationProcessor.run(worker, { queue ->
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
            create(_, _) >> { buildQueue }
        }
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), buildOperationQueueFactory, Stub(ExecutorFactory), 1)
        def worker = Stub(BuildOperationWorker)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)

        when:
        buildOperationProcessor.run(worker, { queue ->
            4.times { queue.add(operation) }
            throw new Exception("Failure in generator")
        })

        then:
        thrown(BuildOperationQueueFailure)

        and:
        4 * buildQueue.add(_)
        1 * buildQueue.cancel()
    }

    def "multi-cause error when there are failures both enqueueing and running operations"() {
        def operationFailures = [new Exception("failed operation 1"), new Exception("failed operation 2")]
        def buildQueue = Mock(BuildOperationQueue) {
            waitForCompletion() >> { throw new MultipleBuildOperationFailures("operations failed", operationFailures, null) }
        }
        def buildOperationQueueFactory = Mock(BuildOperationQueueFactory) {
            create(_, _) >> { buildQueue }
        }
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), buildOperationQueueFactory, Stub(ExecutorFactory), 1)
        def worker = Stub(BuildOperationWorker)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)

        when:
        buildOperationProcessor.run(worker, { queue ->
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
        setupBuildOperationProcessor(2)
        def operation = Mock(RunnableBuildOperation)

        when:
        buildOperationProcessor.run({ queue ->
            5.times { queue.add(operation) }
        })

        then:
        5 * operation.run()
    }
}
