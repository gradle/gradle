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
import java.util.function.BooleanSupplier

class DefaultWorkerLeaseServiceTryWhileConditionTest extends AbstractWorkerLeaseServiceTest {

    def "fails when the current thread already holds a worker lease"() {
        def registry = workerLeaseService(1)
        def lease = registry.startWorker()
        def runCount = new AtomicInteger()

        when:
        registry.tryWhileConditionToRunAsWorkerThread(
            { runCount.incrementAndGet() } as Runnable,
            { true } as BooleanSupplier
        )

        then:
        def e = thrown(IllegalStateException)
        e.message == "Current thread already holds a worker lease."
        runCount.get() == 0
        // The pre-existing lease is unaffected.
        registry.isWorkerThread()
        registry.currentWorkerLease == lease

        cleanup:
        lease?.leaseFinish()
        registry?.stop()
    }

    def "acquires a worker lease then runs the action when the current thread is not a worker"() {
        def registry = workerLeaseService(1)
        def runCount = new AtomicInteger()
        def sawWorkerLease = new AtomicBoolean()

        when:
        registry.tryWhileConditionToRunAsWorkerThread({
            sawWorkerLease.set(registry.isWorkerThread())
            runCount.incrementAndGet()
        } as Runnable, { true } as BooleanSupplier)

        then:
        sawWorkerLease.get()
        runCount.get() == 1
        // The temporary lease is released after the action returns.
        !registry.isWorkerThread()

        cleanup:
        registry?.stop()
    }

    def "returns without running the action when the condition becomes false while blocked on lease"() {
        def registry = workerLeaseService(1)
        def holder = registry.startWorker() // Hold the only lease so the spawned thread starves.
        def keepBlocking = new AtomicBoolean(true)
        def runCount = new AtomicInteger()

        when:
        async {
            start {
                registry.tryWhileConditionToRunAsWorkerThread(
                    { runCount.incrementAndGet() } as Runnable,
                    { keepBlocking.get() } as BooleanSupplier
                )
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

    def "releases the acquired lease on exit"() {
        def registry = workerLeaseService(1)

        when:
        registry.tryWhileConditionToRunAsWorkerThread({} as Runnable, { true } as BooleanSupplier)

        then:
        !registry.isWorkerThread()
        // A subsequent worker can acquire the only lease.
        def lease = registry.startWorker()
        registry.currentWorkerLease != null
        lease.leaseFinish()

        cleanup:
        registry?.stop()
    }

    def "propagates exceptions from the action and releases the lease"() {
        def registry = workerLeaseService(1)

        when:
        registry.tryWhileConditionToRunAsWorkerThread(
            { throw new RuntimeException("BOOM") } as Runnable,
            { true } as BooleanSupplier
        )

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
