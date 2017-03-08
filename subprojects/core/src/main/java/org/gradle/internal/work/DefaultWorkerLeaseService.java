/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultWorkerLeaseService implements WorkerLeaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerLeaseService.class);

    private final int maxWorkerCount;
    private final Object lock = new Object();
    private int counter = 1;
    private final ListMultimap<Thread, DefaultOperation> threads = ArrayListMultimap.create();
    private final Root root = new Root();

    private final boolean parallelEnabled;
    private final ConcurrentMap<String, DefaultProjectLock> projectLocks = Maps.newConcurrentMap();
    private final Multimap<Long, DefaultProjectLock> threadProjectMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.<Long, DefaultProjectLock>create());
    private final ListenerManager listenerManager;
    private final ProjectLockListener projectLockBroadcast;
    private final DefaultProjectLock buildLock = new DefaultProjectLock("BUILD");

    public DefaultWorkerLeaseService(ListenerManager listenerManager, boolean parallelEnabled, int maxWorkerCount) {
        this.maxWorkerCount = maxWorkerCount;
        this.listenerManager = listenerManager;
        this.projectLockBroadcast = listenerManager.getBroadcaster(ProjectLockListener.class);
        this.parallelEnabled = parallelEnabled;
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

    @Override
    public Completion maybeStartOperation() {
        List<DefaultOperation> operations = threads.get(Thread.currentThread());
        if (operations.isEmpty()) {
            return operationStart();
        }
        return new NoOpCompletion();
    }

    private BuildOperationWorkerRegistry.Completion doStartOperation(LeaseHolder parent) {
        synchronized (lock) {
            int workerId = counter++;
            Thread ownerThread = Thread.currentThread();

            DefaultOperation operation = new DefaultOperation(parent, workerId, ownerThread);
            operation.acquireLeaseFromParent();
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

    private void withoutWorkerLease(Runnable runnable) {
        List<DefaultOperation> operations = ImmutableList.copyOf(threads.get(Thread.currentThread()));
        if (!operations.isEmpty()) {
            ListIterator<DefaultOperation> iterator = operations.listIterator(operations.size());
            while(iterator.hasPrevious()) {
                iterator.previous().operationFinish();
            }
            try {
                runnable.run();
            } finally {
                for (DefaultOperation operation : operations) {
                    operation.acquireLeaseFromParent();
                }
            }
        } else {
            runnable.run();
        }
    }

    @Override
    public DefaultProjectLock getProjectLock(String projectPath) {
        if (!parallelEnabled) {
            return buildLock;
        } else {
            projectLocks.putIfAbsent(projectPath, new DefaultProjectLock(projectPath));
            return projectLocks.get(projectPath);
        }
    }

    @Override
    public void addListener(ProjectLockListener projectLockListener) {
        listenerManager.addListener(projectLockListener);
    }

    @Override
    public void removeListener(ProjectLockListener projectLockListener) {
        listenerManager.removeListener(projectLockListener);
    }

    @Override
    public boolean hasProjectLock() {
        Long threadId = Thread.currentThread().getId();
        Collection<DefaultProjectLock> projectLocks = threadProjectMap.get(threadId);
        if (!projectLocks.isEmpty()) {
            for (DefaultProjectLock projectLock : projectLocks) {
                if (projectLock.hasProjectLock()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void withoutProjectLock(Runnable runnable) {
        if (hasProjectLock()) {
            final Long threadId = Thread.currentThread().getId();
            for (DefaultProjectLock projectLock : threadProjectMap.get(threadId)) {
                projectLock.unlock();
            }
            try {
                runnable.run();
            } finally {
                withoutWorkerLease(new Runnable() {
                    @Override
                    public void run() {
                        for (DefaultProjectLock projectLock : threadProjectMap.get(threadId)) {
                            projectLock.lock();
                        }
                    }
                });
            }
        } else {
            runnable.run();
        }
    }

    private class DefaultProjectLock implements ProjectLock {
        private final String projectPath;
        private ReentrantLock lock = new ReentrantLock();

        DefaultProjectLock(String projectPath) {
            this.projectPath = projectPath;
        }

        boolean tryLock() {
            if (!lock.isHeldByCurrentThread()) {
                if (lock.tryLock()) {
                    LOGGER.debug("{}: acquired project lock on {}", Thread.currentThread().getName(), projectPath);
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        void unlock() {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.debug("{}: released project lock on {}", Thread.currentThread().getName(), projectPath);
                projectLockBroadcast.onProjectUnlock(projectPath);
            }
        }

        void lock() {
            if (!lock.isHeldByCurrentThread()) {
                lock.lock();
                LOGGER.debug("{}: acquired project lock on {}", Thread.currentThread().getName(), projectPath);
            }
        }

        @Override
        public boolean hasProjectLock() {
            return lock.isHeldByCurrentThread();
        }

        @Override
        public boolean isLocked() {
            return lock.isLocked();
        }

        @Override
        public boolean tryWithProjectLock(Runnable runnable) {
            Long threadId = Thread.currentThread().getId();
            final DefaultProjectLock thisLock = this;
            if (!hasProjectLock()) {
                if (tryLock()) {
                    try {
                        threadProjectMap.put(threadId, thisLock);
                        runnable.run();
                    } finally {
                        unlock();
                        threadProjectMap.remove(threadId, thisLock);
                    }
                    return true;
                }
            } else {
                runnable.run();
                return true;
            }

            return false;
        }

        @Override
        public void withProjectLock(Runnable runnable) {
            final Long threadId = Thread.currentThread().getId();
            final DefaultProjectLock thisLock = this;
            if (!hasProjectLock()) {
                withoutWorkerLease(new Runnable() {
                    @Override
                    public void run() {
                        lock();
                        threadProjectMap.put(threadId, thisLock);
                    }
                });
                try {
                    runnable.run();
                } finally {
                    unlock();
                    threadProjectMap.remove(threadId, thisLock);
                }
            } else {
                runnable.run();
            }
        }

        @Override
        public String getProjectPath() {
            return projectPath;
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

        void acquireLeaseFromParent() {
            synchronized (lock) {
                while (!parent.grantLease()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Build operation {} waiting for a lease. Currently {} worker(s) in use", getDisplayName(), root.leasesInUse);
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

                threads.put(ownerThread, this);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Build operation {} started ({} worker(s) in use).", getDisplayName(), root.leasesInUse);
                }
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
                    LOGGER.debug("Build operation {} completed ({} worker(s) in use)", getDisplayName(), root.leasesInUse);
                }

                if (children != 0) {
                    throw new IllegalStateException("Some child operations have not yet completed.");
                }
            }
        }
    }

    private static class NoOpCompletion implements Completion {
        @Override
        public void operationFinish() {

        }
    }
}
