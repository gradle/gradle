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


import org.gradle.internal.InternalTransformer
import org.gradle.internal.MutableBoolean
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockState
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock
import static org.gradle.util.Path.path

class DefaultWorkerLeaseServiceProjectLockTest extends AbstractWorkerLeaseServiceTest {
    @Rule
    SetSystemProperties properties = new SetSystemProperties()
    def workerLeaseService = workerLeaseService()

    def "can lock and unlock a project"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        given:
        assert !lockIsHeld(projectLock)
        assert workerLeaseService.currentProjectLocks.empty

        when:
        workerLeaseService.withLocks([projectLock]) {
            assert lockIsHeld(projectLock)
        }

        then:
        !lockIsHeld(projectLock)
        assert workerLeaseService.currentProjectLocks.empty
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
        def projectLockService = workerLeaseService(notParallel())
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
        def projectLockService = workerLeaseService(notParallel())
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

    def "locking task execution lease also locks project state when parallel execution disabled"() {
        def workerLeaseService = workerLeaseService(notParallel())
        def taskLease = workerLeaseService.getTaskExecutionLock(path("build"), path("project"))
        def projectLock = workerLeaseService.getProjectLock(path("build"), path("project"))

        expect:
        !taskLease.is(projectLock)
        !lockIsHeld(taskLease)
        !lockIsHeld(projectLock)
        workerLeaseService.withLocks([taskLease]) {
            assert lockIsHeld(taskLease)
            assert lockIsHeld(projectLock)
        }
        !lockIsHeld(taskLease)
        !lockIsHeld(projectLock)
    }

    def "can release and reacquire project lock while holding task execution lease"() {
        def workerLeaseService = workerLeaseService(notParallel())
        def taskLease = workerLeaseService.getTaskExecutionLock(path("build"), path("project"))
        def projectLock = workerLeaseService.getProjectLock(path("build"), path("project"))

        expect:
        workerLeaseService.withLocks([taskLease]) {
            assert lockIsHeld(taskLease)
            assert lockIsHeld(projectLock)
            workerLeaseService.withoutLocks([projectLock]) {
                assert lockIsHeld(taskLease)
                assert !lockIsHeld(projectLock)
            }
            assert lockIsHeld(taskLease)
            assert lockIsHeld(projectLock)
        }
    }

    def "can acquire task execution lease while holding the project lock"() {
        def workerLeaseService = workerLeaseService(notParallel())
        def taskLease = workerLeaseService.getTaskExecutionLock(path("build"), path("project"))
        def projectLock = workerLeaseService.getProjectLock(path("build"), path("project"))

        expect:
        workerLeaseService.withLocks([projectLock]) {
            assert !lockIsHeld(taskLease)
            assert lockIsHeld(projectLock)
            workerLeaseService.withLocks([taskLease]) {
                assert lockIsHeld(taskLease)
                assert lockIsHeld(projectLock)
            }
            assert !lockIsHeld(taskLease)
            // maybe reconsider this; should probably continue to hold the project lock
            assert !lockIsHeld(projectLock)
        }
    }

    def "locking task execution lease blocks when other thread holds task execution lease"() {
        def workerLeaseService = workerLeaseService(notParallel())
        def taskLease = workerLeaseService.getTaskExecutionLock(path("build"), path("project"))

        when:
        async {
            start {
                workerLeaseService.withLocks([taskLease]) {
                    instant.worker1Locked
                    thread.block()
                    instant.worker1Unlocked
                }
            }
            start {
                thread.blockUntil.worker1Locked
                workerLeaseService.withLocks([taskLease]) {
                    instant.worker2Locked
                    assert lockIsHeld(taskLease)
                }
            }
        }

        then:
        instant.worker2Locked > instant.worker1Unlocked
    }

    def "locking task execution lease blocks when other thread holds project lock"() {
        def workerLeaseService = workerLeaseService(notParallel())
        def taskLease = workerLeaseService.getTaskExecutionLock(path("build"), path("project"))
        def projectLock = workerLeaseService.getProjectLock(path("build"), path("project"))

        when:
        async {
            start {
                workerLeaseService.withLocks([projectLock]) {
                    instant.projectLocked
                    thread.block()
                    instant.projectUnlocked
                }
            }
            start {
                thread.blockUntil.projectLocked
                workerLeaseService.withLocks([taskLease]) {
                    instant.taskLeaseLocked
                    assert lockIsHeld(taskLease)
                    assert lockIsHeld(projectLock)
                }
            }
        }

        then:
        instant.taskLeaseLocked > instant.projectUnlocked
    }

    def "can lock and unlock all projects for a build"() {
        def allProjectsLock = workerLeaseService.getAllProjectsLock(path("root"))
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
        def otherBuildAllProjectsLock = workerLeaseService.getAllProjectsLock(path("other"))
        def otherBuildProjectLock = workerLeaseService.getProjectLock(path("other"), path(":project"))

        given:
        assert !lockIsHeld(allProjectsLock)
        assert workerLeaseService.currentProjectLocks.empty

        when:
        workerLeaseService.withLocks([allProjectsLock]) {
            assert lockIsHeld(allProjectsLock)
            assert !lockIsHeld(projectLock)
            assert !lockIsHeld(otherBuildAllProjectsLock)
            assert !lockIsHeld(otherBuildProjectLock)
            assert workerLeaseService.currentProjectLocks == [allProjectsLock]
        }

        then:
        !lockIsHeld(allProjectsLock)
        assert workerLeaseService.currentProjectLocks.empty
    }

    def "cannot acquire project lock while all projects lock for build is held by another thread"() {
        def allProjectsLock = workerLeaseService.getAllProjectsLock(path("root"))
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        async {
            start {
                workerLeaseService.withLocks([allProjectsLock]) {
                    instant.allLocked
                    assert lockIsHeld(allProjectsLock)
                    assert !lockIsHeld(projectLock)
                    assert workerLeaseService.currentProjectLocks == [allProjectsLock]
                    thread.block()
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks([projectLock]) {
                    instant.projectLocked
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "can acquire project lock while all projects lock for another build is held by another thread"() {
        def allProjectsLock = workerLeaseService.getAllProjectsLock(path("other"))
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        async {
            start {
                workerLeaseService.withLocks([allProjectsLock]) {
                    instant.allLocked
                    assert lockIsHeld(allProjectsLock)
                    assert !lockIsHeld(projectLock)
                    thread.blockUntil.projectLocked
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks([projectLock]) {
                    instant.projectLocked
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "cannot acquire all projects lock for build while it is held by another thread"() {
        def allProjectsLock = workerLeaseService.getAllProjectsLock(path("root"))

        when:
        async {
            start {
                workerLeaseService.withLocks([allProjectsLock]) {
                    instant.allLocked
                    assert lockIsHeld(allProjectsLock)
                    assert workerLeaseService.currentProjectLocks == [allProjectsLock]
                    thread.block()
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks([allProjectsLock]) {
                    instant.projectLocked
                    assert workerLeaseService.currentProjectLocks == [allProjectsLock]
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "can acquire all projects lock for build while another is held by another thread"() {
        def allProjectsLock = workerLeaseService.getAllProjectsLock(path("root"))
        def otherLock = workerLeaseService.getAllProjectsLock(path("other"))

        when:
        async {
            start {
                workerLeaseService.withLocks([otherLock]) {
                    instant.allLocked
                    assert lockIsHeld(otherLock)
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks([allProjectsLock]) {
                    instant.projectLocked
                    assert lockIsHeld(allProjectsLock)
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "can use runAsIsolatedTask to temporarily release project lock"() {
        boolean executed = false
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        given:
        assert !lockIsHeld(projectLock)

        when:
        workerLeaseService.withLocks([projectLock]) {
            assert lockIsHeld(projectLock)
            workerLeaseService.runAsIsolatedTask() {
                assert !lockIsHeld(projectLock)
                executed = true
            }
            assert lockIsHeld(projectLock)
        }

        then:
        !lockIsHeld(projectLock)
        executed
    }

    def "can use runAsIsolatedTask to temporarily release multiple locks"() {
        boolean executed = false
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
        def otherProjectLock = workerLeaseService.getProjectLock(path("root"), path(":otherProject"))

        given:
        assert !lockIsHeld(projectLock)

        when:
        workerLeaseService.withLocks([projectLock, otherProjectLock]) {
            assert lockIsHeld(projectLock)
            assert lockIsHeld(otherProjectLock)
            workerLeaseService.runAsIsolatedTask {
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

    def "can use runAsIsolatedTask when no project is locked"() {
        boolean executed = false

        when:
        workerLeaseService.runAsIsolatedTask {
            executed = true
        }

        then:
        executed
    }

    def "runAsIsolatedTask releases worker leases when waiting on a project lock"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        when:
        async {
            start {
                def workerLease = workerLeaseService.newWorkerLease()
                workerLeaseService.withLocks([projectLock, workerLease]) {
                    workerLeaseService.runAsIsolatedTask {
                        thread.blockUntil.projectLocked
                    }
                    instant.worker1Executed
                }
            }

            workerLeaseService.withLocks([projectLock]) {
                instant.projectLocked
                start {
                    def workerLease = workerLeaseService.newWorkerLease()
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
                workerLeaseService.runAsIsolatedTask {}
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "This thread may not release any locks."
    }

    def "releases worker lease but does not release project locks in blocking action when changes to locks are disallowed"() {
        def lease = workerLeaseService.newWorkerLease()
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        expect:
        workerLeaseService.withLocks([projectLock, lease]) {
            workerLeaseService.whileDisallowingProjectLockChanges {
                assert lockIsHeld(lease)
                assert lockIsHeld(projectLock)
                workerLeaseService.blocking {
                    assert !lockIsHeld(lease)
                    assert lockIsHeld(projectLock)
                }
                assert lockIsHeld(lease)
                assert lockIsHeld(projectLock)
            }
        }
    }

    def "releases and reacquires project locks in blocking action when changes to locks are allowed"() {
        def lease = workerLeaseService.newWorkerLease()
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))

        expect:
        workerLeaseService.withLocks([projectLock, lease]) {
            assert lockIsHeld(lease)
            assert lockIsHeld(projectLock)
            workerLeaseService.blocking {
                assert !lockIsHeld(lease)
                assert !lockIsHeld(projectLock)
            }
            assert lockIsHeld(lease)
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

    def "releases worker lease but does not release project locks in blocking action when thread has uncontrolled access to any project"() {
        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
        def lease = workerLeaseService.newWorkerLease()

        expect:
        workerLeaseService.withLocks([projectLock, lease]) {
            workerLeaseService.allowUncontrolledAccessToAnyProject {
                assert lockIsHeld(lease)
                assert lockIsHeld(projectLock)
                workerLeaseService.blocking {
                    assert !lockIsHeld(lease)
                    assert lockIsHeld(projectLock)
                }
                assert lockIsHeld(lease)
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
        def workerLease = workerLeaseService.newWorkerLease()

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
        coordinationService.withStateLock(new InternalTransformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                held.set(resourceLock.locked && resourceLock.isLockedByCurrentThread())
                return ResourceLockState.Disposition.FINISHED
            }
        })
        return held.get()
    }
}
