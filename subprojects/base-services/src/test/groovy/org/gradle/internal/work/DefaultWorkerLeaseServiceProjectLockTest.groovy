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

import org.gradle.api.Transformer
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.internal.MutableBoolean
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockState
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock
import static org.gradle.util.Path.path

class DefaultWorkerLeaseServiceProjectLockTest extends ConcurrentSpec {
    @Rule
    SetSystemProperties properties = new SetSystemProperties()
    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, parallel())

    def "can cleanly lock and unlock a project"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        given:
        assert !lockIsHeld(projectLock)

        when:
        workerLeaseService.withLocks([projectLock]) {
            assert lockIsHeld(projectLock)
        }

        then:
        !lockIsHeld(projectLock)
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
                    def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
                    workerLeaseService.withLocks([projectLock]) {
                        assert lockIsHeld(projectLock)
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "multiple threads can coordinate locking of a project using tryLock"() {
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times {
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    while (true) {
                        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
                        boolean success = coordinationService.withStateLock(tryLock(projectLock))
                        try {
                            if (success) {
                                assert lockIsHeld(projectLock)
                                break
                            } else {
                                sleep(20)
                            }
                        } finally {
                            coordinationService.withStateLock(unlock(projectLock))
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
                    def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project${i}"))
                    workerLeaseService.withLocks([projectLock]) {
                        started.countDown()
                        thread.blockUntil.releaseAll
                        assert lockIsHeld(projectLock)
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
        def projectLockService = new DefaultWorkerLeaseService(coordinationService, notParallel())
        def testLock = new ReentrantLock()
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    def projectLock = projectLockService.getProjectLock(path("root"), path(":project${i}"))
                    workerLeaseService.withLocks([projectLock]) {
                        assert testLock.tryLock()
                        try {
                            assert lockIsHeld(projectLock)
                        } finally {
                            testLock.unlock()
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

    def "multiple threads can coordinate on locking of multiple builds when not in parallel"() {
        def projectLockService = new DefaultWorkerLeaseService(coordinationService, notParallel())
        def threadCount = 20
        def buildCount = 4
        def testLock = []
        buildCount.times { i -> testLock[i] = new ReentrantLock() }
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    def buildIndex = i % buildCount
                    def projectLock = projectLockService.getProjectLock(path("build${buildIndex}"), path(":project${i}"))
                    workerLeaseService.withLocks([projectLock]) {
                        assert testLock[buildIndex].tryLock()
                        try {
                            assert lockIsHeld(projectLock)
                        } finally {
                            testLock[buildIndex].unlock()
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
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        given:
        assert !lockIsHeld(projectLock)

        when:
        workerLeaseService.withLocks([projectLock]) {
            assert lockIsHeld(projectLock)
            workerLeaseService.withoutProjectLock() {
                assert !lockIsHeld(projectLock)
                executed = true
            }
            assert lockIsHeld(projectLock)
        }

        then:
        !lockIsHeld(projectLock)
        executed
    }

    def "can use withoutProjectLock to temporarily release multiple locks"() {
        boolean executed = false
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
        def otherProjectLock = workerLeaseService.getProjectLock(path("root"), path(":otherProject"))

        given:
        assert !lockIsHeld(projectLock)

        when:
        workerLeaseService.withLocks([projectLock, otherProjectLock]) {
            assert lockIsHeld(projectLock)
            assert lockIsHeld(otherProjectLock)
            workerLeaseService.withoutProjectLock {
                assert !lockIsHeld(projectLock)
                assert !lockIsHeld(otherProjectLock)
                executed = true
            }
            assert lockIsHeld(projectLock)
            assert lockIsHeld(otherProjectLock)
        }

        then:
        !lockIsHeld(projectLock)
        !lockIsHeld(otherProjectLock)
        executed
    }

    def "can use withoutProjectLock when no project is locked"() {
        boolean executed = false

        when:
        workerLeaseService.withoutProjectLock {
            executed = true
        }

        then:
        executed
    }

    def "withoutProjectLock releases worker leases when waiting on a project lock"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        async {
            start {
                def workerLease = workerLeaseService.getWorkerLease()
                workerLeaseService.withLocks([projectLock, workerLease]) {
                    workerLeaseService.withoutProjectLock {
                        thread.blockUntil.projectLocked
                    }
                    instant.worker1Executed
                }
            }

            workerLeaseService.withLocks([projectLock]) {
                instant.projectLocked
                start {
                    def workerLease = workerLeaseService.getWorkerLease()
                    coordinationService.withStateLock(lock(workerLease))
                    try {
                        instant.worker2Executed
                    } finally {
                        coordinationService.withStateLock(unlock(workerLease))
                    }
                }
                thread.blockUntil.worker2Executed
            }
        }

        then:
        instant.worker1Executed > instant.worker2Executed
    }

    def "gathers statistics when acquiring a project lock and statistics flag is set"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        System.setProperty(DefaultWorkerLeaseService.PROJECT_LOCK_STATS_PROPERTY, "")
        boolean thread2Executed = false
        async {
            start {
                workerLeaseService.withLocks([projectLock]) {
                    instant.thread1
                    thread.blockUntil.thread2
                    sleep 100
                }
            }
            start {
                thread.blockUntil.thread1
                instant.thread2
                workerLeaseService.withLocks([projectLock]) {
                    thread2Executed = true
                }
            }
        }

        then:
        workerLeaseService.projectLockStatistics.totalWaitTimeMillis > -1
    }

    def "fails when attempting to acquire a project lock and changes are disallowed"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        workerLeaseService.whileDisallowingProjectLockChanges {
            workerLeaseService.withLocks([projectLock]) {
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "This thread may not acquire more locks."

        when:
        workerLeaseService.whileDisallowingProjectLockChanges {
            workerLeaseService.whileDisallowingProjectLockChanges {}
            workerLeaseService.withLocks([projectLock]) {
            }
        }

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "This thread may not acquire more locks."
    }

    def "fails when attempting to release a project lock and changes are disallowed"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        workerLeaseService.withLocks([projectLock]) {
            workerLeaseService.whileDisallowingProjectLockChanges {
                workerLeaseService.withoutProjectLock {}
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "This thread may not release any locks."
    }

    def "does not release project locks in blocking action when changes to locks are disallowed"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        expect:
        workerLeaseService.withLocks([projectLock]) {
            workerLeaseService.whileDisallowingProjectLockChanges {
                assert lockIsHeld(projectLock)
                workerLeaseService.blocking {
                    assert lockIsHeld(projectLock)
                }
                assert lockIsHeld(projectLock)
            }
        }
    }

    def "releases and reacquires project locks in blocking action when changes to locks are allowed"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        expect:
        workerLeaseService.withLocks([projectLock]) {
            assert lockIsHeld(projectLock)
            workerLeaseService.blocking {
                assert !lockIsHeld(projectLock)
            }
            assert lockIsHeld(projectLock)
        }
    }

    def "thread can be granted uncontrolled access to any project"() {
        expect:
        !workerLeaseService.isAllowedUncontrolledAccessToAnyProject()
        def result = workerLeaseService.allowUncontrolledAccessToAnyProject {
            assert workerLeaseService.isAllowedUncontrolledAccessToAnyProject()
            workerLeaseService.allowUncontrolledAccessToAnyProject {
                assert workerLeaseService.isAllowedUncontrolledAccessToAnyProject()
            }
            assert workerLeaseService.isAllowedUncontrolledAccessToAnyProject()
            "result"
        }
        result == "result"
        !workerLeaseService.isAllowedUncontrolledAccessToAnyProject()
    }

    def "does not release project locks in blocking action when thread has uncontrolled access to any project"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        expect:
        workerLeaseService.withLocks([projectLock]) {
            workerLeaseService.allowUncontrolledAccessToAnyProject {
                assert lockIsHeld(projectLock)
                workerLeaseService.blocking {
                    assert lockIsHeld(projectLock)
                }
                assert lockIsHeld(projectLock)
            }
        }
    }

    def "does not gather statistics when statistics flag is not set"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        boolean thread2Executed = false
        async {
            start {
                workerLeaseService.withLocks([projectLock]) {
                    instant.thread1
                    thread.blockUntil.thread2
                    sleep 10
                }
            }
            start {
                thread.blockUntil.thread1
                instant.thread2
                workerLeaseService.withLocks([projectLock]) {
                    thread2Executed = true
                }
            }
        }

        then:
        workerLeaseService.projectLockStatistics.totalWaitTimeMillis == -1
    }

    def "does not gather statistics when not acquiring project lock"() {
        def workerLease = workerLeaseService.getWorkerLease()

        when:
        System.setProperty(DefaultWorkerLeaseService.PROJECT_LOCK_STATS_PROPERTY, "")
        boolean thread2Executed = false
        async {
            start {
                workerLeaseService.withLocks([workerLease]) {
                    instant.thread1
                    thread.blockUntil.thread2
                    sleep 10
                }
            }
            start {
                thread.blockUntil.thread1
                instant.thread2
                workerLeaseService.withLocks([workerLease]) {
                    thread2Executed = true
                }
            }
        }

        then:
        workerLeaseService.projectLockStatistics.totalWaitTimeMillis == -1
    }

    boolean lockIsHeld(final ResourceLock resourceLock) {
        MutableBoolean held = new MutableBoolean()
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                held.set(resourceLock.locked && resourceLock.isLockedByCurrentThread())
                return ResourceLockState.Disposition.FINISHED
            }
        })
        return held.get()
    }

    ParallelismConfiguration parallel(boolean parallelEnabled) {
        return new DefaultParallelismConfiguration(parallelEnabled, 1)
    }

    ParallelismConfiguration notParallel() {
        return parallel(false)
    }

    ParallelismConfiguration parallel() {
        return parallel(true)
    }
}
