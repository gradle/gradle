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

import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceDeadlockException
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class WorkerLeaseDeadlockDetectionTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService()

    def "detects direct deadlocks between project locks before blocking"() {
        def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 2)
        def lock1 = workerLeaseService.getProjectLock(":root", ":project1")
        def lock2 = workerLeaseService.getProjectLock(":root", ":project2")

        when:
        async {
            workerLeaseService.withLocks(lock1).execute {
                start {
                    workerLeaseService.withLocks(lock2).execute {
                        workerLeaseService.withLocks(lock1).execute { }
                    }
                }

                ConcurrentTestUtil.poll { assert lock2.doIsLocked() }
                workerLeaseService.withLocks(lock2).execute { }
            }
        }

        then:
        def e = thrown(ResourceDeadlockException)
        e.message.startsWith("A dead lock between resource locks has been detected:")
        e.message.readLines().contains("  Test thread 1 is trying to get a lock on :project2 which is held by Test thread 2")
        e.message.readLines().contains("  Test thread 2 is trying to get a lock on :project1 which is held by Test thread 1")
        println e.message
    }

    def "detects indirect deadlocks between project locks before blocking"() {
        def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 1)

        def lock1 = workerLeaseService.getProjectLock(":root", ":project1")
        def lock2 = workerLeaseService.getProjectLock(":root", ":project2")
        def lock3 = workerLeaseService.getProjectLock(":root", ":project3")
        def lock4 = workerLeaseService.getProjectLock(":root", ":project4")

        when:
        async {
            workerLeaseService.withLocks(lock1).execute {
                start {
                    workerLeaseService.withLocks(lock2).execute {
                        workerLeaseService.withLocks(lock1).execute { }
                    }
                }
                start {
                    workerLeaseService.withLocks(lock3).execute {
                        ConcurrentTestUtil.poll { assert lock2.doIsLocked() }
                        workerLeaseService.withLocks(lock2).execute { }
                    }
                }
                start {
                    workerLeaseService.withLocks(lock4).execute {
                        ConcurrentTestUtil.poll { assert lock3.doIsLocked() }
                        workerLeaseService.withLocks(lock3).execute { }
                    }
                }

                ConcurrentTestUtil.poll { assert lock2.doIsLocked() && lock3.doIsLocked() && lock4.doIsLocked() }
                thread.block()
                workerLeaseService.withLocks(lock4).execute { }
            }
        }

        then:
        def e = thrown(ResourceDeadlockException)
        e.message.startsWith("A dead lock between resource locks has been detected:")
        e.message.readLines().contains("  Test thread 1 is trying to get a lock on :project4 which is held by Test thread 4")
        e.message.readLines().contains("  Test thread 4 is trying to get a lock on :project3 which is held by Test thread 3")
        e.message.readLines().contains("  Test thread 3 is trying to get a lock on :project2 which is held by Test thread 2")
        e.message.readLines().contains("  Test thread 2 is trying to get a lock on :project1 which is held by Test thread 1")
        println e.message
    }

    def "detects deadlocks when a project lease is blocking a worker lease"() {
        def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 3)
        def projectLock1 = workerLeaseService.getProjectLock("root", ":project1")
        def projectLock2 = workerLeaseService.getProjectLock("root", ":project2")

        when:
        async {
            def workerLease4 = workerLeaseService.getWorkerLease()
            workerLeaseService.withLocks(projectLock1).execute {
                start {
                    def workerLease1 = workerLeaseService.getWorkerLease()
                    workerLeaseService.withLocks(workerLease1, projectLock2).execute {
                        instant.workerLease1Locked
                        workerLeaseService.withLocks(projectLock1).execute { }
                    }
                }
                start {
                    def workerLease2 = workerLeaseService.getWorkerLease()
                    workerLeaseService.withLocks(workerLease2).execute {
                        ConcurrentTestUtil.poll { assert projectLock2.doIsLocked() }
                        instant.workerLease2Locked
                        workerLeaseService.withLocks(projectLock2).execute { }
                    }
                }
                start {
                    def workerLease3 = workerLeaseService.getWorkerLease()
                    workerLeaseService.withLocks(workerLease3).execute {
                        ConcurrentTestUtil.poll { assert projectLock2.doIsLocked() }
                        instant.workerLease3Locked
                        workerLeaseService.withLocks(projectLock2).execute { }
                    }
                }

                thread.blockUntil.workerLease1Locked
                thread.blockUntil.workerLease2Locked
                thread.blockUntil.workerLease3Locked
                thread.block()

                workerLeaseService.withLocks(workerLease4).execute { }
            }
        }

        then:
        def e = thrown(ResourceDeadlockException)
        e.message.startsWith("A dead lock between resource locks has been detected:")
        e.message.readLines().any { it =~ "  Test thread 1 is trying to get a lock on worker lease which is held by Test thread [234](, Test thread [234]){2}" }
        e.message.readLines().contains("  Test thread 3 is trying to get a lock on :project2 which is held by Test thread 2")
        e.message.readLines().contains("  Test thread 4 is trying to get a lock on :project2 which is held by Test thread 2")
        e.message.readLines().contains("  Test thread 2 is trying to get a lock on :project1 which is held by Test thread 1")
        println e.message
    }

    def "detects deadlocks when a worker lease is blocking a project lease"() {
        def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 1)
        def projectLock1 = workerLeaseService.getProjectLock("root", ":project1")

        when:
        async {
            def workerLease1 = workerLeaseService.getWorkerLease()
            workerLeaseService.withLocks(workerLease1).execute {
                start {
                    def workerLease2 = workerLeaseService.getWorkerLease()
                    workerLeaseService.withLocks(projectLock1).execute {
                        workerLeaseService.withLocks(workerLease2).execute { }
                    }
                }

                ConcurrentTestUtil.poll { assert projectLock1.doIsLocked() }
                thread.block()

                workerLeaseService.withLocks(projectLock1).execute { }
            }
        }

        then:
        def e = thrown(ResourceDeadlockException)
        e.message.startsWith("A dead lock between resource locks has been detected:")
        e.message.readLines().contains("  Test thread 1 is trying to get a lock on :project1 which is held by Test thread 2")
        e.message.readLines().contains("  Test thread 2 is trying to get a lock on worker lease which is held by Test thread 1")
        println e.message
    }
}
