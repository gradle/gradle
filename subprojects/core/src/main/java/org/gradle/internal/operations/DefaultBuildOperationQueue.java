/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.work.SubmissionQueue;
import org.gradle.internal.work.WorkerLeaseService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private enum QueueStatus {
        WORKING, CANCELED, WAITING_TO_COMPLETE
    }

    private static final class QueueState {
        private final QueueStatus status;
        private final int pendingOperations;
        private final int inFlightOperations;

        private QueueState(QueueStatus status, int pendingOperations, int inFlightOperations) {
            if (pendingOperations < 0) {
                throw new IllegalArgumentException("pendingOperations cannot be negative");
            }
            if (inFlightOperations < 0) {
                throw new IllegalArgumentException("inFlightOperations cannot be negative");
            }
            this.status = status;
            this.pendingOperations = pendingOperations;
            this.inFlightOperations = inFlightOperations;
        }

        QueueState addOperation() {
            return new QueueState(status, pendingOperations + 1, inFlightOperations);
        }

        QueueState startOperation() {
            // Cancellation must short-circuit atomically with the in-flight increment, otherwise
            // waitForCompletion can observe isComplete() == true between cancel() and this op
            // joining the in-flight count, and skip the wait while a failure is still in flight.
            if (status == QueueStatus.CANCELED) {
                return this;
            }
            return new QueueState(status, pendingOperations, inFlightOperations + 1);
        }

        QueueState finishOperation() {
            // cancel() zeroes pendingOperations; skip the decrement in that case.
            int newPending = status == QueueStatus.CANCELED ? pendingOperations : pendingOperations - 1;
            return new QueueState(status, newPending, inFlightOperations - 1);
        }

        QueueState cancelQueue() {
            return new QueueState(QueueStatus.CANCELED, 0, inFlightOperations);
        }

        QueueState waitToComplete() {
            return new QueueState(QueueStatus.WAITING_TO_COMPLETE, pendingOperations, inFlightOperations);
        }

        boolean isComplete() {
            return pendingOperations == 0 && inFlightOperations == 0;
        }
    }

    private final boolean allowAccessToProjectState;
    private final WorkerLeaseService workerLeases;
    private final SubmissionQueue submissionQueue;
    private final QueueWorker<T> queueWorker;
    private final @Nullable BuildOperationRef parent;

    private volatile String logLocation;

    private final CountDownLatch allOperationsComplete = new CountDownLatch(1);
    private final AtomicReference<QueueState> state = new AtomicReference<>(new QueueState(QueueStatus.WORKING, 0, 0));
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    DefaultBuildOperationQueue(
        boolean allowAccessToProjectState,
        WorkerLeaseService workerLeases,
        SubmissionQueue submissionQueue,
        QueueWorker<T> queueWorker,
        @Nullable BuildOperationRef parent
    ) {
        this.allowAccessToProjectState = allowAccessToProjectState;
        this.workerLeases = workerLeases;
        this.submissionQueue = submissionQueue;
        this.queueWorker = queueWorker;
        this.parent = parent;
    }

    @Override
    public void add(T operation) {
        state.updateAndGet(s -> {
            switch (s.status) {
                case WORKING:
                    return s.addOperation();
                case CANCELED:
                    throw new IllegalStateException("BuildOperationQueue cannot be reused once it has cancelled.");
                case WAITING_TO_COMPLETE:
                    throw new IllegalStateException("BuildOperationQueue cannot be reused once it has completed.");
                default:
                    throw new AssertionError("Unknown queue status: " + s.status);
            }
        });
        submissionQueue.add(new OperationRunnable(operation));
    }

    @Override
    public void cancel() {
        QueueState newState = state.updateAndGet(s -> {
            switch (s.status) {
                case WORKING:
                    return s.cancelQueue();
                case CANCELED:
                    return s;
                case WAITING_TO_COMPLETE:
                    throw new IllegalStateException("Cannot cancel a BuildOperationQueue that has already completed.");
                default:
                    throw new AssertionError("Unknown queue status: " + s.status);
            }
        });
        if (newState.isComplete()) {
            allOperationsComplete.countDown();
        }
    }

    @Override
    public void waitForCompletion() throws MultipleBuildOperationFailures {
        QueueState prev = state.getAndUpdate(s -> {
            if (s.status == QueueStatus.WAITING_TO_COMPLETE) {
                throw new IllegalStateException("Cannot wait for completion more than once.");
            }
            if (s.status == QueueStatus.CANCELED) {
                return s;
            }
            return s.waitToComplete();
        });

        if (!prev.isComplete()) {
            // Drain on the current thread while there is queued work to pull.
            if (prev.pendingOperations > 0) {
                submissionQueue.processWorkUsingCurrentThreadUntilEmptyOr(() -> state.get().pendingOperations == 0);
            }

            // Release the worker lease while blocked, but only drop the project lock if the work
            // might need it (allowAccessToProjectState); otherwise hold it to avoid deadlocks when a
            // resource lock is held above (see gradle/gradle#38154).
            if (allowAccessToProjectState) {
                awaitAllOperationsComplete();
            } else {
                workerLeases.whileDisallowingProjectLockChanges(() -> {
                    awaitAllOperationsComplete();
                    return null;
                });
            }
        }

        rethrowFailures();
    }

    private void awaitAllOperationsComplete() {
        workerLeases.blocking(() -> {
            try {
                allOperationsComplete.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    @Override
    public void setLogLocation(String logLocation) {
        this.logLocation = logLocation;
    }

    private void rethrowFailures() {
        List<Throwable> failures = ImmutableList.copyOf(this.failures);
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, logLocation);
        }
    }

    private final class OperationRunnable implements Runnable {
        private final T operation;

        OperationRunnable(T operation) {
            this.operation = operation;
        }

        @Override
        public void run() {
            QueueState currentState = state.updateAndGet(QueueState::startOperation);
            if (currentState.status == QueueStatus.CANCELED) {
                return;
            }
            try {
                CurrentBuildOperationRef.instance().with(parent, () -> {
                    if (allowAccessToProjectState) {
                        runOperation();
                    } else {
                        // Disallow this thread from making any changes to the project locks while it is running the work. This implies that this thread will not
                        // block waiting for access to some other project, which means it can proceed even if some other thread is waiting for a project lock it
                        // holds without causing a deadlock. This in turn implies that this thread does not need to release the project locks it holds while
                        // blocking waiting for an operation to complete and does not need to deal with another thread stealing its project lock(s) while blocking.
                        //
                        // See {@link ProjectLeaseRegistry#whileDisallowingProjectLockChanges} for more details
                        workerLeases.whileDisallowingProjectLockChanges(() -> {
                            runOperation();
                            return null;
                        });
                    }
                });
            } finally {
                QueueState newState = state.updateAndGet(QueueState::finishOperation);
                // In WORKING state more work may still be scheduled, so we're not done yet
                if (newState.isComplete() && newState.status != QueueStatus.WORKING) {
                    allOperationsComplete.countDown();
                }
            }
        }

        private void runOperation() {
            try {
                queueWorker.execute(operation);
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }
}
