/*
 * Copyright 2026 the original author or authors.
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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DefaultWorkerLeaseServiceWorkerLoopTest extends AbstractWorkerLeaseServiceTest {

    def "runs the loop directly when the current thread already holds a worker lease"() {
        def registry = workerLeaseService(1)
        def lease = registry.startWorker()
        def runCount = new AtomicInteger()
        def loop = new WorkerLoop() {
            @Override
            boolean shouldContinue() {
                return runCount.get() < 3
            }

            @Override
            void runOnce() {
                assert registry.isWorkerThread()
                assert registry.currentWorkerLease == lease
                runCount.incrementAndGet()
            }
        }

        when:
        registry.runWorkerLoop(loop)

        then:
        runCount.get() == 3
        // The pre-existing lease is unaffected.
        registry.isWorkerThread()

        cleanup:
        lease?.leaseFinish()
        registry?.stop()
    }

    def "acquires a worker lease then runs the loop when the current thread is not a worker"() {
        def registry = workerLeaseService(1)
        def runCount = new AtomicInteger()
        def sawWorkerLease = new AtomicBoolean()
        def loop = new WorkerLoop() {
            @Override
            boolean shouldContinue() {
                return runCount.get() < 1
            }

            @Override
            void runOnce() {
                sawWorkerLease.set(registry.isWorkerThread())
                runCount.incrementAndGet()
            }
        }

        when:
        registry.runWorkerLoop(loop)

        then:
        sawWorkerLease.get()
        runCount.get() == 1
        // The temporary lease is released after the loop exits.
        !registry.isWorkerThread()

        cleanup:
        registry?.stop()
    }

    def "returns without running the loop when shouldContinue becomes false while blocked on lease"() {
        def registry = workerLeaseService(1)
        def holder = registry.startWorker() // Hold the only lease so the spawned thread starves.
        def keepBlocking = new AtomicBoolean(true)
        def runCount = new AtomicInteger()
        def loop = new WorkerLoop() {
            @Override
            boolean shouldContinue() {
                return keepBlocking.get()
            }

            @Override
            void runOnce() {
                runCount.incrementAndGet()
            }
        }

        when:
        async {
            start {
                registry.runWorkerLoop(loop)
                instant.returned
                assert !registry.isWorkerThread()
            }

            // Give the other thread a moment to enter the blocking acquire,
            // then flip the condition.
            thread.block()
            keepBlocking.set(false)
            registry.coordinationService.notifyStateChange()

            thread.blockUntil.returned
        }

        then:
        runCount.get() == 0

        cleanup:
        holder?.leaseFinish()
        registry?.stop()
    }

    def "releases the acquired lease on exit even if shouldContinue is initially false"() {
        def registry = workerLeaseService(1)
        def loop = new WorkerLoop() {
            @Override
            boolean shouldContinue() {
                return false
            }

            @Override
            void runOnce() {
                throw new AssertionError("runOnce should not be called")
            }
        }

        when:
        registry.runWorkerLoop(loop)

        then:
        // The temporary lease is released even when the loop exits immediately.
        !registry.isWorkerThread()
        // A subsequent worker can acquire the only lease.
        def lease = registry.startWorker()
        registry.currentWorkerLease != null
        lease.leaseFinish()

        cleanup:
        registry?.stop()
    }

    def "propagates exceptions from runOnce and releases the lease"() {
        def registry = workerLeaseService(1)
        def loop = new WorkerLoop() {
            @Override
            boolean shouldContinue() {
                return true
            }

            @Override
            void runOnce() {
                throw new RuntimeException("BOOM")
            }
        }

        when:
        registry.runWorkerLoop(loop)

        then:
        def e = thrown(RuntimeException)
        e.message == "BOOM"
        // Lease must have been released so subsequent workers can run.
        !registry.isWorkerThread()
        def lease = registry.startWorker()
        registry.currentWorkerLease != null

        cleanup:
        lease?.leaseFinish()
        registry?.stop()
    }
}
