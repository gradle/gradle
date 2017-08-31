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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ParallelismConfigurationListener;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.resources.AbstractResourceLockRegistry;
import org.gradle.internal.resources.AbstractTrackedResourceLock;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ExclusiveAccessResourceLock;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.*;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;

public class DefaultWorkerLeaseService implements WorkerLeaseService, ParallelismConfigurationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerLeaseService.class);

    private volatile int maxWorkerCount;
    private int counter = 1;
    private final Root root = new Root();

    private final ResourceLockCoordinationService coordinationService;
    private final ProjectLockRegistry projectLockRegistry;
    private final WorkerLeaseLockRegistry workerLeaseLockRegistry;
    private final ParallelismConfigurationManager parallelismConfigurationManager;

    public DefaultWorkerLeaseService(ResourceLockCoordinationService coordinationService, ParallelismConfigurationManager parallelismConfigurationManager) {
        this.maxWorkerCount = parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount();
        this.coordinationService = coordinationService;
        this.projectLockRegistry = new ProjectLockRegistry(coordinationService, parallelismConfigurationManager.getParallelismConfiguration().isParallelProjectExecutionEnabled());
        this.workerLeaseLockRegistry = new WorkerLeaseLockRegistry(coordinationService);
        this.parallelismConfigurationManager = parallelismConfigurationManager;
        parallelismConfigurationManager.addListener(this);
        LOGGER.info("Using {} worker leases.", maxWorkerCount);
    }

    @Override
    public void onParallelismConfigurationChange(ParallelismConfiguration parallelismConfiguration) {
        this.maxWorkerCount = parallelismConfiguration.getMaxWorkerCount();
        projectLockRegistry.setParallelEnabled(parallelismConfiguration.isParallelProjectExecutionEnabled());
    }

    @Override
    public int getMaxWorkerCount() {
        return maxWorkerCount;
    }

    @Override
    public WorkerLease getCurrentWorkerLease() {
        Collection<? extends ResourceLock> operations = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        if (operations.isEmpty()) {
            throw new NoAvailableWorkerLeaseException("No worker lease associated with the current thread");
        }
        return (DefaultWorkerLease) operations.toArray()[operations.size() - 1];
    }

    private synchronized DefaultWorkerLease getWorkerLease(LeaseHolder parent) {
        int workerId = counter++;
        Thread ownerThread = Thread.currentThread();
        return workerLeaseLockRegistry.getResourceLock(parent, workerId, ownerThread);
    }

    @Override
    public DefaultWorkerLease getWorkerLease() {
        Collection<? extends ResourceLock> operations = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        LeaseHolder parent = operations.isEmpty() ? root : (DefaultWorkerLease) operations.toArray()[operations.size() - 1];
        return getWorkerLease(parent);
    }

    @Override
    public void withSharedLease(WorkerLease sharedLease, Runnable action) {
        workerLeaseLockRegistry.associateResourceLock(sharedLease);
        try {
            action.run();
        } finally {
            workerLeaseLockRegistry.unassociatResourceLock(sharedLease);
            coordinationService.notifyStateChange();
        }
    }

    @Override
    public void stop() {
        parallelismConfigurationManager.removeListener(this);
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (workerLeaseLockRegistry.hasOpenLocks()) {
                    throw new IllegalStateException("Some worker leases have not been marked as completed.");
                }
                if (projectLockRegistry.hasOpenLocks()) {
                    throw new IllegalStateException("Some project locks have not been unlocked.");
                }
                return FINISHED;
            }
        });
    }

    @Override
    public ResourceLock getProjectLock(String gradlePath, String projectPath) {
        return projectLockRegistry.getResourceLock(gradlePath, projectPath);
    }

    @Override
    public <T> T withoutProjectLock(Callable<T> action) {
        final Iterable<? extends ResourceLock> projectLocks = projectLockRegistry.getResourceLocksByCurrentThread();
        return withoutLocks(projectLocks, action);
    }

    @Override
    public void withoutProjectLock(Runnable action) {
        final Iterable<? extends ResourceLock> projectLocks = projectLockRegistry.getResourceLocksByCurrentThread();
        withoutLocks(projectLocks, action);
    }

    @Override
    public <T> T withLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        coordinationService.withStateLock(lock(locks));
        try {
            return action.call();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            coordinationService.withStateLock(unlock(locks));
        }
    }

    @Override
    public void withLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        coordinationService.withStateLock(lock(locks));
        try {
            action.run();
        } finally {
            coordinationService.withStateLock(unlock(locks));
        }
    }

    @Override
    public <T> T withoutLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        if (!allLockedByCurrentThread(locks)) {
            throw new IllegalStateException("Not all of the locks specified are currently held by the current thread.  This could lead to orphaned locks.");
        }

        coordinationService.withStateLock(unlock(locks));
        try {
            return action.call();
        } catch (Exception e) {
            throw new UncheckedException(e);
        } finally {
            if (!coordinationService.withStateLock(tryLock(locks))) {
                releaseWorkerLeaseAndWaitFor(locks);
            }
        }
    }

    @Override
    public void withoutLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        if (!allLockedByCurrentThread(locks)) {
            throw new IllegalStateException("Not all of the locks specified are currently held by the current thread.  This could lead to orphaned locks.");
        }

        coordinationService.withStateLock(unlock(locks));
        try {
            action.run();
        } finally {
            if (!coordinationService.withStateLock(tryLock(locks))) {
                releaseWorkerLeaseAndWaitFor(locks);
            }
        }
    }

    private void releaseWorkerLeaseAndWaitFor(Iterable<? extends ResourceLock> locks) {
        WorkerLease workerLease = getCurrentWorkerLease();
        List<ResourceLock> allLocks = Lists.newArrayList();
        allLocks.add(workerLease);
        Iterables.addAll(allLocks, locks);
        coordinationService.withStateLock(unlock(workerLease));
        coordinationService.withStateLock(lock(allLocks));
    }

    private boolean allLockedByCurrentThread(final Iterable<? extends ResourceLock> locks) {
        final AtomicBoolean allLocked = new AtomicBoolean();
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                allLocked.set(CollectionUtils.every(locks, new Spec<ResourceLock>() {
                    @Override
                    public boolean isSatisfiedBy(ResourceLock lock) {
                        return lock.isLockedByCurrentThread();
                    }
                }));
                return FINISHED;
            }
        });
        return allLocked.get();
    }

    private static class ProjectLockRegistry extends AbstractResourceLockRegistry<ExclusiveAccessResourceLock> {
        private volatile boolean parallelEnabled;

        ProjectLockRegistry(ResourceLockCoordinationService coordinationService, boolean parallelEnabled) {
            super(coordinationService);
            this.parallelEnabled = parallelEnabled;
        }

        void setParallelEnabled(boolean parallelEnabled) {
            this.parallelEnabled = parallelEnabled;
        }

        ResourceLock getResourceLock(String gradlePath, String projectPath) {
            String displayName = projectPath;
            if (!parallelEnabled) {
                displayName = gradlePath;
            }

            return getOrRegisterResourceLock(displayName, new ResourceLockProducer<ExclusiveAccessResourceLock>() {
                @Override
                public ExclusiveAccessResourceLock create(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
                    return new ExclusiveAccessResourceLock(displayName, coordinationService, lockAction, unlockAction);
                }
            });
        }
    }

    private class WorkerLeaseLockRegistry extends AbstractResourceLockRegistry<DefaultWorkerLease> {
        WorkerLeaseLockRegistry(ResourceLockCoordinationService coordinationService) {
            super(coordinationService);
        }

        DefaultWorkerLease getResourceLock(final LeaseHolder parent, int workerId, final Thread ownerThread) {
            String displayName = parent.getDisplayName() + '.' + workerId;
            return getOrRegisterResourceLock(displayName, new ResourceLockProducer<DefaultWorkerLease>() {
                @Override
                public DefaultWorkerLease create(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
                    return new DefaultWorkerLease(displayName, coordinationService, lockAction, unlockAction, parent, ownerThread);
                }
            });
        }
    }

    private interface LeaseHolder extends Describable {
        boolean grantLease();

        void releaseLease();
    }

    private class Root implements LeaseHolder {
        int leasesInUse;

        public String getDisplayName() {
            return "root";
        }

        @Override
        public boolean grantLease() {
            if (leasesInUse >= maxWorkerCount) {
                return false;
            }
            leasesInUse++;
            return true;
        }

        @Override
        public void releaseLease() {
            leasesInUse--;
        }
    }

    private class DefaultWorkerLease extends AbstractTrackedResourceLock implements LeaseHolder, WorkerLeaseCompletion, WorkerLease {
        private final LeaseHolder parent;
        private final Thread ownerThread;
        int children;
        boolean active;

        public DefaultWorkerLease(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction, LeaseHolder parent, Thread ownerThread) {
            super(displayName, coordinationService, lockAction, unlockAction);
            this.parent = parent;
            this.ownerThread = ownerThread;
        }

        @Override
        protected boolean doIsLocked() {
            return active;
        }

        @Override
        protected boolean doIsLockedByCurrentThread() {
            return active && Thread.currentThread() == ownerThread;
        }

        @Override
        protected boolean acquireLock() {
            if (parent.grantLease()) {
                active = true;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Worker lease {} started ({} worker(s) in use).", getDisplayName(), root.leasesInUse);
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Build operation {} could not be started ({} worker(s) in use).", getDisplayName(), root.leasesInUse);
                }
            }
            return active;
        }

        @Override
        protected void releaseLock() {
            if (Thread.currentThread() != ownerThread) {
                // Not implemented - not yet required. Please implement if required
                throw new UnsupportedOperationException("Must complete operation from owner thread.");
            }
            parent.releaseLease();
            active = false;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Worker lease {} completed ({} worker(s) in use)", getDisplayName(), root.leasesInUse);
            }
            if (children != 0) {
                throw new IllegalStateException("Some child operations have not yet completed.");
            }
        }

        @Override
        public boolean grantLease() {
            if (children == 0 || root.grantLease()) {
                children++;
                return true;
            }
            return false;
        }

        @Override
        public void releaseLease() {
            children--;
            if (children > 0) {
                root.releaseLease();
            }
        }

        WorkerLeaseCompletion start() {
            coordinationService.withStateLock(lock(this));
            return this;
        }

        @Override
        public WorkerLease createChild() {
            return getWorkerLease(this);
        }

        @Override
        public WorkerLeaseCompletion startChild() {
            return getWorkerLease(this).start();
        }

        @Override
        public void leaseFinish() {
            coordinationService.withStateLock(DefaultResourceLockCoordinationService.unlock(this));
        }
    }
}
