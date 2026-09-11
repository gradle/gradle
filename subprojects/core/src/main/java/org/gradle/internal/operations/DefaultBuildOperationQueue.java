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

import javax.annotation.concurrent.GuardedBy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private enum QueueStatus {
        WORKING, CANCELED, WAITING_TO_COMPLETE
    }

    private final boolean allowAccessToProjectState;
    private final WorkerLeaseService workerLeases;
    private final SubmissionQueue constrainedQueue;
    private final Executor unconstrainedExecutor;
    private final QueueWorker<T> queueWorker;
    private final @Nullable BuildOperationRef parent;

    private volatile String logLocation;

    private final Lock lock = new ReentrantLock();
    /**
     * Signaled when the waiting thread may have something to do: constrained work was added, or the last outstanding operation finished.
     */
    @GuardedBy("lock")
    private final Condition waiterWorkStateChanged = lock.newCondition();
    @GuardedBy("lock")
    private QueueStatus status = QueueStatus.WORKING;
    /**
     * Operations that have been submitted and have not yet finished.
     */
    @GuardedBy("lock")
    private int outstandingOperations;
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    DefaultBuildOperationQueue(
        boolean allowAccessToProjectState,
        WorkerLeaseService workerLeases,
        SubmissionQueue constrainedQueue,
        Executor unconstrainedExecutor,
        QueueWorker<T> queueWorker,
        @Nullable BuildOperationRef parent
    ) {
        this.allowAccessToProjectState = allowAccessToProjectState;
        this.workerLeases = workerLeases;
        this.constrainedQueue = constrainedQueue;
        this.unconstrainedExecutor = unconstrainedExecutor;
        this.queueWorker = queueWorker;
        this.parent = parent;
    }

    @Override
    public void add(T operation) {
        submit(operation, constrainedQueue::add);
    }

    @Override
    public void addUnconstrained(T operation) {
        submit(operation, unconstrainedExecutor::execute);
    }

    private void submit(T operation, Consumer<Runnable> enqueue) {
        lock.lock();
        try {
            switch (status) {
                case WORKING:
                    break;
                case CANCELED:
                    throw new IllegalStateException("BuildOperationQueue cannot be reused once it has cancelled.");
                case WAITING_TO_COMPLETE:
                    // Only allow additions while a waiting thread is still there to see them.
                    if (outstandingOperations == 0) {
                        throw new IllegalStateException("BuildOperationQueue cannot be reused once it has completed.");
                    }
                    break;
                default:
                    throw new AssertionError("Unknown queue status: " + status);
            }
            outstandingOperations++;
            try {
                enqueue.accept(new OperationRunnable(operation));
            } catch (Throwable t) {
                // Restore the count so that operation tracking is accurate.
                operationFinished();
                throw t;
            }
            waiterWorkStateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void operationFinished() {
        lock.lock();
        try {
            if (outstandingOperations == 0) {
                // This might happen if the queue does accept the runnable but also throws an exception.
                // Just in case, we don't want to go negative and break the waitForCompletion() logic.
                return;
            }
            outstandingOperations--;
            if (outstandingOperations == 0) {
                waiterWorkStateChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isComplete() {
        lock.lock();
        try {
            return outstandingOperations == 0;
        } finally {
            lock.unlock();
        }
    }

    private boolean isCancelled() {
        lock.lock();
        try {
            return status == QueueStatus.CANCELED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancel() {
        lock.lock();
        try {
            switch (status) {
                case WORKING:
                    status = QueueStatus.CANCELED;
                    break;
                case CANCELED:
                    break;
                case WAITING_TO_COMPLETE:
                    throw new IllegalStateException("Cannot cancel a BuildOperationQueue that has already completed.");
                default:
                    throw new AssertionError("Unknown queue status: " + status);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForCompletion() throws MultipleBuildOperationFailures {
        if (!workerLeases.isWorkerThread()) {
            throw new IllegalStateException("waitForCompletion() must be called from a thread that holds a worker lease.");
        }

        lock.lock();
        try {
            if (status == QueueStatus.WAITING_TO_COMPLETE) {
                throw new IllegalStateException("Cannot wait for completion more than once.");
            }
            if (status == QueueStatus.WORKING) {
                status = QueueStatus.WAITING_TO_COMPLETE;
            }
        } finally {
            lock.unlock();
        }

        while (true) {
            // Only the constrained queue is drained: it can stall when every lease is held elsewhere,
            // and this thread has one to lend. See https://github.com/gradle/gradle/issues/37613
            // Unconstrained work cannot stall that way, and running it here would put it back under a lease.
            constrainedQueue.processWorkUsingCurrentThreadUntilEmpty();
            if (isComplete()) {
                break;
            }

            // Release the worker lease while blocked, but only drop the project lock if the work
            // might need it (allowAccessToProjectState); otherwise hold it to avoid deadlocks when a
            // resource lock is held above. See https://github.com/gradle/gradle/issues/38154
            if (allowAccessToProjectState) {
                awaitCompletionOrPendingWork();
            } else {
                workerLeases.whileDisallowingProjectLockChanges(() -> {
                    awaitCompletionOrPendingWork();
                    return null;
                });
            }
        }

        rethrowFailures();
    }

    /**
     * Await either completion of the queue or the presence of pending constrained work which should be run by this thread.
     *
     * <p>
     * Despite the fact that normally most constrained work will have been processed before this method is called,
     * it is possible that running work submits constrained work to this queue. In that case, we need this thread
     * to run that work, otherwise we would run into <a href="https://github.com/gradle/gradle/issues/37613">
     * https://github.com/gradle/gradle/issues/37613</a> again.
     */
    private void awaitCompletionOrPendingWork() {
        workerLeases.blocking(() -> {
            lock.lock();
            try {
                while (outstandingOperations > 0 && constrainedQueue.isEmpty()) {
                    waiterWorkStateChanged.await();
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                lock.unlock();
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
            try {
                // A cancelled queue still has to account for this operation, so skip the work rather than return
                if (!isCancelled()) {
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
                }
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                operationFinished();
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
