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

package org.gradle.internal.work;

import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcUnboundedXaddArrayQueue;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processes queues of runnables, rather than just single runnables, always executing them under a worker lease.
 */
public final class WorkerLeaseQueueProcessor implements WorkerThreadPool {
    private static final class SubmissionQueueImpl implements SubmissionQueue {
        private final WorkerLeaseQueueProcessor processor;
        // Only using @Nullable here to convince IntelliJ that queue.poll() can return null
        private final MessagePassingQueue<@Nullable Runnable> queue = new MpmcUnboundedXaddArrayQueue<>(64);

        SubmissionQueueImpl(WorkerLeaseQueueProcessor processor) {
            this.processor = processor;
        }

        @Override
        public void add(Runnable task) {
            if (processor.shutdown.get()) {
                throw new IllegalStateException("Cannot add to a queue owned by a shutdown processor.");
            }
            if (!queue.offer(task)) {
                // Should never happen since the queue is unbounded
                throw new AssertionError("WorkerLeaseQueueProcessor submission queue is full.");
            }
            processor.activeQueues.add(this);
            // We always spawn a worker even if already active, as we want to process a single submission queue in parallel.
            processor.trySpawnWorker();
        }

        @Override
        public void processWorkUsingCurrentThreadUntilEmpty() {
            if (!processor.workerThreadRegistry.isWorkerThread()) {
                throw new IllegalStateException("Current thread is not a worker thread.");
            }
            while (!processor.shutdown.get()) {
                Runnable work = poll();
                if (work == null) {
                    return;
                }

                try {
                    work.run();
                } catch (Throwable t) {
                    // Re-throw inside executor to avoid killing this thread, which we don't manage.
                    // Most task throwables will be handled by FutureTask.
                    processor.backingExecutor.execute(() -> {
                        throw t;
                    });
                }
            }
            // Discard remaining work on shutdown.
            // This is a best-effort attempt to clean up,
            // it doesn't matter too much if we miss some work here as the processor is likely about to be GC'd.
            queue.clear();
            processor.activeQueues.remove(this);
        }

        @Nullable
        Runnable poll() {
            Runnable item = queue.poll();
            if (item == null) {
                if (!processor.activeQueues.remove(this)) {
                    return null;
                }
                // add() offers before the queue is put in the active set, so a concurrent add() may have offered work
                // between our poll and our remove. We need to re-check that there is no work to do, or we will leave
                // a queue with work in it removed from the active set.
                item = queue.poll();
                if (item != null) {
                    processor.activeQueues.add(this);
                }
            }
            return item;
        }
    }

    private final ResourceLockCoordinationService coordinationService;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final ExecutorService backingExecutor;
    private final WorkerCounter workerCounter;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final Set<SubmissionQueueImpl> activeQueues = ConcurrentHashMap.newKeySet();

    public WorkerLeaseQueueProcessor(ResourceLockCoordinationService coordinationService, WorkerThreadRegistry workerThreadRegistry, ExecutorService backingExecutor, int maxWorkers, int maxUnconstrainedWorkers) {
        this.coordinationService = coordinationService;
        this.workerThreadRegistry = workerThreadRegistry;
        this.backingExecutor = backingExecutor;
        this.workerCounter = new WorkerCounter(maxWorkers, maxUnconstrainedWorkers);
    }

    /**
     * Create a new submission queue to this processor. Tasks added to the submission queue will be executed by this processor,
     * but may be executed in parallel with other tasks from the same submission queue. The submission queue may also be
     * drained on its own using {@link SubmissionQueue#processWorkUsingCurrentThreadUntilEmpty()}.
     *
     * <p>The returned submission queue is thread-safe and can be used concurrently from multiple threads.
     *
     * @return a new submission queue
     */
    public SubmissionQueue createSubmissionQueue() {
        if (shutdown.get()) {
            throw new IllegalStateException("Cannot create submission queue for a shutdown processor.");
        }
        return new SubmissionQueueImpl(this);
    }

    private void trySpawnWorker() {
        if (!workerCounter.tryClaimSlot()) {
            // We already have enough workers.
            return;
        }
        backingExecutor.execute(() -> {
            QueueConsumer consumer = new QueueConsumer();
            try {
                workerThreadRegistry.setOwningThreadPool(this);
                try {
                    workerThreadRegistry.runWorkerLoop(consumer);
                } finally {
                    workerThreadRegistry.setOwningThreadPool(null);
                }
            } finally {
                consumer.releaseSlotIfStillOwned();
            }
        });
    }

    @Override
    public void notifyBlockingWorkStarting() {
        workerCounter.notifyBlockingWorkStarting();
        trySpawnWorker();
    }

    @Override
    public void notifyBlockingWorkFinished() {
        workerCounter.notifyBlockingWorkFinished();
    }

    public void shutdown() {
        shutdown.set(true);
        // Wake up workers so they exit.
        coordinationService.notifyStateChange();
    }

    private final class QueueConsumer implements WorkerLoop {
        @Nullable
        private SubmissionQueueImpl currentQueue;
        private boolean locallyFinished;
        private boolean slotReleased;

        /**
         * <p>Side effect: if the worker count has exceeded the effective max (e.g. a blocking
         * worker just unblocked, shrinking the cap), this method atomically releases this
         * worker's slot and returns {@code false} so the loop exits. The CAS in
         * {@link WorkerCounter#tryReleaseExcessSlot()} ensures only the excess workers exit
         * when multiple consumers race the same check.
         */
        @Override
        public boolean shouldContinue() {
            if (locallyFinished || shutdown.get() || slotReleased) {
                return false;
            }
            if (workerCounter.tryReleaseExcessSlot()) {
                slotReleased = true;
                return false;
            }
            return true;
        }

        void releaseSlotIfStillOwned() {
            if (!slotReleased) {
                workerCounter.releaseSlot();
                slotReleased = true;
            }
        }

        @Override
        public void runOnce() {
            Runnable work = pullFromQueues();
            if (work != null) {
                // Most task throwables will be handled by FutureTask.
                // Unexpected throwables are handled by backing executor.
                work.run();
            } else {
                locallyFinished = true;
            }
        }

        @Nullable
        private Runnable pullFromQueues() {
            while (true) {
                if (currentQueue == null) {
                    currentQueue = getFirstActiveQueue();
                    if (currentQueue == null) {
                        // No active queues, so nothing to do.
                        return null;
                    }
                }
                Runnable item = currentQueue.poll();
                if (item != null) {
                    return item;
                }
                // Empty queue, try another one.
                currentQueue = null;
            }
        }

        private @Nullable SubmissionQueueImpl getFirstActiveQueue() {
            Iterator<SubmissionQueueImpl> iterator = activeQueues.iterator();
            return iterator.hasNext() ? iterator.next() : null;
        }

    }
}
