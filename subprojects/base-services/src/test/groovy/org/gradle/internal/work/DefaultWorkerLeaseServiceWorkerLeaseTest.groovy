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

import org.gradle.internal.concurrent.ParallelismConfigurationManagerFixture
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.*

class DefaultWorkerLeaseServiceWorkerLeaseTest extends ConcurrentSpec {
    ResourceLockCoordinationService coordinationService = new DefaultResourceLockCoordinationService()

    def "operation starts immediately when there are sufficient leases available"() {
        def registry = workerLeaseService(2)

        expect:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                instant.worker1
                thread.blockUntil.worker2
                cl.leaseFinish()
            }
            start {
                def cl = registry.getWorkerLease().start()
                instant.worker2
                thread.blockUntil.worker1
                cl.leaseFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "operation start blocks when there are no leases available"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                instant.worker1
                thread.block()
                instant.worker1Finished
                cl.leaseFinish()
            }
            start {
                thread.blockUntil.worker1
                def cl = registry.getWorkerLease().start()
                instant.worker2
                cl.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "child operation starts immediately when there are sufficient leases available"() {
        def registry = workerLeaseService(1)

        expect:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    def child = op.startChild()
                    child.leaseFinish()
                    instant.childFinished
                }
                thread.blockUntil.childFinished
                cl.leaseFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "child operation borrows parent lease"() {
        def registry = workerLeaseService(1)

        expect:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    def child = op.startChild()
                    child.leaseFinish()
                    instant.child1Finished
                }
                thread.blockUntil.child1Finished
                start {
                    def child = op.startChild()
                    child.leaseFinish()
                    instant.child2Finished
                }
                thread.blockUntil.child2Finished
                cl.leaseFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "child operations block until lease available when there is more than one child"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    def child = op.startChild()
                    instant.child1Started
                    thread.block()
                    instant.child1Finished
                    child.leaseFinish()
                }
                start {
                    thread.blockUntil.child1Started
                    def child = op.startChild()
                    instant.child2Started
                    child.leaseFinish()
                    instant.child2Finished
                }
                thread.blockUntil.child2Finished
                cl.leaseFinish()
            }
        }

        then:
        instant.child2Started > instant.child1Finished

        cleanup:
        registry?.stop()
    }


    def "action with shared lease borrows parent lease"() {
        def registry = workerLeaseService(1)

        expect:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    registry.withSharedLease(op) {
                        assert registry.currentWorkerLease == op
                        def child = registry.currentWorkerLease.startChild()
                        child.leaseFinish()
                        instant.child1Finished
                    }
                }
                thread.blockUntil.child1Finished
                cl.leaseFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "action with shared lease block until lease available when there is more than one child"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    registry.withSharedLease(op) {
                        assert registry.currentWorkerLease == op
                        def child = registry.currentWorkerLease.startChild()
                        instant.child1Started
                        thread.block()
                        instant.child1Finished
                        child.leaseFinish()
                    }
                }
                start {
                    registry.withSharedLease(op) {
                        assert registry.currentWorkerLease == op
                        thread.blockUntil.child1Started
                        def child = registry.currentWorkerLease.startChild()
                        instant.child2Started
                        child.leaseFinish()
                        instant.child2Finished
                    }
                }
                thread.blockUntil.child2Finished
                cl.leaseFinish()
            }
        }

        then:
        instant.child2Started > instant.child1Finished

        cleanup:
        registry?.stop()
    }

    def "fails when child operation completes after parent"() {
        def registry = workerLeaseService(2)

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                def op = registry.currentWorkerLease
                start {
                    def child = op.startChild()
                    instant.childStarted
                    thread.blockUntil.parentFinished
                    child.leaseFinish()
                }
                thread.blockUntil.childStarted
                try {
                    cl.leaseFinish()
                } finally {
                    instant.parentFinished
                }
            }
        }

        then:
        def e = thrown IllegalStateException
        e.message == 'Some child operations have not yet completed.'

        cleanup:
        registry?.stop()
    }

    def "can get operation for current thread"() {
        def registry = workerLeaseService(1)

        given:
        def op = registry.getWorkerLease().start()

        expect:
        registry.currentWorkerLease == op

        cleanup:
        op?.leaseFinish()
        registry?.stop()
    }

    def "cannot get current operation when current thread has no operation"() {
        def registry = workerLeaseService(1)

        when:
        registry.currentWorkerLease

        then:
        def e = thrown NoAvailableWorkerLeaseException
        e.message == 'No worker lease associated with the current thread'

        when:
        registry.getWorkerLease().start().leaseFinish()
        registry.currentWorkerLease

        then:
        e = thrown NoAvailableWorkerLeaseException
        e.message == 'No worker lease associated with the current thread'

        cleanup:
        registry?.stop()
    }

    def "synchronous child operation borrows parent lease"() {
        def registry = workerLeaseService(1)

        expect:
        def outer = registry.getWorkerLease().start()
        def inner = registry.currentWorkerLease.startChild()
        inner.leaseFinish()
        outer.leaseFinish()

        cleanup:
        registry?.stop()
    }

    def "fails when synchronous child operation completes after parent"() {
        def registry = workerLeaseService(1)

        when:
        def outer = registry.getWorkerLease().start()
        def inner = registry.currentWorkerLease.startChild()
        try {
            outer.leaseFinish()
        } finally {
            inner.leaseFinish()
        }

        then:
        def e = thrown IllegalStateException
        e.message == 'Some child operations have not yet completed.'

        cleanup:
        registry?.stop()
    }

    def "can use worker lease as resource lock"() {
        def registry = workerLeaseService(1)

        when:
        def workerLease = registry.getWorkerLease()
        coordinationService.withStateLock(lock(workerLease))

        then:
        noExceptionThrown()
    }

    def "can use child lease as resource lock"() {
        def registry = workerLeaseService(1)

        when:
        def workerLease = registry.getWorkerLease()
        coordinationService.withStateLock(lock(workerLease))
        def childLease = workerLease.createChild()
        coordinationService.withStateLock(lock(childLease))

        then:
        noExceptionThrown()

        when:
        coordinationService.withStateLock(unlock(childLease))
        coordinationService.withStateLock(unlock(workerLease))

        then:
        noExceptionThrown()
    }

    WorkerLeaseService workerLeaseService(int maxWorkers) {
        return new DefaultWorkerLeaseService(coordinationService, new ParallelismConfigurationManagerFixture(true, maxWorkers))
    }
}
