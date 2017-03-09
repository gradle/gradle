/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.internal.progress.BuildOperationExecutor.*

class DefaultProjectLockServiceTest extends ConcurrentSpec {
    AtomicInteger idGenerator = new AtomicInteger()
    ProjectLockService projectLockService = new DefaultProjectLockService(true)

    def "can cleanly lock and unlock a project"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
            executed = true
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "can nest calls to withProjectLock"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            projectLockService.withProjectLock(projectPath, operation) {
                assert projectLockService.isLocked(projectPath)
                assert projectLockService.hasLock(operation)
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "can nest calls to withProjectLock with child operations"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            def childOperation = newOperation(operation)
            projectLockService.withProjectLock(projectPath, childOperation) {
                assert projectLockService.isLocked(projectPath)
                assert projectLockService.hasLock(childOperation)
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "a child operation has a project lock if its parent does"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            def childOperation = newOperation(operation)
            assert projectLockService.hasLock(childOperation)
            executed = true
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "multiple tasks can coordinate locking of a project"() {
        def operations = []
        10.times { i ->
            operations << newOperation()
        }
        def started = new CountDownLatch(operations.size())

        when:
        async {
            operations.each { Operation operation ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.withProjectLock(":project1", operation) {
                        assert projectLockService.hasLock(operation)
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "locks on different projects do not affect each other"() {
        def operations = []
        10.times {
            operations << newOperation()
        }
        def started = new CountDownLatch(operations.size())

        when:
        async {
            operations.eachWithIndex { Operation operation, i ->
                start {
                    projectLockService.withProjectLock(":project${i}", operation) {
                        started.countDown()
                        thread.blockUntil.releaseAll
                        assert projectLockService.hasLock(operation)
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "multiple tasks in different projects coordinate on locking of entire build when not in parallel"() {
        def projectLockService = new DefaultProjectLockService(false)
        def lock = new ReentrantLock()
        def operations = []
        10.times { i ->
            operations << newOperation()
        }
        def started = new CountDownLatch(operations.size())

        when:
        async {
            operations.eachWithIndex { Operation operation, i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.withProjectLock(":project${i}", operation) {
                        assert lock.tryLock()
                        try {
                            assert projectLockService.hasLock(operation)
                        } finally {
                            lock.unlock()
                        }
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "can use withoutProjectLock to temporarily release a lock"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
            projectLockService.withoutProjectLock(operation) {
                assert !projectLockService.isLocked(projectPath)
                assert !projectLockService.hasLock(operation)
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "can use withoutProjectLock when the project is not already locked"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withoutProjectLock(operation) {
            assert !projectLockService.isLocked(projectPath)
            assert !projectLockService.hasLock(operation)
            executed = true
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    def "can use withoutProjectLock to temporarily release a lock held by a parent operation"() {
        def operation = newOperation()
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        projectLockService.withProjectLock(projectPath, operation) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
            def childOperation = newOperation(operation)
            projectLockService.withoutProjectLock(childOperation) {
                assert !projectLockService.isLocked(projectPath)
                assert !projectLockService.hasLock(childOperation)
                assert !projectLockService.hasLock(operation)
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock(operation)
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock(operation)
        assert executed
    }

    Operation newOperation(Operation parent=null) {
        int operationId = idGenerator.getAndIncrement()
        return Mock(Operation) {
            _ * getId() >> operationId
            _ * getParentOperation() >> parent
        }
    }
}
