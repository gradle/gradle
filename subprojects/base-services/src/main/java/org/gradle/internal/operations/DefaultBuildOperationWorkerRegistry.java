/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultBuildOperationWorkerRegistry implements BuildOperationWorkerRegistry, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationWorkerRegistry.class);
    private final Semaphore semaphore;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger busyCount = new AtomicInteger();
    private final WaitingForLeaseQueue waitingForLease = new WaitingForLeaseQueue();

    private final ThreadLocal<LinkedList<DefaultOperation>> operationsPerThread = new ThreadLocal<LinkedList<DefaultOperation>>() {
        @Override
        protected LinkedList<DefaultOperation> initialValue() {
            return Lists.newLinkedList();
        }
    };

    private final Root root = new Root();

    public DefaultBuildOperationWorkerRegistry(int maxWorkerCount) {
        this.semaphore = new Semaphore(maxWorkerCount);
        LOGGER.info("Using {} worker leases.", maxWorkerCount);
    }

    @Override
    public Operation getCurrent() {
        LinkedList<DefaultOperation> operations = operationsPerThread.get();
        if (operations.isEmpty()) {
            throw new IllegalStateException("No build operation associated with the current thread");
        }
        return operations.getLast();
    }

    @Override
    public Completion operationStart() {
        LinkedList<DefaultOperation> operations = operationsPerThread.get();
        LeaseHolder parent = operations.isEmpty() ? root : operations.getLast();
        return doStartOperation(parent);
    }

    private BuildOperationWorkerRegistry.Completion doStartOperation(LeaseHolder parent) {

        parent.waitForLease();
        busyCount.incrementAndGet();
        int workerId = counter.incrementAndGet();
        DefaultOperation operation = new DefaultOperation(parent, workerId, Thread.currentThread());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Build operation {} started ({} worker(s) in use).", operation.getDisplayName(), busyCount);
        }
        // this can be done out of locking, because it's for the current thread in any case
        operationsPerThread.get().add(operation);
        return operation;
    }

    @Override
    public void stop() {
        if (busyCount.get() > 0) {
            throw new IllegalStateException("Some build operations have not been marked as completed.");
        }
    }

    private abstract class LeaseHolder {
        abstract String getDisplayName();

        abstract void waitForLease();

        abstract void releaseLease();
    }

    private class Root extends LeaseHolder {
        public String getDisplayName() {
            return "root";
        }

        @Override
        void waitForLease() {
            semaphore.acquireUninterruptibly();
        }

        @Override
        void releaseLease() {
            semaphore.release();
            waitingForLease.wakeUpFirst();
        }
    }

    private class DefaultOperation extends LeaseHolder implements Completion, Operation {
        private final LeaseHolder parent;
        private final int workerId;
        private final Thread ownerThread;
        private volatile int children;
        private final Object lock = new Object();

        DefaultOperation(LeaseHolder parent, int workerId, Thread ownerThread) {
            this.parent = parent;
            this.workerId = workerId;
            this.ownerThread = ownerThread;
        }

        @Override
        String getDisplayName() {
            return parent.getDisplayName() + '.' + workerId;
        }

        @Override
        void waitForLease() {
            while (children != 0 && !semaphore.tryAcquire()) {
                synchronized (lock) {
                    waitingForLease.add(this);
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

            }
            synchronized (lock) {
                children++;
            }
        }

        @Override
        void releaseLease() {
            synchronized (lock) {
                if (--children > 0) {
                    semaphore.release();
                }
            }
            waitingForLease.wakeUpFirst();
        }

        @Override
        public Completion operationStart() {
            return doStartOperation(this);
        }

        @Override
        public void operationFinish() {
            if (Thread.currentThread() != ownerThread) {
                // Not implemented - not yet required. Please implement if required
                throw new UnsupportedOperationException("Must complete operation from owner thread.");
            }

            busyCount.decrementAndGet();
            parent.releaseLease();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Build operation {} completed ({} worker(s) in use)", getDisplayName(), busyCount);
            }

            if (children != 0) {
                throw new IllegalStateException("Some child operations have not yet completed.");
            }

            // doesn't have to be done under lock, since it's all done for the current thread
            popOperation();
        }

        private void popOperation() {
            LinkedList<DefaultOperation> operations = operationsPerThread.get();
            operations.remove(this);
            if (operations.isEmpty()) {
                operationsPerThread.remove();
            }
        }
    }

    private class WaitingForLeaseQueue extends LinkedBlockingQueue<DefaultOperation> {
        public void wakeUpFirst() {
            DefaultOperation operation = waitingForLease.poll();
            if (operation != null) {
                synchronized (operation.lock) {
                    operation.lock.notify();
                }
            }
        }
    }
}
