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

import org.jspecify.annotations.Nullable;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Common code for handling work for {@link WorkerThreadPool} implementations.
 *
 * <p>
 * This class is not thread-safe and must be externally synchronized.
 * </p>
 */
public final class WorkerThreadPoolHelper<T> {
    private final WorkerLimits workerLimits;
    private final Runnable spawnWorker;
    private final AtomicInteger blockedWorkerCount = new AtomicInteger();
    private final Deque<T> queue = new LinkedList<>();
    private int workerCount;

    public WorkerThreadPoolHelper(WorkerLimits workerLimits, Runnable spawnWorker) {
        this.workerLimits = workerLimits;
        this.spawnWorker = spawnWorker;
    }

    public void notifyBlockingWorkStarting() {
        blockedWorkerCount.incrementAndGet();
        // May not be above max workers even after adding 1 if we're at the max unconstrained worker count
        if (!queue.isEmpty() && workerCount < getCurrentMaxWorkerCount()) {
            doSpawnWorker();
        }
    }

    public void notifyBlockingWorkFinished() {
        blockedWorkerCount.decrementAndGet();
    }

    public void submitWork(T work) {
        queue.add(work);

        // TODO This could be more efficient, so that we only start a worker when there are none idle _and_ there is a worker lease available
        if (workerCount < getCurrentMaxWorkerCount()) {
            doSpawnWorker();
        }
    }

    @Nullable
    public T pollWork() {
        return queue.pollFirst();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void clearQueue() {
        queue.clear();
    }

    /**
     * Wait for work to be submitted if the queue is empty and the worker count is under max workers.
     * This attempts to keep up to max workers alive once they've been started.
     */
    public boolean shouldWorkerKeepWaiting() {
        return queue.isEmpty() && workerCount > getCurrentMaxWorkerCount();
    }

    public void notifyWorkerFinished() {
        workerCount--;
    }

    private void doSpawnWorker() {
        spawnWorker.run();
        workerCount++;
    }

    private int getCurrentMaxWorkerCount() {
        int maxWorkersWithCompensation = workerLimits.getMaxWorkerCount() + blockedWorkerCount.get();
        return Math.min(maxWorkersWithCompensation, workerLimits.getMaxUnconstrainedWorkerCount());
    }
}
