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

import org.gradle.internal.MutableBoolean
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockState

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock
import static org.gradle.util.Path.path

class DefaultWorkerLeaseServiceProjectLockTest extends AbstractWorkerLeaseServiceTest {

    def workerLeaseService = workerLeaseService(1)

    def "can lock and unlock a project"() {
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
                    def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))
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
                        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))
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
                    def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project${i}"))
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
        def projectLockService = workerLeaseService(false)
        def testLock = new ReentrantLock()
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times { i ->
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    def projectLock = projectLockService.getProjectLock(path(":"), path(":project${i}"))
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
        def projectLockService = workerLeaseService(false)
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
                    def projectLock = projectLockService.getProjectLock(path(":build${buildIndex}"), path(":project${i}"))
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
        def workerLeaseService = workerLeaseService(false)
        def taskLease = workerLeaseService.getTaskExecutionLock(path(":"), path(":project"))
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def workerLeaseService = workerLeaseService(false)
        def taskLease = workerLeaseService.getTaskExecutionLock(path(":"), path(":project"))
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def workerLeaseService = workerLeaseService(false)
        def taskLease = workerLeaseService.getTaskExecutionLock(path(":"), path(":project"))
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def workerLeaseService = workerLeaseService(false)
        def taskLease = workerLeaseService.getTaskExecutionLock(path(":"), path(":project"))

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
        def workerLeaseService = workerLeaseService(false)
        def taskLease = workerLeaseService.getTaskExecutionLock(path(":"), path(":project"))
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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

    def "can lock and unlock all projects of a build"() {
        def projectLocks = allProjectLocksOf(":", [":a", ":b"])
        def otherBuildProjectLocks = allProjectLocksOf(":other", [":other:a", ":other:b"])

        given:
        assert workerLeaseService.currentProjectLocks.empty

        when:
        workerLeaseService.withLocks(projectLocks) {
            projectLocks.each { assert lockIsHeld(it) }
            otherBuildProjectLocks.each { assert !lockIsHeld(it) }
            assert workerLeaseService.currentProjectLocks as Set == projectLocks as Set
        }

        then:
        projectLocks.every { !lockIsHeld(it) }
        workerLeaseService.currentProjectLocks.empty
    }

    def "cannot acquire the lock of a project while all project locks of its build are held by another thread"() {
        def projectLocks = allProjectLocksOf(":", [":a", ":b"])
        def projectLock = projectLocks[0]

        when:
        async {
            start {
                workerLeaseService.withLocks(projectLocks) {
                    instant.allLocked
                    projectLocks.each { assert lockIsHeld(it) }
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

    def "can acquire the lock of a project while all project locks of another build are held by another thread"() {
        def otherBuildProjectLocks = allProjectLocksOf(":other", [":other:a", ":other:b"])
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":a"))

        when:
        async {
            start {
                workerLeaseService.withLocks(otherBuildProjectLocks) {
                    instant.allLocked
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

    def "cannot acquire all project locks of a build while they are held by another thread"() {
        def projectLocks = allProjectLocksOf(":", [":a", ":b"])

        when:
        async {
            start {
                workerLeaseService.withLocks(projectLocks) {
                    instant.allLocked
                    projectLocks.each { assert lockIsHeld(it) }
                    thread.block()
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks(projectLocks) {
                    instant.projectLocked
                    assert workerLeaseService.currentProjectLocks as Set == projectLocks as Set
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "can acquire all project locks of a build while those of another build are held by another thread"() {
        def projectLocks = allProjectLocksOf(":", [":a", ":b"])
        def otherBuildProjectLocks = allProjectLocksOf(":other", [":other:a", ":other:b"])

        when:
        async {
            start {
                workerLeaseService.withLocks(otherBuildProjectLocks) {
                    instant.allLocked
                    otherBuildProjectLocks.each { assert lockIsHeld(it) }
                }
            }
            start {
                thread.blockUntil.allLocked
                workerLeaseService.withLocks(projectLocks) {
                    instant.projectLocked
                    projectLocks.each { assert lockIsHeld(it) }
                }
            }
        }

        then:
        assert instant.projectLocked > instant.allLocked
    }

    def "a thread blocked on all project locks of a build acquires none of them until all are free"() {
        def projectLocks = allProjectLocksOf(":", [":a", ":b"])
        def contendedLock = projectLocks[1]

        when:
        async {
            start {
                workerLeaseService.withLocks([contendedLock]) {
                    instant.oneLocked
                    thread.blockUntil.blockingOnAll
                    // The other thread cannot hold :a either, as the locks are acquired all at once or not at all
                    assert !lockIsHeld(projectLocks[0])
                    instant.checked
                }
            }
            start {
                thread.blockUntil.oneLocked
                instant.blockingOnAll
                workerLeaseService.withLocks(projectLocks) {
                    instant.allLocked
                }
            }
        }

        then:
        assert instant.allLocked > instant.checked
    }

    private List<ResourceLock> allProjectLocksOf(String buildIdentityPath, Collection<String> projectIdentityPaths) {
        return projectIdentityPaths.collect { workerLeaseService.getProjectLock(path(buildIdentityPath), path(it)) }
    }

    def "can use runAsIsolatedTask to temporarily release project lock"() {
        boolean executed = false
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))
        def otherProjectLock = workerLeaseService.getProjectLock(path(":"), path(":otherProject"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

        when:
        def buildOperationRunner = new TestBuildOperationRunner()
        def statistics = new DefaultResourceLockStatistics(buildOperationRunner)
        def workerLeaseService = workerLeaseService(true, 1, statistics)
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
        statistics.totalBlockedTime.get() > -1
        (buildOperationRunner.operations.collect {it.displayName} as Set) == ([
            "Acquired [state of project :project]",
            "Blocked on [state of project :project]",
            "Acquired [state of project :project]"
        ] as Set)
    }

    def "fails when attempting to acquire a project lock and changes are disallowed"() {
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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
        def projectLock = workerLeaseService.getProjectLock(path(":"), path(":project"))

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

    def "does not track blocking on worker leases"() {
        def workerLease = workerLeaseService.newWorkerLease()

        when:
        def buildOperationRunner = new TestBuildOperationRunner()
        def statistics = new DefaultResourceLockStatistics(buildOperationRunner)
        def workerLeaseService = workerLeaseService(true, 1, statistics)
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
        statistics.totalBlockedTime.get() == -1
        (buildOperationRunner.operations.collect {it.displayName} as Set) == ([
            "Acquired [worker lease]",
            "Acquired [worker lease]"
        ] as Set)
    }

    boolean lockIsHeld(final ResourceLock resourceLock) {
        MutableBoolean held = new MutableBoolean()
        coordinationService.withStateLock {
            held.set(resourceLock.locked && resourceLock.isLockedByCurrentThread())
            return ResourceLockState.Disposition.FINISHED
        }
        return held.get()
    }
}
