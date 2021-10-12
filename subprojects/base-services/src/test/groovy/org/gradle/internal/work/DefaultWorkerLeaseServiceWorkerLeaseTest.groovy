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

import org.gradle.internal.Factory
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock

class DefaultWorkerLeaseServiceWorkerLeaseTest extends ConcurrentSpec {
    ResourceLockCoordinationService coordinationService = new DefaultResourceLockCoordinationService()

    def "worker start run immediately when there are sufficient leases available"() {
        def registry = workerLeaseService(2)

        expect:
        async {
            start {
                def cl = registry.startWorker()
                instant.worker1
                thread.blockUntil.worker2
                cl.leaseFinish()
            }
            start {
                def cl = registry.startWorker()
                instant.worker2
                thread.blockUntil.worker1
                cl.leaseFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "worker start blocks when there are no leases available"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                def cl = registry.startWorker()
                instant.worker1
                thread.block()
                instant.worker1Finished
                cl.leaseFinish()
            }
            start {
                thread.blockUntil.worker1
                def cl = registry.startWorker()
                instant.worker2
                cl.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "can run as worker thread"() {
        def registry = workerLeaseService(1)

        def action = {
            assert registry.workerThread
            def lease = registry.currentWorkerLease
            def nested = registry.runAsWorkerThread({
                assert registry.workerThread
                assert registry.currentWorkerLease == lease
                "result"
            } as Factory)
            assert registry.workerThread
            assert registry.currentWorkerLease == lease
            nested
        } as Factory

        given:
        assert !registry.workerThread

        when:
        registry.currentWorkerLease

        then:
        thrown(NoAvailableWorkerLeaseException)

        when:
        def result = registry.runAsWorkerThread(action)

        then:
        result == "result"

        and:
        !registry.workerThread

        when:
        registry.currentWorkerLease

        then:
        thrown(NoAvailableWorkerLeaseException)

        cleanup:
        registry?.stop()
    }

    def "run as worker starts immediately when there are sufficient leases available"() {
        def registry = workerLeaseService(2)

        expect:
        async {
            start {
                registry.runAsWorkerThread {
                    instant.worker1
                    thread.blockUntil.worker2
                }
            }
            start {
                registry.runAsWorkerThread {
                    instant.worker2
                    thread.blockUntil.worker1
                }
            }
        }

        cleanup:
        registry?.stop()
    }

    def "run as worker blocks when there are no leases available"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                registry.runAsWorkerThread {
                    instant.worker1
                    thread.block()
                    instant.worker1Finished
                }
            }
            start {
                thread.blockUntil.worker1
                registry.runAsWorkerThread {
                    instant.worker2
                }
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "can get lease for current thread"() {
        def registry = workerLeaseService(1)

        given:
        def op = registry.startWorker()

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
        NoAvailableWorkerLeaseException e = thrown()
        e.message == 'No worker lease associated with the current thread'

        when:
        registry.startWorker().leaseFinish()
        registry.currentWorkerLease

        then:
        e = thrown()
        e.message == 'No worker lease associated with the current thread'

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

    def "acquire lease as resource lock blocks when there are no leases available"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                registry.runAsWorkerThread {
                    instant.worker1
                    thread.block()
                    instant.worker1Finished
                }
            }
            start {
                thread.blockUntil.worker1
                registry.withLocks([registry.workerLease]) {
                    instant.worker2
                }
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "can release and acquire worker lease"() {
        def registry = workerLeaseService(1)

        expect:
        registry.runAsWorkerThread {
            def lease = registry.currentWorkerLease
            assert lease != null
            registry.withoutLocks([registry.currentWorkerLease]) {
                assert !registry.workerThread
            }
            assert registry.workerThread
            assert registry.currentWorkerLease == lease
        }

        cleanup:
        registry?.stop()
    }

    def "release and acquire worker lease blocks when there are no worker leases available"() {
        def registry = workerLeaseService(1)

        when:
        async {
            start {
                registry.runAsWorkerThread {
                    instant.worker1
                    registry.withoutLocks([registry.currentWorkerLease]) {
                        thread.blockUntil.worker2
                    }
                    instant.acquired
                }
            }
            start {
                thread.blockUntil.worker1
                registry.runAsWorkerThread {
                    instant.worker2
                    thread.block()
                    instant.worker2Finished
                }
            }
        }

        then:
        instant.acquired > instant.worker2Finished

        cleanup:
        registry?.stop()
    }

    WorkerLeaseService workerLeaseService(int maxWorkers) {
        return new DefaultWorkerLeaseService(coordinationService, new DefaultParallelismConfiguration(true, maxWorkers))
    }
}
