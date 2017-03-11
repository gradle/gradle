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

import org.gradle.internal.event.ListenerManager
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock


class DefaultProjectLockServiceTest extends ConcurrentSpec {
    def projectLockBroadcast = Mock(ProjectLockListener)
    def listenerManager = Mock(ListenerManager) {
        _ * getBroadcaster(ProjectLockListener) >> projectLockBroadcast
    }
    def projectLockService = new DefaultProjectLockService(listenerManager, true)

    def "can cleanly lock and unlock a project"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock()
            executed = true
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        assert executed
    }

    def "can nest calls to withProjectLock"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withProjectLock(projectPath) {
            projectLockService.withProjectLock(projectPath) {
                assert projectLockService.isLocked(projectPath)
                assert projectLockService.hasLock()
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock()
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        assert executed
    }

    def "cannot nest calls to withProjectLock using different projects"() {
        def projectPath = ":project"
        def otherProjectPath = ":otherProject"
        boolean executed = false

        when:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withProjectLock(projectPath) {
            projectLockService.withProjectLock(otherProjectPath) {
                executed = true
            }
        }

        then:
        thrown(UnsupportedOperationException)

        and:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        assert !executed
    }

    def "multiple tasks can coordinate locking of a project"() {
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times {
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.withProjectLock(":project1") {
                        assert projectLockService.hasLock()
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
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    projectLockService.withProjectLock(":project${i}") {
                        started.countDown()
                        thread.blockUntil.releaseAll
                        assert projectLockService.hasLock()
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "multiple threads can coordinate on locking of entire build when not in parallel"() {
        def projectLockService = new DefaultProjectLockService(listenerManager, false)
        def lock = new ReentrantLock()
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.withProjectLock(":project${i}") {
                        assert lock.tryLock()
                        try {
                            assert projectLockService.hasLock()
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
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock()
            projectLockService.withoutProjectLock() {
                assert !projectLockService.isLocked(projectPath)
                assert !projectLockService.hasLock()
                executed = true
            }
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock()
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        assert executed
    }

    def "can use withoutProjectLock when the project is not already locked"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withoutProjectLock() {
            assert !projectLockService.isLocked(projectPath)
            assert !projectLockService.hasLock()
            executed = true
        }
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        assert executed
    }

    def "can lock and unlock a project in conjunction with withProjectLock"() {
        def projectPath = ":project"
        boolean executed = false

        when:
        projectLockService.lockProject(projectPath)
        assert projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isLocked(projectPath)
            assert projectLockService.hasLock()
            executed = true
        }

        then:
        assert executed
        assert !projectLockService.isLocked(projectPath)
        assert !projectLockService.hasLock()

        when:
        projectLockService.unlockProject(projectPath)

        then:
        noExceptionThrown()
    }

    def "can register a listener to be notified when a project is unlocked"() {
        def projectPath = ":project"
        boolean executed = false

        when:
        projectLockService.withProjectLock(projectPath) {
            executed = true
        }

        then:
        executed
        1 * projectLockBroadcast.onProjectUnlock(_)
    }

    def "listener is notified when a project is unlocked using withoutProjectLock"() {
        def projectPath = ":project"
        boolean executed = false

        when:
        projectLockService.withProjectLock(projectPath) {
            projectLockService.withoutProjectLock() {
                executed = true
            }
        }

        then:
        executed
        2 * projectLockBroadcast.onProjectUnlock(_)
    }
}
