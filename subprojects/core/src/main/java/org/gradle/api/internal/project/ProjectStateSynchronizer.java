/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.work.Synchronizer;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

final class ProjectStateSynchronizer implements Synchronizer {
    private final WorkerLeaseService workerLeaseService;

    private final ResourceLock allProjectsLock;
    private final ResourceLock projectLock;

    private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();

    public ProjectStateSynchronizer(BuildState owner, ProjectIdentity identity, WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
        this.allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
        this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identity.getBuildTreePath());
    }

    public ResourceLock getProjectLock() {
        return projectLock;
    }

    public boolean holdsLock() {
        Thread currentThread = Thread.currentThread();
        if (canDoAnythingToThisProject.contains(currentThread) || workerLeaseService.isAllowedUncontrolledAccessToAnyProject()) {
            return true;
        }
        Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
        return locks.contains(projectLock) || locks.contains(allProjectsLock);
    }

    public <S> S forceLock(Supplier<S> factory) {
        Thread currentThread = Thread.currentThread();
        boolean added = canDoAnythingToThisProject.add(currentThread);
        try {
            return factory.get();
        } finally {
            if (added) {
                canDoAnythingToThisProject.remove(currentThread);
            }
        }
    }

    @Override
    public void withLock(Runnable action) {
        withLock(Factories.toFactory(action));
    }

    @Override
    public <T> T withLock(Factory<T> action) {
        Thread currentThread = Thread.currentThread();
        if (workerLeaseService.isAllowedUncontrolledAccessToAnyProject() || canDoAnythingToThisProject.contains(currentThread)) {
            // Current thread is allowed to access anything at any time, so run the action
            return action.create();
        }

        Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
        if (currentLocks.contains(projectLock) || currentLocks.contains(allProjectsLock)) {
            // if we already hold the project lock for this project
            if (currentLocks.size() == 1) {
                // the lock for this project is the only lock we hold, can run the action
                return action.create();
            } else {
                throw new IllegalStateException("Current thread holds more than one project lock. It should hold only one project lock at any given time.");
            }
        } else {
            // DEBUG (flaky investigation): trace contention on this project's lock.
            debugLog("LOCK-ATTEMPT " + projectLock);
            return workerLeaseService.withReplacedLocks(currentLocks, projectLock, () -> {
                debugLog("LOCK-ACQUIRED " + projectLock);
                try {
                    return action.create();
                } finally {
                    debugLog("LOCK-RELEASED " + projectLock);
                }
            });
        }
    }

    // DEBUG (flaky investigation): direct stdout so it is forwarded to the tooling client and captured by CI.
    private static void debugLog(String message) {
        System.out.println("@@CFGDBG@@ " + System.currentTimeMillis() + " [" + Thread.currentThread().getName() + "] " + message);
        System.out.flush();
    }
}
