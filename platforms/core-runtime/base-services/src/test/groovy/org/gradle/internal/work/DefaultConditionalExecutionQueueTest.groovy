/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work

import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.Callable

class DefaultConditionalExecutionQueueTest extends ConcurrentSpec {
    private static final DISPLAY_NAME = "Test Execution Queue"
    private static final int MAX_WORKERS = 4
    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, new DefaultParallelismConfiguration(true, 4))
    def queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, MAX_WORKERS, new DefaultExecutorFactory(), workerLeaseService)

    def "can run an action and wait for the result"() {
        def execution = testExecution({
            instant.executionStarted
            println("I'm running!")
            thread.block()
            instant.executionFinished
        })

        when:
        async {
            queue.submit(execution)
            execution.await()
            instant.awaitFinished
        }

        then:
        instant.awaitFinished > instant.executionFinished
    }

    def "can release executions in any order"() {
        def execution1 = testExecution({
            instant.execution1Started
            println("Execution 1 running!")
            thread.blockUntil.execution1Released
        })
        def execution2 = testExecution({
            instant.execution2Started
            println("Execution 2 running!")
            thread.blockUntil.execution2Released
        })
        def execution3 = testExecution({
            instant.execution3Started
            println("Execution 3 running!")
            thread.blockUntil.execution3Released
        })

        expect:
        async {
            queue.submit(execution1)
            queue.submit(execution2)
            queue.submit(execution3)

            thread.blockUntil.execution1Started
            thread.blockUntil.execution2Started
            thread.blockUntil.execution3Started

            instant.execution2Released
            execution2.await()

            instant.execution1Released
            execution1.await()

            instant.execution3Released
            execution3.await()
        }
    }

    def "can submit executions from many threads"() {
        def execution1 = testExecution({
            instant.execution1Started
            println("Execution 1 running!")
        })
        def execution2 = testExecution({
            instant.execution2Started
            println("Execution 2 running!")
        })
        def execution3 = testExecution({
            instant.execution3Started
            println("Execution 3 running!")
        })

        expect:
        async {
            start {
                queue.submit(execution1)
                instant.execution1Submitted
                execution1.await()
            }
            start {
                queue.submit(execution2)
                instant.execution2Submitted
                execution2.await()
            }
            start {
                queue.submit(execution3)
                instant.execution3Submitted
                execution3.await()
            }
            thread.blockUntil.execution1Started
            thread.blockUntil.execution2Started
            thread.blockUntil.execution3Started
        }
    }

    def "can process more executions than max workers (maxWorkers = #maxWorkers)"() {
        def executions = []
        queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, maxWorkers, new DefaultExecutorFactory(), workerLeaseService)

        expect:
        async {
            start {
                int executionCount = maxWorkers * 3
                executionCount.times { i ->
                    def execution = testExecution({
                        println("Execution ${i} running!")
                    })
                    executions.add execution
                    queue.submit(execution)
                }
                executions.each { it.await() }
            }
        }

        and:
        ConcurrentTestUtil.poll {
            queue.workerCount <= maxWorkers
        }

        where:
        maxWorkers << [1, 2, 4]
    }

    def "submitting a large number of executions does not start more than max workers"() {
        def executions = []
        expect:
        async {
            start {
                3000.times { i ->
                    def execution = testExecution({
                        println("Execution ${i} running!")
                    })
                    executions.add(execution)
                    queue.submit(execution)
                }
                instant.allSubmitted
            }
        }
        thread.blockUntil.allSubmitted
        assert queue.workerCount <= MAX_WORKERS
        executions.each { it.await() }

        and:
        ConcurrentTestUtil.poll {
            queue.workerCount <= MAX_WORKERS
        }
    }

    def "can get a result from an execution"() {
        def execution = testExecution({
            println("I'm running!")
            return "foo"
        })
        String result = null

        when:
        async {
            queue.submit(execution)
            result = execution.await()
        }

        then:
        result == "foo"
    }

    def "stopping the queue stops the underlying executor"() {
        ExecutorFactory factory = Mock(ExecutorFactory)
        ManagedExecutor executor = Mock(ManagedExecutor)

        when:
        queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, 4, factory, workerLeaseService)

        then:
        1 * factory.create(_) >> executor

        when:
        queue.stop()

        then:
        1 * executor.stop()
    }

    TestExecution testExecution(Callable<String> callable) {
        return new TestExecution(callable)
    }

    class TestExecution extends AbstractConditionalExecution {
        TestExecution(Callable callable) {
            super(callable)
        }
    }
}
