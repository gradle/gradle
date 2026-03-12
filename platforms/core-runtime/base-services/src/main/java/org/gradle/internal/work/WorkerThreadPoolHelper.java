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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Common code for handling work for {@link WorkerThreadPool} implementations.
 *
 * <p>
 * This class is not thread-safe and must be externally synchronized.
 * </p>
 */
public final class WorkerThreadPoolHelper<T> {
    public static final class WorkerToken {
        private final WorkerThreadPoolHelper<?> helper;
        private boolean valid = true;

        private WorkerToken(WorkerThreadPoolHelper<?> helper) {
            this.helper = helper;
        }

        public boolean isValid() {
            return valid;
        }

        public void invalidateIfNeeded() {
            if (!valid) {
                return;
            }
            valid = false;
            helper.notifyWorkerFinished();
        }
    }

    private final WorkerLimits workerLimits;
    private final Consumer<WorkerToken> spawnWorker;
    private final Deque<T> queue = new ArrayDeque<>();
    private int workerCount;
    private int blockedWorkerCount;

    public WorkerThreadPoolHelper(WorkerLimits workerLimits, Consumer<WorkerToken> spawnWorker) {
        this.workerLimits = workerLimits;
        this.spawnWorker = spawnWorker;
    }

    public void notifyBlockingWorkStarting() {
        blockedWorkerCount++;
        // May not be above max workers even after adding 1 if we're at the max unconstrained worker count
        if (!queue.isEmpty() && workerCount < getCurrentMaxWorkerCount()) {
            doSpawnWorker();
        }
    }

    public void notifyBlockingWorkFinished() {
        blockedWorkerCount--;
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

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void clearQueue() {
        queue.clear();
    }

    public boolean isExtraWorker() {
        return workerCount > getCurrentMaxWorkerCount();
    }

    private void notifyWorkerFinished() {
        workerCount--;
    }

    private void doSpawnWorker() {
        spawnWorker.accept(new WorkerToken(this));
        workerCount++;
    }

    private int getCurrentMaxWorkerCount() {
        int maxWorkersWithCompensation = workerLimits.getMaxWorkerCount() + blockedWorkerCount;
        return Math.min(maxWorkersWithCompensation, workerLimits.getMaxUnconstrainedWorkerCount());
    }
}
