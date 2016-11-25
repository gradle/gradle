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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultBuildOperationWorkerRegistry implements BuildOperationWorkerRegistry, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationWorkerRegistry.class);
    private final int maxWorkerCount;
    private final Object lock = new Object();
    private int counter = 1;
    private final ListMultimap<Thread, DefaultOperation> threads = ArrayListMultimap.create();
    private final Root root = new Root();

    public DefaultBuildOperationWorkerRegistry(int maxWorkerCount) {
        this.maxWorkerCount = maxWorkerCount;
        LOGGER.info("Using {} worker leases.", maxWorkerCount);
    }

    @Override
    public Operation getCurrent() {
        List<DefaultOperation> operations = threads.get(Thread.currentThread());
        if (operations.isEmpty()) {
            throw new IllegalStateException("No build operation associated with the current thread");
        }
        return operations.get(operations.size() - 1);
    }

    @Override
    public Completion operationStart() {
        List<DefaultOperation> operations = threads.get(Thread.currentThread());
        LeaseHolder parent = operations.isEmpty() ? root : operations.get(operations.size() - 1);
        return doStartOperation(parent);
    }

    private BuildOperationWorkerRegistry.Completion doStartOperation(LeaseHolder parent) {
        synchronized (lock) {
            int workerId = counter++;
            Thread ownerThread = Thread.currentThread();

            DefaultOperation operation = new DefaultOperation(parent, workerId, ownerThread);
            while (!parent.grantLease()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Worker {} waiting for a lease. Currently {} in use", operation.getDisplayName(), root.leasesInUse);
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            threads.put(ownerThread, operation);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Worker {} started ({} in use).", operation.getDisplayName(), root.leasesInUse);
            }
            return operation;
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (!threads.isEmpty()) {
                throw new IllegalStateException("Some build operations have not been marked as completed.");
            }
        }
    }

    private abstract class LeaseHolder {
        abstract String getDisplayName();

        abstract boolean grantLease();

        abstract void releaseLease();
    }

    private class Root extends LeaseHolder {
        int leasesInUse;

        public String getDisplayName() {
            return "root";
        }

        @Override
        boolean grantLease() {
            if (leasesInUse >= maxWorkerCount) {
                return false;
            }
            leasesInUse++;
            return true;
        }

        @Override
        void releaseLease() {
            leasesInUse--;
        }
    }

    private class DefaultOperation extends LeaseHolder implements Completion, Operation {
        private final LeaseHolder parent;
        private final int workerId;
        private final Thread ownerThread;
        int children;

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
        boolean grantLease() {
            if (children == 0 || root.grantLease()) {
                children++;
                return true;
            }
            return false;
        }

        @Override
        void releaseLease() {
            children--;
            if (children > 0) {
                root.releaseLease();
            }
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
            synchronized (lock) {
                parent.releaseLease();
                threads.remove(ownerThread, this);
                lock.notifyAll();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Worker {} completed ({} in use)", getDisplayName(), root.leasesInUse);
                }

                if (children != 0) {
                    throw new IllegalStateException("Some child operations have not yet completed.");
                }
            }
        }
    }
}
