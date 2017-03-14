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

package org.gradle.internal.work

import org.gradle.internal.event.ListenerManager
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock


class DefaultWorkerManagementServiceProjectLockTest extends ConcurrentSpec {
    def projectLockBroadcast = Mock(ProjectLockListener)
    def listenerManager = Mock(ListenerManager) {
        _ * getBroadcaster(ProjectLockListener) >> projectLockBroadcast
    }
    def projectLockService = new DefaultWorkerManagementService(listenerManager, true, 1)

    def "can cleanly lock and unlock a project"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isProjectLocked(projectPath)
            assert projectLockService.hasProjectLock()
            executed = true
        }
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        assert executed
    }

    def "can nest calls to withProjectLock"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withProjectLock(projectPath) {
            projectLockService.withProjectLock(projectPath) {
                assert projectLockService.isProjectLocked(projectPath)
                assert projectLockService.hasProjectLock()
                executed = true
            }
            assert projectLockService.isProjectLocked(projectPath)
            assert projectLockService.hasProjectLock()
        }
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        assert executed
    }

    def "can nest calls to withProjectLock using different projects"() {
        def projectPath = ":project"
        def otherProjectPath = ":otherProject"
        boolean executed = false

        when:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withProjectLock(projectPath) {
            projectLockService.withProjectLock(otherProjectPath) {
                executed = true
            }
        }

        then:
        assert executed

        and:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.isProjectLocked(otherProjectPath)
        assert !projectLockService.hasProjectLock()
    }

    def "multiple threads can coordinate locking of a project"() {
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times {
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.withProjectLock(":project1") {
                        assert projectLockService.hasProjectLock()
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "multiple threads can coordinate locking of a project using tryWithProjectLock"() {
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times {
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    while (true) {
                        boolean success = projectLockService.tryWithProjectLock(":project1") {
                            assert projectLockService.hasProjectLock()
                        }
                        if (success) {
                            break
                        } else {
                            sleep(20)
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
                        assert projectLockService.hasProjectLock()
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
        def projectLockService = new DefaultWorkerManagementService(listenerManager, false, 1)
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
                            assert projectLockService.hasProjectLock()
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
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isProjectLocked(projectPath)
            assert projectLockService.hasProjectLock()
            projectLockService.withoutProjectLock() {
                assert !projectLockService.isProjectLocked(projectPath)
                assert !projectLockService.hasProjectLock()
                executed = true
            }
            assert projectLockService.isProjectLocked(projectPath)
            assert projectLockService.hasProjectLock()
        }
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        assert executed
    }

    def "can use withoutProjectLock to temporarily release multiple locks"() {
        def projectPath = ":project"
        def otherProjectPath = ":otherProject"
        boolean executed = false

        expect:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withProjectLock(projectPath) {
            assert projectLockService.isProjectLocked(projectPath)
            projectLockService.withProjectLock(otherProjectPath) {
                assert projectLockService.isProjectLocked(otherProjectPath)
                projectLockService.withoutProjectLock() {
                    assert !projectLockService.isProjectLocked(projectPath)
                    assert !projectLockService.isProjectLocked(otherProjectPath)
                    assert !projectLockService.hasProjectLock()
                    executed = true
                }
                assert projectLockService.isProjectLocked(otherProjectPath)
            }
            assert projectLockService.isProjectLocked(projectPath)
            assert projectLockService.hasProjectLock()
        }
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.isProjectLocked(otherProjectPath)
        assert !projectLockService.hasProjectLock()
        assert executed
    }

    def "can use withoutProjectLock when the project is not already locked"() {
        def projectPath = ":project"
        boolean executed = false

        expect:
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        projectLockService.withoutProjectLock() {
            assert !projectLockService.isProjectLocked(projectPath)
            assert !projectLockService.hasProjectLock()
            executed = true
        }
        assert !projectLockService.isProjectLocked(projectPath)
        assert !projectLockService.hasProjectLock()
        assert executed
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

    def "releases worker leases when waiting on a project lock"() {
        def projectPath = ":project"
        boolean executed = false

        when:
        async {
            projectLockService.withProjectLock(projectPath) {
                start {
                    def completion = projectLockService.operationStart()
                    instant.worker1Started
                    projectLockService.withProjectLock(projectPath) {
                        instant.worker1Executed
                        executed = true
                    }
                    completion.operationFinish()
                }

                thread.blockUntil.worker1Started

                start {
                    def completion = projectLockService.operationStart()
                    instant.worker2Executed
                    completion.operationFinish()
                }

                thread.blockUntil.worker2Executed
            }
        }

        then:
        instant.worker1Executed > instant.worker2Executed
    }

    def "releases worker leases when waiting to reacquire a project lock"() {
        def projectPath = ":project"
        boolean executed = false

        when:
        async {
            start {
                def completion = projectLockService.operationStart()
                projectLockService.withProjectLock(projectPath) {
                    instant.worker1Started
                    projectLockService.withoutProjectLock {
                        executed = true
                        thread.blockUntil.worker2Started
                    }
                    instant.worker1Executed
                }
                completion.operationFinish()
            }

            thread.blockUntil.worker1Started

            start {
                projectLockService.withProjectLock(projectPath) {
                    instant.worker2Started
                    def completion = projectLockService.operationStart()
                    instant.worker2Executed
                    completion.operationFinish()
                }
            }
        }

        then:
        instant.worker1Executed > instant.worker2Executed
    }
}
