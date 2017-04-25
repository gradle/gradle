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
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private enum State {
        Working, Cancelled, Done
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
    private final Set<OperationHolder> notFinished = new HashSet<OperationHolder>();
    private final LinkedList<OperationHolder> notYetStarted = new LinkedList<OperationHolder>();
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
                // Discard
                return;
            }
            OperationHolder operationHolder = new OperationHolder(parentWorkerLease, operation);
            notFinished.add(operationHolder);
            notYetStarted.add(operationHolder);
            workAvailable.signalAll();
            if (workers == 0 || workers < workerLeases.getMaxWorkerCount()) {
                // This could be more efficient, so that we only start a worker when there are none idle _and_ there is a worker lease available
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
            if (state != State.Working) {
                return;
            }

            // Discard everything that has not been started and notify the workers to finish up
            notFinished.removeAll(notYetStarted);
            notYetStarted.clear();
            state = State.Cancelled;
            workAvailable.signalAll();
            workDone.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
        lock.lock();
        try {
            while (!notFinished.isEmpty()) {
                try {
                    workDone.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (state == State.Done) {
                throw new IllegalStateException("Cannot wait for completion more than once.");
            }
            state = State.Done;
            workAvailable.signalAll();
            // all operations are complete, check for errors
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

    private class OperationHolder implements Runnable {
        private final WorkerLeaseRegistry.WorkerLease parentWorkerLease;
        private final T operation;
        private BuildOperationDescriptor operationDescription;

        OperationHolder(WorkerLeaseRegistry.WorkerLease parentWorkerLease, T operation) {
            this.parentWorkerLease = parentWorkerLease;
            this.operation = operation;
        }

        @Override
        public void run() {
            workerLeases.withLocks(parentWorkerLease.createChild()).execute(new Runnable() {
                @Override
                public void run() {
                    queueWorker.execute(operation);
                }
            });
        }

        @Override
        public String toString() {
            if (operationDescription == null) {
                operationDescription = operation.description().build();
            }
            return "Worker ".concat(queueWorker.getDisplayName()).concat(" for operation ").concat(operationDescription.getDisplayName());
        }
    }

    private class WorkerRunnable implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    OperationHolder operation;
                    lock.lock();
                    try {
                        while (state == State.Working && notYetStarted.isEmpty()) {
                            workAvailable.await();
                        }
                        if (state != State.Working) {
                            // Finish up
                            return;
                        }
                        operation = notYetStarted.removeFirst();
                    } finally {
                        lock.unlock();
                    }

                    Throwable failure = null;
                    try {
                        operation.run();
                    } catch (Throwable t) {
                        failure = t;
                    }

                    lock.lock();
                    try {
                        if (failure != null) {
                            failures.add(failure);
                        }
                        notFinished.remove(operation);
                        if (notFinished.isEmpty()) {
                            workDone.signalAll();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
