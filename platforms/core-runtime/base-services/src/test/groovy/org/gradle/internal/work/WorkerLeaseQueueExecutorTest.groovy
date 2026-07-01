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

import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WorkerLeaseQueueExecutorTest extends AbstractWorkerLeaseServiceTest {

    static class CountingThreadFactory implements ThreadFactory {
        final AtomicInteger threadsCreated = new AtomicInteger()
        final BlockingQueue<Throwable> uncaught = new LinkedBlockingQueue<>()

        @Override
        Thread newThread(Runnable r) {
            def t = new Thread(r, "wlqe-test-" + threadsCreated.incrementAndGet())
            t.daemon = true
            t.uncaughtExceptionHandler = { Thread th, Throwable e -> uncaught.offer(e) } as Thread.UncaughtExceptionHandler
            return t
        }
    }

    WorkerLeaseService registry
    ExecutorService backingExecutor
    WorkerLeaseQueueExecutor leaseExecutor
    CountingThreadFactory threadFactory
    WorkerLeaseRegistry.WorkerLeaseCompletion heldLease

    private WorkerLeaseQueueExecutor createExecutor(int maxWorkers, int maxUnconstrainedWorkers = maxWorkers * 2) {
        registry = workerLeaseService(maxWorkers)
        threadFactory = new CountingThreadFactory()
        backingExecutor = Executors.newCachedThreadPool(threadFactory)
        leaseExecutor = new WorkerLeaseQueueExecutor(coordinationService, registry, backingExecutor, maxWorkers, maxUnconstrainedWorkers)
        return leaseExecutor
    }

    def cleanup() {
        leaseExecutor?.shutdown()
        heldLease?.leaseFinish()
        backingExecutor?.shutdown()
        backingExecutor?.awaitTermination(15, TimeUnit.SECONDS)
        registry?.stop()
    }

    def "createSubmissionQueue throws after shutdown"() {
        given:
        createExecutor(1)
        leaseExecutor.shutdown()

        when:
        leaseExecutor.createSubmissionQueue()

        then:
        thrown(IllegalStateException)
    }

    def "add to a submission queue spawns a worker that runs the task"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        def ran = new CountDownLatch(1)

        when:
        queue.add({ ran.countDown() } as Runnable)

        then:
        ran.await(15, TimeUnit.SECONDS)
    }

    def "peak concurrent task execution does not exceed maxWorkers"() {
        given:
        createExecutor(2)
        def queue = leaseExecutor.createSubmissionQueue()
        def concurrent = new AtomicInteger()
        def peak = new AtomicInteger()
        def done = new CountDownLatch(10)

        when:
        10.times {
            queue.add({
                int c = concurrent.incrementAndGet()
                peak.updateAndGet({ p -> Math.max(p, c) })
                Thread.sleep(50)
                concurrent.decrementAndGet()
                done.countDown()
            } as Runnable)
        }

        then:
        done.await(25, TimeUnit.SECONDS)
        peak.get() <= 2
    }

    def "tryClaimSlot honors maxWorkers under contention"() {
        given:
        createExecutor(2)
        def queue = leaseExecutor.createSubmissionQueue()
        def gate = new CountDownLatch(1)
        def started = new CountDownLatch(2)

        when:
        20.times {
            queue.add({
                started.countDown()
                gate.await()
            } as Runnable)
        }
        // Wait until the two workers are running their gated tasks.
        assert started.await(15, TimeUnit.SECONDS)
        // Brief settle to expose any extra spurious spawn.
        Thread.sleep(100)

        then:
        threadFactory.threadsCreated.get() == 2

        cleanup:
        gate?.countDown()
    }

    def "processWorkUsingCurrentThreadUntilEmptyOr throws on non-worker thread"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        def caught = new AtomicReference<Throwable>()

        when:
        def t = new Thread({
            try {
                queue.processWorkUsingCurrentThreadUntilEmptyOr({ false })
            } catch (Throwable e) {
                caught.set(e)
            }
        })
        t.start()
        t.join(15000)

        then:
        caught.get() instanceof IllegalStateException
        caught.get().message == "Current thread is not a worker thread."
    }

    def "processWorkUsingCurrentThreadUntilEmptyOr drains the queue when called from a worker thread"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        // Hold the only lease so the spawned worker starves and we are forced to drain on main.
        heldLease = registry.startWorker()
        def threadIds = new CopyOnWriteArrayList<Long>()
        def mainId = Thread.currentThread().id

        when:
        5.times {
            queue.add({ threadIds.add(Thread.currentThread().id) } as Runnable)
        }
        queue.processWorkUsingCurrentThreadUntilEmptyOr({ false })

        then:
        threadIds.size() == 5
        threadIds.every { it == mainId }
    }

    def "processWorkUsingCurrentThreadUntilEmptyOr stops when stopping condition returns true"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        heldLease = registry.startWorker()
        def counter = new AtomicInteger()

        when:
        10.times {
            queue.add({ counter.incrementAndGet() } as Runnable)
        }
        queue.processWorkUsingCurrentThreadUntilEmptyOr({ counter.get() >= 3 })

        then:
        counter.get() == 3
    }

    def "processWorkUsingCurrentThreadUntilEmptyOr stops when executor is shut down"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def secondRan = new AtomicBoolean()
        def returned = new CountDownLatch(1)

        queue.add({ started.countDown(); release.await() } as Runnable)
        queue.add({ secondRan.set(true) } as Runnable)

        when:
        def drainThread = new Thread({
            registry.runAsWorkerThread({
                queue.processWorkUsingCurrentThreadUntilEmptyOr({ false })
            } as Runnable)
            returned.countDown()
        })
        drainThread.start()

        assert started.await(15, TimeUnit.SECONDS)
        leaseExecutor.shutdown()
        release.countDown()

        then:
        returned.await(15, TimeUnit.SECONDS)
        !secondRan.get()
    }

    def "spawns a compensation worker when a task blocks via registry.blocking"() {
        // registry.blocking releases the worker lease and calls
        // owningThreadPool.notifyBlockingWorkStarting, which is what the executor's
        // compensation path is wired up to.
        given:
        createExecutor(1, 4)
        def queue = leaseExecutor.createSubmissionQueue()
        def started = new CountDownLatch(2)
        def release = new CountDownLatch(1)

        when:
        queue.add({
            registry.blocking({
                started.countDown()
                release.await()
            } as Runnable)
        } as Runnable)
        queue.add({
            started.countDown()
            release.await()
        } as Runnable)

        then:
        started.await(15, TimeUnit.SECONDS)

        cleanup:
        release?.countDown()
    }

    def "compensation respects maxUnconstrainedWorkers cap"() {
        // With maxWorkers=1, maxUnconstrainedWorkers=2: the first blocking task spawns
        // exactly one compensation worker (slot 2). Further blocking tasks must not push
        // the thread count past 2.
        given:
        createExecutor(1, 2)
        def queue = leaseExecutor.createSubmissionQueue()
        def started = new CountDownLatch(2)
        def release = new CountDownLatch(1)

        when:
        5.times {
            queue.add({
                registry.blocking({
                    started.countDown()
                    release.await()
                } as Runnable)
            } as Runnable)
        }
        // Wait until two of the blocking tasks are running concurrently.
        assert started.await(15, TimeUnit.SECONDS)
        // Brief settle to expose any extra spurious spawn beyond the cap.
        Thread.sleep(100)

        then:
        threadFactory.threadsCreated.get() == 2

        cleanup:
        release?.countDown()
    }

    def "tasks from independent submission queues run concurrently up to maxWorkers"() {
        given:
        createExecutor(2)
        def q1 = leaseExecutor.createSubmissionQueue()
        def q2 = leaseExecutor.createSubmissionQueue()
        // `started` only reaches zero if both tasks run concurrently, since each
        // blocks on `release` after counting down.
        def started = new CountDownLatch(2)
        def release = new CountDownLatch(1)

        when:
        q1.add({ started.countDown(); release.await() } as Runnable)
        q2.add({ started.countDown(); release.await() } as Runnable)

        then:
        started.await(15, TimeUnit.SECONDS)

        cleanup:
        release?.countDown()
    }

    def "worker releases its slot after the loop completes so a fresh add can spawn a new worker"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        def first = new CountDownLatch(1)
        def second = new CountDownLatch(1)

        when:
        queue.add({ first.countDown() } as Runnable)
        assert first.await(15, TimeUnit.SECONDS)
        // Let the worker fully unwind from its loop and release the slot.
        Thread.sleep(100)
        queue.add({ second.countDown() } as Runnable)

        then:
        second.await(15, TimeUnit.SECONDS)
    }

    def "shutdown alone causes a starved worker to exit"() {
        given:
        def workerStartedLoop = new CountDownLatch(1)
        def mainThread = Thread.currentThread()
        registry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(1), ResourceLockStatistics.NO_OP) {
            @Override
            void runWorkerLoop(WorkerLoop loop) {
                if (Thread.currentThread() !== mainThread) {
                    workerStartedLoop.countDown()
                }
                super.runWorkerLoop(loop)
            }
        }
        registry.startProjectExecution(true)
        threadFactory = new CountingThreadFactory()
        backingExecutor = Executors.newCachedThreadPool(threadFactory)
        leaseExecutor = new WorkerLeaseQueueExecutor(coordinationService, registry, backingExecutor, 1, 2)
        heldLease = registry.startWorker()
        def queue = leaseExecutor.createSubmissionQueue()
        def taskRan = new AtomicBoolean()

        when:
        queue.add({ taskRan.set(true) } as Runnable)
        assert workerStartedLoop.await(15, TimeUnit.SECONDS)

        leaseExecutor.shutdown()
        backingExecutor.shutdown()

        then:
        backingExecutor.awaitTermination(15, TimeUnit.SECONDS)
        !taskRan.get()
    }

    def "uncaught throwables during current-thread drain are surfaced via the backing executor"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        heldLease = registry.startWorker()

        when:
        queue.add({ throw new RuntimeException("BOOM-drain") } as Runnable)
        queue.processWorkUsingCurrentThreadUntilEmptyOr({ false })

        then:
        // Drain uncaught before the spec cleanup shuts down the backing executor,
        // otherwise the rethrow-task could race shutdown and be silently rejected.
        def captured = threadFactory.uncaught.poll(5, TimeUnit.SECONDS)
        captured != null
        captured.message == "BOOM-drain"
    }

    def "worker releases its slot when runnable throws"() {
        given:
        createExecutor(1)
        def queue = leaseExecutor.createSubmissionQueue()
        def firstFailed = new CountDownLatch(1)
        def secondRan = new CountDownLatch(1)

        when:
        queue.add({
            try {
                throw new RuntimeException("BOOM-throw")
            } finally {
                firstFailed.countDown()
            }
        } as Runnable)
        assert firstFailed.await(15, TimeUnit.SECONDS)
        // Let the worker unwind so the slot is released before we test reuse.
        Thread.sleep(100)
        queue.add({ secondRan.countDown() } as Runnable)

        then:
        secondRan.await(15, TimeUnit.SECONDS)

        cleanup:
        threadFactory?.uncaught?.poll(1, TimeUnit.SECONDS)
    }

    def "an over-max worker exits via shouldContinue when blocking finishes and effective max shrinks"() {
        given:
        createExecutor(1, 2)
        def queue = leaseExecutor.createSubmissionQueue()
        def aBlockingStarted = new CountDownLatch(1)
        def aRelease = new CountDownLatch(1)
        def bRunning = new CountDownLatch(1)
        def bRelease = new CountDownLatch(1)
        def probesStarted = new AtomicInteger()
        def probeRelease = new CountDownLatch(1)

        when:
        queue.add({
            // Enqueue bTask before blocking so compensation B has work on spawn.
            queue.add({ bRunning.countDown(); bRelease.await() } as Runnable)
            registry.blocking({
                aBlockingStarted.countDown()
                aRelease.await()
            } as Runnable)
        } as Runnable)
        assert aBlockingStarted.await(15, TimeUnit.SECONDS)
        assert bRunning.await(15, TimeUnit.SECONDS)

        // Queue two gated probes, then open the over-max window: the effective max
        // shrinks to 1 while the worker count is 2.
        2.times { queue.add({ probesStarted.incrementAndGet(); probeRelease.await() } as Runnable) }
        aRelease.countDown()
        bRelease.countDown()

        then:
        // Exactly one worker survives and only one probe runs; the other probe
        // stays queued.
        ConcurrentTestUtil.poll(15) {
            assert leaseExecutor.@workerCounter.currentWorkerCount() == 1
            assert probesStarted.get() == 1
        }

        cleanup:
        probeRelease?.countDown()
    }
}
