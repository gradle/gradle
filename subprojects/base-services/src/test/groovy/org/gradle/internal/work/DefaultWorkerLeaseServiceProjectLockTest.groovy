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
import org.gradle.internal.MutableBoolean
import org.gradle.internal.concurrent.ParallelismConfigurationManager
import org.gradle.internal.concurrent.ParallelismConfigurationManagerFixture
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockState
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock

class DefaultWorkerLeaseServiceProjectLockTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService();
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, parallel())

    def "can cleanly lock and unlock a project"() {
        def projectLock = workerLeaseService.getProjectLock("root", ":project")

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
                    def projectLock = workerLeaseService.getProjectLock("root", ":project")
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
                        def projectLock = workerLeaseService.getProjectLock("root", ":project")
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
                    def projectLock = workerLeaseService.getProjectLock("root", ":project${i}")
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
                    def projectLock = projectLockService.getProjectLock("root", ":project${i}")
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
                    def projectLock = projectLockService.getProjectLock("build${buildIndex}", ":project${i}")
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
        def projectLock = workerLeaseService.getProjectLock("root", ":project")

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
        def projectLock = workerLeaseService.getProjectLock("root", ":project")
        def otherProjectLock = workerLeaseService.getProjectLock("root", ":otherProject")

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
        def projectLock = workerLeaseService.getProjectLock("root", ":project")

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

    ParallelismConfigurationManager parallel(boolean parallelEnabled) {
        return new ParallelismConfigurationManagerFixture(parallelEnabled, 1)
    }

    ParallelismConfigurationManager notParallel() {
        return parallel(false)
    }

    ParallelismConfigurationManager parallel() {
        return parallel(true)
    }
}
