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
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private enum State {
        Working, Finishing, Cancelled, Done
    }

    private final WorkerLeaseService workerLeases;
    private final WorkerLeaseRegistry.WorkerLease parentWorkerLease;
    private final Executor executor;
    private final QueueWorker<T> queueWorker;
    private String logLocation;

    // Lock protects the following state, using an intentionally simple locking strategy
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private final Condition workDone = lock.newCondition();
    private State state = State.Working;
    private int workers;
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
            if (state == State.Done) {
                throw new IllegalStateException("BuildOperationQueue cannot be reused once it has completed.");
            }
            if (state == State.Cancelled) {
                return;
            }
            workQueue.add(operation);
            workAvailable.signalAll();
            if (workers == 0 || workers < workerLeases.getMaxWorkerCount()) {
                // TODO This could be more efficient, so that we only start a worker when there are none idle _and_ there is a worker lease available
                workers++;
                executor.execute(new WorkerRunnable());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancel() {
        lock.lock();
        try {
            if (state == State.Cancelled || state == State.Done) {
                return;
            }
            state = State.Cancelled;
            workQueue.clear();
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
        lock.lock();
        try {
            if (state == State.Done) {
                throw new IllegalStateException("Cannot wait for completion more than once.");
            }
            state = State.Finishing;
            workAvailable.signalAll();
            while (workers > 0) {
                try {
                    workDone.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            state = State.Done;
            if (!failures.isEmpty()) {
                throw new MultipleBuildOperationFailures(getFailureMessage(failures), failures, logLocation);
            }
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

    private class WorkerRunnable implements Runnable {
        @Override
        public void run() {
            T operation;
            while ((operation = waitForNextOperation()) != null) {
                runBatch(operation);
            }
            shutDown();
        }

        private T waitForNextOperation() {
            lock.lock();
            try {
                while (state == State.Working && workQueue.isEmpty()) {
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
            workerLeases.withLocks(parentWorkerLease.createChild()).execute(new Runnable() {
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

        private void addFailure(Throwable failure) {
            lock.lock();
            try {
                failures.add(failure);
            } finally {
                lock.unlock();
            }
        }

        private void shutDown() {
            lock.lock();
            try {
                workers--;
                workDone.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
