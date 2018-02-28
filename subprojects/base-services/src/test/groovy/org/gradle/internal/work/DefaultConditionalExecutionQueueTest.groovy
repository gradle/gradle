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
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Unroll

import java.util.concurrent.Callable

class DefaultConditionalExecutionQueueTest extends ConcurrentSpec {
    private static final DISPLAY_NAME = "Test Execution Queue"
    ResourceLockCoordinationService coordinationService = new DefaultResourceLockCoordinationService()
    DefaultConditionalExecutionQueue queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, 4, new DefaultExecutorFactory(), coordinationService)

    def "can conditionally execute a runnable"() {
        def execution = testExecution({
            instant.executionStarted
            println("I'm running!")
        })

        when:
        async {
            start {
                queue.submit(execution)
                execution.await()
            }
            start {
                instant.canExecute
                release(execution)
            }
        }

        then:
        instant.executionStarted > instant.canExecute

        and:
        execution.resourceLock.released
    }

    def "can release executions in any order"() {
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

        when:
        async {
            start {
                queue.submit(execution1)
                queue.submit(execution2)
                queue.submit(execution3)
            }
            start {
                release(execution2)
                execution2.await()

                release(execution1)
                execution1.await()

                release(execution3)
                execution3.await()
            }
        }

        then:
        instant.execution2Started < instant.execution1Started
        instant.execution1Started < instant.execution3Started
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
            start {
                thread.blockUntil.execution1Submitted
                thread.blockUntil.execution2Submitted
                thread.blockUntil.execution3Submitted
                release(execution1)
                release(execution2)
                release(execution3)
            }
        }
    }

    def "can submit executions immediately ready to run"() {
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
                release(execution1)
                release(execution2)
                release(execution3)
                queue.submit(execution1)
                queue.submit(execution2)
                queue.submit(execution3)
            }
            start {
                execution1.await()
                execution2.await()
                execution3.await()
            }
        }
    }

    @Unroll
    def "can process more executions than max workers (maxWorkers = #maxWorkers)"() {
        def executions = []
        queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, maxWorkers, new DefaultExecutorFactory(), coordinationService)

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
                executions.each { release(it) }
                executions.each { it.await() }
            }
        }

        where:
        maxWorkers << [1, 2, 4]
    }

    def "can get a result from an execution"() {
        def execution = testExecution({
            instant.executionStarted
            println("I'm running!")
            return "foo"
        })
        String result = null

        when:
        async {
            start {
                queue.submit(execution)
                result = execution.await()
            }
            start {
                instant.canExecute
                release(execution)
            }
        }

        then:
        instant.executionStarted > instant.canExecute

        and:
        result == "foo"
    }

    def "stopping the queue stops the underlying executor"() {
        ExecutorFactory factory = Mock(ExecutorFactory)
        ManagedExecutor executor = Mock(ManagedExecutor)

        when:
        queue = new DefaultConditionalExecutionQueue(DISPLAY_NAME, 4, factory, coordinationService)

        then:
        1 * factory.create(_, 4) >> executor

        when:
        queue.stop()

        then:
        1 * executor.stop()
    }

    void release(TestExecution execution) {
        execution.setCanExecute(true)
        coordinationService.notifyStateChange()
    }

    TestExecution testExecution(Callable<String> callable) {
        return new TestExecution(callable, new SimpleResourceLock())
    }

    class TestExecution extends AbstractConditionalExecution {
        final SimpleResourceLock resourceLock

        TestExecution(Callable callable, SimpleResourceLock resourceLock) {
            super(callable, resourceLock)
            this.resourceLock = resourceLock
        }

        void setCanExecute(boolean canExecute) {
            this.resourceLock.canExecute = canExecute
        }
    }

    class SimpleResourceLock implements ResourceLock {
        boolean canExecute
        boolean released
        boolean locked

        @Override
        boolean isLocked() {
            return false
        }

        @Override
        boolean isLockedByCurrentThread() {
            return true
        }

        @Override
        boolean tryLock() {
            if (canExecute) {
                locked = true
                return true
            } else {
                return false
            }
        }

        @Override
        void unlock() {
            locked = false
            released = true
            canExecute = false
        }

        @Override
        String getDisplayName() {
            return "simple lock"
        }
    }
}
