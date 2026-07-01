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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class WorkerCounter {
    private static final class WorkerCountState {
        /**
         * Workers currently running. May be above {@code maxWorkers} if there are blocked workers that
         * need compensation, but may not be above {@code maxUnconstrainedWorkers}.
         */
        final int currentWorkerCount;
        /**
         * Workers consumed by blocking work. Needs compensation.
         */
        final int blockedWorkerCount;

        WorkerCountState(int currentWorkerCount, int blockedWorkerCount) {
            this.currentWorkerCount = currentWorkerCount;
            this.blockedWorkerCount = blockedWorkerCount;
        }

        WorkerCountState withNewWorker() {
            return new WorkerCountState(currentWorkerCount + 1, blockedWorkerCount);
        }

        WorkerCountState withReleasedWorker() {
            if (currentWorkerCount == 0) {
                throw new IllegalStateException("Cannot release worker when there are no current workers");
            }
            return new WorkerCountState(currentWorkerCount - 1, blockedWorkerCount);
        }

        WorkerCountState withBlockedWorker() {
            return new WorkerCountState(currentWorkerCount, blockedWorkerCount + 1);
        }

        WorkerCountState withUnblockedWorker() {
            if (blockedWorkerCount == 0) {
                throw new IllegalStateException("Cannot unblock worker when there are no blocked workers");
            }
            return new WorkerCountState(currentWorkerCount, blockedWorkerCount - 1);
        }

        private int getEffectiveMaxWorkers(int maxWorkers, int maxUnconstrainedWorkers) {
            return Math.min(maxWorkers + blockedWorkerCount, maxUnconstrainedWorkers);
        }

        boolean isAtOrAboveEffectiveMaxWorkers(int maxWorkers, int maxUnconstrainedWorkers) {
            return currentWorkerCount >= getEffectiveMaxWorkers(maxWorkers, maxUnconstrainedWorkers);
        }

        boolean isAboveEffectiveMaxWorkers(int maxWorkers, int maxUnconstrainedWorkers) {
            return currentWorkerCount > getEffectiveMaxWorkers(maxWorkers, maxUnconstrainedWorkers);
        }
    }

    private final int maxWorkers;
    private final int maxUnconstrainedWorkers;
    private final AtomicReference<WorkerCountState> workerCountState = new AtomicReference<>(
        new WorkerCountState(0, 0)
    );

    WorkerCounter(int maxWorkers, int maxUnconstrainedWorkers) {
        this.maxWorkers = maxWorkers;
        this.maxUnconstrainedWorkers = maxUnconstrainedWorkers;
    }

    public boolean tryClaimSlot() {
        while (true) {
            WorkerCountState currentState = Objects.requireNonNull(workerCountState.get());
            if (currentState.isAtOrAboveEffectiveMaxWorkers(maxWorkers, maxUnconstrainedWorkers)) {
                return false;
            }
            // Attempt to claim a worker slot. If the state changes, we need to re-check if we're allowed to
            // spawn.
            WorkerCountState newState = currentState.withNewWorker();
            if (workerCountState.compareAndSet(currentState, newState)) {
                return true;
            }
        }
    }

    public void releaseSlot() {
        workerCountState.updateAndGet(WorkerCountState::withReleasedWorker);
    }

    /**
     * If the current worker count is strictly above the effective max (e.g. because a blocking
     * worker just unblocked, shrinking the cap), atomically decrement and return {@code true}.
     * Otherwise, return {@code false} and leave state untouched.
     *
     * @return {@code true} if a slot was released and the caller should stop
     */
    public boolean tryReleaseExcessSlot() {
        while (true) {
            WorkerCountState currentState = Objects.requireNonNull(workerCountState.get());
            if (!currentState.isAboveEffectiveMaxWorkers(maxWorkers, maxUnconstrainedWorkers)) {
                return false;
            }
            WorkerCountState newState = currentState.withReleasedWorker();
            if (workerCountState.compareAndSet(currentState, newState)) {
                return true;
            }
        }
    }

    int currentWorkerCount() {
        return Objects.requireNonNull(workerCountState.get()).currentWorkerCount;
    }

    public void notifyBlockingWorkStarting() {
        workerCountState.updateAndGet(WorkerCountState::withBlockedWorker);
    }

    public void notifyBlockingWorkFinished() {
        workerCountState.updateAndGet(WorkerCountState::withUnblockedWorker);
    }
}
