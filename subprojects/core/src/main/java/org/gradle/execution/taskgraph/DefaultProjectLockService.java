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

package org.gradle.execution.taskgraph;

import com.google.common.collect.Maps;
import org.gradle.internal.event.ListenerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProjectLockService implements ProjectLockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProjectLockService.class);

    private final boolean parallelEnabled;
    private final ConcurrentMap<String, ProjectLock> projectLocks = Maps.newConcurrentMap();
    private final ConcurrentMap<Long, String> threadProjectMap = Maps.newConcurrentMap();
    private final ListenerManager listenerManager;
    private final ProjectLockListener projectLockBroadcast;
    private final ProjectLock buildLock = new ProjectLock("BUILD");

    public DefaultProjectLockService(ListenerManager listenerManager, boolean parallelEnabled) {
        this.listenerManager = listenerManager;
        this.projectLockBroadcast = listenerManager.getBroadcaster(ProjectLockListener.class);
        this.parallelEnabled = parallelEnabled;
    }

    private ProjectLock getProjectLock(String projectPath) {
        if (!parallelEnabled) {
            return buildLock;
        } else {
            return projectLocks.get(projectPath);
        }
    }

    public boolean tryLockProject(Long threadId) {
        String projectPath = threadProjectMap.get(threadId);
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
            ProjectLock projectLock = getProjectLock(projectPath);
            return projectLock.tryLock();
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    public void unlockProject(String projectPath) {
        ProjectLock projectLock = getProjectLock(projectPath);
        if (projectLock != null) {
            projectLock.unlock();
        }
    }

    private void lockProject(Long threadId) {
        String projectPath = threadProjectMap.get(threadId);
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
            ProjectLock projectLock = getProjectLock(projectPath);
            projectLock.lock();
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    private void unlockProject(Long threadId) {
        String projectPath = threadProjectMap.get(threadId);
        if (projectPath != null) {
            ProjectLock projectLock = getProjectLock(projectPath);
            if (projectLock != null) {
                projectLock.unlock();
            }
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
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
    public boolean isLocked(String projectPath) {
        ProjectLock projectLock = getProjectLock(projectPath);
        if (projectLock == null) {
            return false;
        } else {
            return projectLock.isLocked();
        }
    }

    @Override
    public boolean hasLock() {
        Long threadId = Thread.currentThread().getId();
        String projectPath = getProjectPathForThread(threadId);
        if (projectPath != null) {
            ProjectLock projectLock = getProjectLock(projectPath);
            if (projectLock == null) {
                return false;
            } else {
                return projectLock.hasLock();
            }
        } else {
            return false;
        }
    }

    @Override
    public void withProjectLock(String projectPath, Runnable runnable) {
        Long threadId = Thread.currentThread().getId();
        if (!hasLock()) {
            threadProjectMap.put(threadId, projectPath);
            lockProject(threadId);
            try {
                runnable.run();
            } finally {
                unlockProject(threadId);
                threadProjectMap.remove(threadId);
            }
        } else {
            checkAgainstExistingLocks(threadId, projectPath);
            runnable.run();
        }
    }

    @Override
    public boolean tryWithProjectLock(String projectPath, Runnable runnable) {
        Long threadId = Thread.currentThread().getId();
        if (!hasLock()) {
            threadProjectMap.put(threadId, projectPath);
            if (tryLockProject(threadId)) {
                try {
                    runnable.run();
                } finally {
                    unlockProject(threadId);
                    threadProjectMap.remove(threadId);
                }
                return true;
            }
        } else {
            checkAgainstExistingLocks(threadId, projectPath);
            runnable.run();
            return true;
        }

        return false;
    }

    private void checkAgainstExistingLocks(Long threadId, String projectPath) {
        String currentLockedProject = getProjectPathForThread(threadId);
        if (!currentLockedProject.equals(projectPath)) {
            // Implement this if it's needed
            throw new UnsupportedOperationException("Thread " + threadId + " called withProjectLock() for " + projectPath + " but is already associated with a lock on " + currentLockedProject);
        }
    }

    @Override
    public void withoutProjectLock(Runnable runnable) {
        Long threadId = Thread.currentThread().getId();
        if (hasLock()) {
            String projectPath = getProjectPathForThread(threadId);
            ProjectLock projectLock = getProjectLock(projectPath);
            unlockProject(threadId);
            try {
                runnable.run();
            } finally {
                lockProject(threadId);
            }
        } else {
            runnable.run();
        }
    }

    private String getProjectPathForThread(Long threadId) {
        return threadProjectMap.get(threadId);
    }

    private class ProjectLock {
        private final String projectPath;
        private ReentrantLock lock = new ReentrantLock();

        public ProjectLock(String projectPath) {
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

        boolean hasLock() {
            return lock.isHeldByCurrentThread();
        }

        boolean isLocked() {
            return lock.isLocked();
        }
    }
}
