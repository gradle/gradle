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


class DefaultWorkerLeaseServiceProjectLockTest extends ConcurrentSpec {
    def projectLockBroadcast = Mock(ProjectLockListener)
    def listenerManager = Mock(ListenerManager) {
        _ * getBroadcaster(ProjectLockListener) >> projectLockBroadcast
    }
    def projectLockService = new DefaultWorkerLeaseService(listenerManager, true, 1)

    def "can cleanly lock and unlock a project"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")

        given:
        assert !projectLock.locked
        assert !projectLock.hasProjectLock()

        when:
        projectLock.withProjectLock {
            assert projectLock.locked
            assert projectLock.hasProjectLock()
            executed = true
        }

        then:
        !projectLock.locked
        !projectLock.hasProjectLock()
        executed
    }

    def "can nest calls to withProjectLock"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")

        given:
        assert !projectLock.locked
        assert !projectLock.hasProjectLock()

        when:
        projectLock.withProjectLock {
            projectLock.withProjectLock {
                assert projectLock.locked
                assert projectLock.hasProjectLock()
                executed = true
            }
            assert projectLock.locked
            assert projectLock.hasProjectLock()
        }

        then:
        !projectLock.locked
        !projectLock.hasProjectLock()
        executed
    }

    def "can nest calls to withProjectLock using different projects"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")
        def otherProjectLock = projectLockService.getProjectLock(":otherProject")

        given:
        assert !projectLock.locked
        assert !otherProjectLock.locked
        assert !projectLockService.hasProjectLock()

        when:
        projectLock.withProjectLock {
            otherProjectLock.withProjectLock {
                assert projectLock.locked
                assert otherProjectLock.locked
                executed = true
            }
        }

        then:
        executed

        and:
        !projectLock.locked
        !otherProjectLock.locked
        !projectLockService.hasProjectLock()
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
                    projectLockService.getProjectLock(":project1").withProjectLock {
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
                        boolean success = projectLockService.getProjectLock(":project1").tryWithProjectLock {
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
                    projectLockService.getProjectLock(":project${i}").withProjectLock {
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
        def projectLockService = new DefaultWorkerLeaseService(listenerManager, false, 1)
        def lock = new ReentrantLock()
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    projectLockService.getProjectLock(":project${i}").withProjectLock {
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
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")

        given:
        assert !projectLock.locked
        assert !projectLock.hasProjectLock()

        when:
        projectLock.withProjectLock {
            assert projectLock.locked
            assert projectLock.hasProjectLock()
            projectLockService.withoutProjectLock() {
                assert !projectLock.locked
                assert !projectLock.hasProjectLock()
                executed = true
            }
            assert projectLock.locked
            assert projectLock.hasProjectLock()
        }

        then:
        !projectLock.locked
        !projectLock.hasProjectLock()
        executed
    }

    def "can use withoutProjectLock to temporarily release multiple locks"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")
        def otherProjectLock = projectLockService.getProjectLock(":otherProject")

        given:
        assert !projectLock.locked
        assert !projectLock.hasProjectLock()

        when:
        projectLock.withProjectLock {
            assert projectLock.locked
            otherProjectLock.withProjectLock {
                assert otherProjectLock.locked
                projectLockService.withoutProjectLock() {
                    assert !projectLock.locked
                    assert !otherProjectLock.locked
                    assert !projectLock.hasProjectLock()
                    executed = true
                }
                assert otherProjectLock.locked
            }
            assert !otherProjectLock.locked
            assert projectLock.locked
        }

        then:
        !projectLock.locked
        !otherProjectLock.locked
        !projectLockService.hasProjectLock()
        executed
    }

    def "can use withoutProjectLock when no project is locked"() {
        boolean executed = false

        when:
        projectLockService.withoutProjectLock() {
            executed = true
        }

        then:
        executed
    }

    def "can register a listener to be notified when a project is unlocked"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")

        when:
        projectLock.withProjectLock {
            executed = true
        }

        then:
        executed
        1 * projectLockBroadcast.onProjectUnlock(_)
    }

    def "listener is notified when a project is unlocked using withoutProjectLock"() {
        boolean executed = false
        def projectLock = projectLockService.getProjectLock(":project")

        when:
        projectLock.withProjectLock {
            projectLockService.withoutProjectLock() {
                executed = true
            }
        }

        then:
        executed
        2 * projectLockBroadcast.onProjectUnlock(_)
    }

    def "releases worker leases when waiting on a project lock"() {
        def projectLock = projectLockService.getProjectLock(":project")

        when:
        async {
            projectLock.withProjectLock {
                start {
                    def completion = projectLockService.operationStart()
                    instant.worker1Started
                    projectLock.withProjectLock {
                        instant.worker1Executed
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
        def projectLock = projectLockService.getProjectLock(":project")

        when:
        async {
            start {
                def completion = projectLockService.operationStart()
                projectLock.withProjectLock {
                    instant.worker1Started
                    projectLockService.withoutProjectLock {
                        thread.blockUntil.worker2Started
                    }
                    instant.worker1Executed
                }
                completion.operationFinish()
            }

            thread.blockUntil.worker1Started

            start {
                projectLock.withProjectLock {
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
