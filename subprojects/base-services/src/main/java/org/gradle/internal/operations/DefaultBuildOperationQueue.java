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

import com.google.common.collect.Sets;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private enum QueueState {
        Working, Finishing, Cancelled, Done
    }
    private enum WorkerState {
        NOT_STARTED, STARTED
    }

    private final WorkerLeaseService workerLeases;
    private final WorkerLeaseRegistry.WorkerLease parentWorkerLease;
    private final ExecutorService executor;
    private final QueueWorker<T> queueWorker;
    private String logLocation;

    // Lock protects the following state, using an intentionally simple locking strategy
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private QueueState queueState = QueueState.Working;
    private Set<SubmittedWorker> workers = Sets.newConcurrentHashSet();
    private final Deque<T> workQueue = new LinkedList<T>();
    private final LinkedList<Throwable> failures = new LinkedList<Throwable>();

    DefaultBuildOperationQueue(WorkerLeaseService workerLeases, ExecutorService executor, QueueWorker<T> queueWorker) {
        this.workerLeases = workerLeases;
        this.parentWorkerLease = workerLeases.getWorkerLease();
        this.executor = executor;
        this.queueWorker = queueWorker;
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
            workQueue.add(operation);
            workAvailable.signalAll();
            if (workers.size() == 0 || workers.size() < workerLeases.getMaxWorkerCount()) {
                // TODO This could be more efficient, so that we only start a worker when there are none idle _and_ there is a worker lease available
                WorkerRunnable worker = new WorkerRunnable();
                workers.add(new SubmittedWorker(worker, executor.submit(worker)));
            }
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
            workQueue.clear();
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
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

        // Use this thread to process any work - this should only actually process work if none of the
        // submitted workers make it onto the executor thread pool.
        try {
            new WorkerRunnable().run();
        } catch (Throwable t) {
            addFailure(t);
        }

        // Wait for any work still running in other threads
        while (workers.size() > 0) {
            for (SubmittedWorker worker : workers) {
                if (worker.isRunning()) {
                    worker.waitForCompletion();
                } else {
                    worker.cancel();
                }
                workers.remove(worker);
            }
        }

        lock.lock();
        try {
            queueState = QueueState.Done;
            if (!failures.isEmpty()) {
                throw new MultipleBuildOperationFailures(getFailureMessage(failures), failures, logLocation);
            }
        } finally {
            lock.unlock();
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

    @Override
    public void setLogLocation(String logLocation) {
        this.logLocation = logLocation;
    }

    private static String getFailureMessage(Collection<? extends Throwable> failures) {
        if (failures.size() == 1) {
            return "A build operation failed.";
        }
        return "Multiple build operations failed.";
    }

    private class SubmittedWorker {
        private final WorkerRunnable worker;
        private final Future<?> future;

        SubmittedWorker(WorkerRunnable worker, Future<?> future) {
            this.worker = worker;
            this.future = future;
        }

        void cancel() {
            future.cancel(false);
        }

        boolean isRunning() {
            return worker.getWorkerState() == WorkerState.STARTED;
        }

        void waitForCompletion() {
            try {
                future.get();
            } catch (Exception e) {
                addFailure(e);
            }
        }
    }

    private class WorkerRunnable implements Runnable {
        WorkerState workerState = WorkerState.NOT_STARTED;

        @Override
        public void run() {
            workerState = WorkerState.STARTED;
            T operation;
            while ((operation = waitForNextOperation()) != null) {
                runBatch(operation);
            }
        }

        WorkerState getWorkerState() {
            return workerState;
        }

        private T waitForNextOperation() {
            lock.lock();
            try {
                while (queueState == QueueState.Working && workQueue.isEmpty()) {
                    try {
                        workAvailable.await();
                    } catch (InterruptedException e) {
                        throw new UncheckedException(e);
                    }
                }
                return getNextOperation();
            } finally {
                lock.unlock();
            }
        }

        private void runBatch(final T firstOperation) {
            workerLeases.withLocks(Collections.singleton(parentWorkerLease.createChild()), new Runnable() {
                @Override
                public void run() {
                    T operation = firstOperation;
                    while (operation != null) {
                        runOperation(operation);
                        operation = getNextOperation();
                    }
                }
            });
        }

        private T getNextOperation() {
            lock.lock();
            try {
                return workQueue.pollFirst();
            } finally {
                lock.unlock();
            }
        }


        private void runOperation(T operation) {
            try {
                queueWorker.execute(operation);
            } catch (Throwable t) {
                addFailure(t);
            }
        }
    }
}
