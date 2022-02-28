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
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.resources.AbstractResourceLockRegistry;
import org.gradle.internal.resources.DefaultLease;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.LeaseHolder;
import org.gradle.internal.resources.ProjectLock;
import org.gradle.internal.resources.ProjectLockRegistry;
import org.gradle.internal.resources.ProjectLockStatistics;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockContainer;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.resources.TaskExecutionLockRegistry;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock;
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock;
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;

public class DefaultWorkerLeaseService implements WorkerLeaseService, Stoppable {
    public static final String PROJECT_LOCK_STATS_PROPERTY = "org.gradle.internal.project.lock.stats";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerLeaseService.class);

    private final int maxWorkerCount;
    private final ResourceLockCoordinationService coordinationService;
    private final TaskExecutionLockRegistry taskLockRegistry;
    private final ProjectLockRegistry projectLockRegistry;
    private final WorkerLeaseLockRegistry workerLeaseLockRegistry;
    private final ProjectLockStatisticsImpl projectLockStatistics = new ProjectLockStatisticsImpl();

    public DefaultWorkerLeaseService(ResourceLockCoordinationService coordinationService, ParallelismConfiguration parallelismConfiguration) {
        this.maxWorkerCount = parallelismConfiguration.getMaxWorkerCount();
        this.coordinationService = coordinationService;
        this.projectLockRegistry = new ProjectLockRegistry(coordinationService, parallelismConfiguration.isParallelProjectExecutionEnabled());
        this.taskLockRegistry = new TaskExecutionLockRegistry(coordinationService, projectLockRegistry);
        this.workerLeaseLockRegistry = new WorkerLeaseLockRegistry(coordinationService);
        LOGGER.info("Using {} worker leases.", maxWorkerCount);
    }

    @Override
    public int getMaxWorkerCount() {
        return maxWorkerCount;
    }

    @Override
    public WorkerLease getCurrentWorkerLease() {
        List<? extends WorkerLease> operations = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        if (operations.isEmpty()) {
            throw new NoAvailableWorkerLeaseException("No worker lease associated with the current thread");
        }
        if (operations.size() != 1) {
            throw new IllegalStateException("Expected the current thread of hold a single worker lease");
        }
        return operations.get(0);
    }

    @Override
    public DefaultWorkerLease getWorkerLease() {
        return workerLeaseLockRegistry.getResourceLock();
    }

    @Override
    public boolean isWorkerThread() {
        return workerLeaseLockRegistry.holdsLock();
    }

    @Override
    public <T> T runAsWorkerThread(Factory<T> action) {
        Collection<? extends ResourceLock> locks = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        if (!locks.isEmpty()) {
            // Already a worker
            return action.create();
        }
        return withLocks(Collections.singletonList(getWorkerLease()), action);
    }

    @Override
    public void runAsWorkerThread(Runnable action) {
        runAsWorkerThread(Factories.<Void>toFactory(action));
    }

    @Override
    public Synchronizer newResource() {
        return new DefaultSynchronizer(this);
    }

    @Override
    public void stop() {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (workerLeaseLockRegistry.hasOpenLocks()) {
                    throw new IllegalStateException("Some worker leases have not been marked as completed.");
                }
                if (projectLockRegistry.hasOpenLocks()) {
                    throw new IllegalStateException("Some project locks have not been unlocked.");
                }
                if (taskLockRegistry.hasOpenLocks()) {
                    throw new IllegalStateException("Some task execution locks have not been unlocked.");
                }
                return FINISHED;
            }
        });

        if (projectLockStatistics.isEnabled()) {
            LOGGER.warn("Time spent waiting on project locks: " + projectLockStatistics.getTotalWaitTimeMillis() + "ms");
        }
    }

    @Override
    public boolean getAllowsParallelExecution() {
        return projectLockRegistry.getAllowsParallelExecution();
    }

    @Override
    public ResourceLock getAllProjectsLock(Path buildIdentityPath) {
        return projectLockRegistry.getAllProjectsLock(buildIdentityPath);
    }

    @Override
    public ResourceLock getProjectLock(Path buildIdentityPath, Path projectIdentityPath) {
        return projectLockRegistry.getProjectLock(buildIdentityPath, projectIdentityPath);
    }

    @Override
    public ResourceLock getTaskExecutionLock(Path buildIdentityPath, Path projectIdentityPath) {
        return taskLockRegistry.getTaskExecutionLock(buildIdentityPath, projectIdentityPath);
    }

    @Override
    public Collection<? extends ResourceLock> getCurrentProjectLocks() {
        return projectLockRegistry.getResourceLocksByCurrentThread();
    }

    @Override
    public void runAsIsolatedTask() {
        Collection<? extends ResourceLock> projectLocks = getCurrentProjectLocks();
        releaseLocks(projectLocks);
        releaseLocks(taskLockRegistry.getResourceLocksByCurrentThread());
    }

    @Override
    public void runAsIsolatedTask(Runnable runnable) {
        runAsIsolatedTask(Factories.toFactory(runnable));
    }

    @Override
    public <T> T runAsIsolatedTask(Factory<T> factory) {
        Collection<? extends ResourceLock> projectLocks = getCurrentProjectLocks();
        Collection<? extends ResourceLock> taskLocks = taskLockRegistry.getResourceLocksByCurrentThread();
        List<ResourceLock> locks = new ArrayList<ResourceLock>(projectLocks.size() + taskLocks.size());
        locks.addAll(projectLocks);
        locks.addAll(taskLocks);
        return withoutLocks(locks, factory);
    }

    @Override
    public void blocking(Runnable action) {
        if (projectLockRegistry.mayAttemptToChangeLocks()) {
            final Collection<? extends ResourceLock> projectLocks = getCurrentProjectLocks();
            if (!projectLocks.isEmpty()) {
                // Need to run the action without the project locks and the worker lease
                List<ResourceLock> locks = new ArrayList<ResourceLock>(projectLocks.size() + 1);
                locks.addAll(projectLocks);
                locks.add(getCurrentWorkerLease());
                releaseLocks(locks);
                try {
                    action.run();
                    return;
                } finally {
                    acquireLocks(locks);
                }
            }
        }
        // Else, release only the worker lease
        List<? extends ResourceLock> locks = Collections.singletonList(getCurrentWorkerLease());
        releaseLocks(locks);
        try {
            action.run();
        } finally {
            acquireLocks(locks);
        }
    }

    @Override
    public <T> T whileDisallowingProjectLockChanges(Factory<T> action) {
        return projectLockRegistry.whileDisallowingLockChanges(action);
    }

    @Override
    public <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory) {
        return projectLockRegistry.allowUncontrolledAccessToAnyResource(factory);
    }

    @Override
    public boolean isAllowedUncontrolledAccessToAnyProject() {
        return projectLockRegistry.isAllowedUncontrolledAccessToAnyResource();
    }

    @Override
    public void withLocks(Iterable<? extends ResourceLock> locks, Runnable runnable) {
        withLocks(locks, Factories.toFactory(runnable));
    }

    @Override
    public <T> T withLocks(Iterable<? extends ResourceLock> locks, Factory<T> factory) {
        Iterable<? extends ResourceLock> locksToAcquire = locksNotHeld(locks);

        if (Iterables.isEmpty(locksToAcquire)) {
            return factory.create();
        }

        acquireLocks(locksToAcquire);
        try {
            return factory.create();
        } finally {
            releaseLocks(locksToAcquire);
        }
    }

    private void releaseLocks(Iterable<? extends ResourceLock> locks) {
        coordinationService.withStateLock(unlock(locks));
    }

    private void acquireLocks(final Iterable<? extends ResourceLock> locks) {
        if (containsProjectLocks(locks)) {
            projectLockStatistics.measure(new Runnable() {
                @Override
                public void run() {
                    coordinationService.withStateLock(lock(locks));
                }
            });
        } else {
            coordinationService.withStateLock(lock(locks));
        }
    }

    private boolean containsProjectLocks(Iterable<? extends ResourceLock> locks) {
        for (ResourceLock lock : locks) {
            if (lock instanceof ProjectLock) {
                return true;
            }
        }
        return false;
    }

    private Iterable<? extends ResourceLock> locksNotHeld(final Iterable<? extends ResourceLock> locks) {
        if (Iterables.isEmpty(locks)) {
            return locks;
        }

        final List<ResourceLock> locksNotHeld = Lists.newArrayList(locks);
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                Iterator<ResourceLock> iterator = locksNotHeld.iterator();
                while (iterator.hasNext()) {
                    ResourceLock lock = iterator.next();
                    if (lock.isLockedByCurrentThread()) {
                        iterator.remove();
                    }
                }
                return FINISHED;
            }
        });
        return locksNotHeld;
    }

    @Override
    public void withoutLocks(Iterable<? extends ResourceLock> locks, Runnable runnable) {
        withoutLocks(locks, Factories.toFactory(runnable));
    }

    @Override
    public <T> T withoutLocks(Iterable<? extends ResourceLock> locks, Factory<T> factory) {
        if (Iterables.isEmpty(locks)) {
            return factory.create();
        }

        if (!allLockedByCurrentThread(locks)) {
            throw new IllegalStateException("Not all of the locks specified are currently held by the current thread.  This could lead to orphaned locks.");
        }

        releaseLocks(locks);
        try {
            return factory.create();
        } finally {
            if (!coordinationService.withStateLock(tryLock(locks))) {
                releaseWorkerLeaseAndWaitFor(locks);
            }
        }
    }

    @Override
    public WorkerLeaseCompletion startWorker() {
        if (!workerLeaseLockRegistry.getResourceLocksByCurrentThread().isEmpty()) {
            throw new IllegalStateException("Current thread is already a worker thread");
        }
        DefaultWorkerLease lease = getWorkerLease();
        coordinationService.withStateLock(lock(lease));
        return lease;
    }

    @Override
    public WorkerLeaseCompletion maybeStartWorker() {
        List<DefaultWorkerLease> operations = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        if (operations.isEmpty()) {
            return startWorker();
        }
        return operations.get(0);
    }

    private void releaseWorkerLeaseAndWaitFor(Iterable<? extends ResourceLock> locks) {
        Collection<? extends ResourceLock> workerLeases = workerLeaseLockRegistry.getResourceLocksByCurrentThread();
        List<ResourceLock> allLocks = Lists.newArrayList();
        allLocks.addAll(workerLeases);
        Iterables.addAll(allLocks, locks);
        // We free the worker lease but keep shared resource leases. We don't want to free shared resources until a task completes,
        // regardless of whether it is actually doing work just to make behavior more predictable. This might change in the future.
        coordinationService.withStateLock(unlock(workerLeases));
        acquireLocks(allLocks);
    }

    private boolean allLockedByCurrentThread(final Iterable<? extends ResourceLock> locks) {
        final MutableBoolean allLocked = new MutableBoolean();
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

    private class WorkerLeaseLockRegistry extends AbstractResourceLockRegistry<String, DefaultWorkerLease> {
        private final LeaseHolder root = new LeaseHolder(maxWorkerCount);

        WorkerLeaseLockRegistry(ResourceLockCoordinationService coordinationService) {
            super(coordinationService);
        }

        DefaultWorkerLease getResourceLock() {
            return new DefaultWorkerLease("worker lease", coordinationService, this, root);
        }
    }

    private class DefaultWorkerLease extends DefaultLease implements WorkerLeaseCompletion, WorkerLease {
        public DefaultWorkerLease(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner, LeaseHolder parent) {
            super(displayName, coordinationService, owner, parent);
        }

        @Override
        public void leaseFinish() {
            coordinationService.withStateLock(DefaultResourceLockCoordinationService.unlock(this));
        }
    }

    private static class ProjectLockStatisticsImpl implements ProjectLockStatistics {
        private final AtomicLong total = new AtomicLong(-1);

        @Override
        public void measure(Runnable runnable) {
            if (isEnabled()) {
                Timer timer = Time.startTimer();
                runnable.run();
                total.addAndGet(timer.getElapsedMillis());
            } else {
                runnable.run();
            }
        }

        @Override
        public long getTotalWaitTimeMillis() {
            return total.get();
        }

        public boolean isEnabled() {
            return System.getProperty(PROJECT_LOCK_STATS_PROPERTY) != null;
        }
    }
}
