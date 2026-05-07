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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.work.DefaultWorkerLimits;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.internal.work.WorkerThreadPool;
import org.gradle.internal.work.WorkerThreadPoolHelper;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T>, WorkerThreadPool {
    private enum QueueState {
        Working, Finishing, Cancelled, Done
    }

    private final boolean allowAccessToProjectState;
    private final WorkerLeaseService workerLeases;
    private final BuildOperationExecutionContext context;
    private final QueueWorker<T> queueWorker;
    private final @Nullable BuildOperationRef parent;

    private String logLocation;

    // Lock protects the following state, using an intentionally simple locking strategy
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private final Condition operationsComplete = lock.newCondition();
    private QueueState queueState = QueueState.Working;
    private int pendingOperations;
    private final List<Throwable> failures = new ArrayList<>();
    @GuardedBy("lock")
    private final WorkerThreadPoolHelper<T> helper;

    DefaultBuildOperationQueue(
        boolean allowAccessToProjectState,
        WorkerLeaseService workerLeases,
        BuildOperationExecutionContext context,
        QueueWorker<T> queueWorker,
        @Nullable BuildOperationRef parent
    ) {
        this.allowAccessToProjectState = allowAccessToProjectState;
        this.workerLeases = workerLeases;
        this.context = context;
        this.queueWorker = queueWorker;
        this.parent = parent;
        // `context.getMaxConcurrency() - 1` because main thread executes work as well. See https://github.com/gradle/gradle/issues/3273
        int maxWorkerTasks = context.requiresWorkerLease()
            ? Math.max(1, context.getMaxConcurrency() - 1)
            : context.getMaxConcurrency();
        this.helper = new WorkerThreadPoolHelper<>(
            new DefaultWorkerLimits(maxWorkerTasks),
            token -> context.getExecutor().execute(new WorkerRunnable(token, parent))
        );
    }

    @Override
    public void add(final T operation) {
        lock.lock();
        try {
            if (queueState == QueueState.Done) {
                throw new IllegalStateException("BuildOperationQueue cannot be reused once it has completed.");
            }
            if (queueState == QueueState.Cancelled) {
                return;
            }
            helper.submitWork(operation);
            pendingOperations++;
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyBlockingWorkStarting() {
        lock.lock();
        try {
            helper.notifyBlockingWorkStarting();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyBlockingWorkFinished() {
        lock.lock();
        try {
            helper.notifyBlockingWorkFinished();
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancel() {
        lock.lock();
        try {
            if (queueState == QueueState.Cancelled || queueState == QueueState.Done) {
                return;
            }
            queueState = QueueState.Cancelled;
            completeOperations(helper.getQueueSize());
            helper.clearQueue();
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForCompletion() throws MultipleBuildOperationFailures {
        signalNoMoreWork();

        // If our work requires worker leases, use this thread to process any work, as we already
        // have a worker lease. This ensures that all worker leases are being utilized,
        // regardless of the bounds of the thread pool.
        if (context.requiresWorkerLease()) {
            new WorkerRunnable(null, parent).runOperations();
        }

        waitForWorkToComplete();
    }

    private void signalNoMoreWork() {
        lock.lock();
        try {
            if (queueState == QueueState.Done) {
                throw new IllegalStateException("Cannot wait for completion more than once.");
            }
            queueState = QueueState.Finishing;
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void waitForWorkToComplete() {
        lock.lock();
        try {
            if (pendingOperations == 0) {
                // All work is finished, clean up
                markFinished();
                return;
            }
        } finally {
            lock.unlock();
        }

        // Need to wait for work to complete, so release worker lease while waiting
        workerLeases.blocking(() -> {
            lock.lock();
            try {
                // Wait for any work still running in other threads
                while (pendingOperations > 0) {
                    try {
                        operationsComplete.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

                markFinished();
            } finally {
                lock.unlock();
            }
        });
    }

    private void markFinished() {
        queueState = QueueState.Done;
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, logLocation);
        }
    }

    private void addFailure(Throwable failure) {
        lock.lock();
        try {
            failures.add(failure);
        } finally {
            lock.unlock();
        }
    }

    private void completeOperations(int count) {
        lock.lock();
        try {
            pendingOperations = pendingOperations - count;
            operationsComplete.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setLogLocation(String logLocation) {
        this.logLocation = logLocation;
    }

    private class WorkerRunnable implements Runnable {

        private final WorkerThreadPoolHelper.@Nullable WorkerToken token;
        private final @Nullable BuildOperationRef parent;

        public WorkerRunnable(WorkerThreadPoolHelper.@Nullable WorkerToken token, @Nullable BuildOperationRef parent) {
            this.token = token;
            this.parent = parent;
        }

        @Override
        public void run() {
            workerLeases.setOwningThreadPool(DefaultBuildOperationQueue.this);
            try {
                runOperations();
            } finally {
                workerLeases.setOwningThreadPool(null);
            }
        }

        private void runOperations() {
            CurrentBuildOperationRef.instance().with(parent, () -> {
                try {
                    while (waitForNextOperation()) {
                        runBatch();
                    }
                } catch (Throwable t) {
                    addFailure(t);
                } finally {
                    invalidateIfNeeded();
                }
            });
        }

        private boolean waitForNextOperation() {
            lock.lock();
            try {
                // If the token was already invalidated (e.g. in runBatch), exit immediately
                // to avoid becoming a zombie thread stuck in await().
                if (token != null && !token.isValid()) {
                    return false;
                }
                while (queueState == QueueState.Working && helper.isQueueEmpty()) {
                    if (helper.isExtraWorker()) {
                        // We should exit, immediately invalidate our token to ensure the count goes down now.
                        invalidateIfNeeded();
                        return false;
                    }
                    try {
                        workAvailable.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                return !helper.isQueueEmpty();
            } finally {
                lock.unlock();
            }
        }

        private void runBatch() {
            int operationsExecuted;
            if (context.requiresWorkerLease()) {
                operationsExecuted = workerLeases.runAsWorkerThread(this::executePendingWork);
            } else {
                operationsExecuted = executePendingWork();
            }

            // We need to update pending count outside of withLocks() so that we don't have a race
            // condition where the pending count is 0, but a child worker lease is still held when
            // the parent lease is released.
            completeOperations(operationsExecuted);
        }

        private int executePendingWork() {
            if (allowAccessToProjectState) {
                return doRunBatch();
            } else {
                // Disallow this thread from making any changes to the project locks while it is running the work. This implies that this thread will not
                // block waiting for access to some other project, which means it can proceed even if some other thread is waiting for a project lock it
                // holds without causing a deadlock. This in turn implies that this thread does not need to release the project locks it holds while
                // blocking waiting for an operation to complete and does not need to deal with another thread stealing its project lock(s) while blocking.
                //
                // Eventually, this should become the default and only behaviour for all worker threads and changes to locks made only when starting or
                // finishing an execution node. Adding this constraint here means that we can make all build operation queue workers compliant with this
                // constraint and then gradually roll this out to other worker threads, such as task action workers.
                //
                // See {@link ProjectLeaseRegistry#whileDisallowingProjectLockChanges} for more details
                return workerLeases.whileDisallowingProjectLockChanges(this::doRunBatch);
            }
        }

        /**
         * Run as much work as possible until the queue is empty or the queue is cancelled.
         * Then, we return and release the worker lease while we wait for more work to be added to the queue.
         */
        private int doRunBatch() {
            int operationCount = 0;
            while (true) {
                if (queueState == QueueState.Cancelled) {
                    break;
                }

                T operation;
                lock.lock();
                try {
                    if (helper.isExtraWorker()) {
                        // We should exit, immediately invalidate our token to ensure the count goes down now.
                        invalidateIfNeeded();
                        break;
                    }
                    operation = helper.pollWork();
                } finally {
                    lock.unlock();
                }

                if (operation == null) {
                    break;
                }

                runOperation(operation);
                operationCount++;

            }
            return operationCount;
        }

        private void runOperation(T operation) {
            try {
                queueWorker.execute(operation);
            } catch (Throwable t) {
                addFailure(t);
            }
        }

        private void invalidateIfNeeded() {
            lock.lock();
            try {
                if (token != null) {
                    token.invalidateIfNeeded();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
