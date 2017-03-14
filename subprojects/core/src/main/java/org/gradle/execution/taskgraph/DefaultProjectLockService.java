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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.gradle.internal.event.ListenerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProjectLockService implements ProjectLockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProjectLockService.class);

    private final boolean parallelEnabled;
    private final ConcurrentMap<String, ProjectLock> projectLocks = Maps.newConcurrentMap();
    private final Multimap<Long, String> threadProjectMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.<Long, String>create());
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

    private boolean tryLockProject(String projectPath, Long threadId) {
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
            ProjectLock projectLock = getProjectLock(projectPath);
            return projectLock.tryLock();
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    private void lockProject(String projectPath) {
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
            ProjectLock projectLock = getProjectLock(projectPath);
            projectLock.lock();
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    private void unlockProject(String projectPath) {
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
        Collection<String> projectPaths = threadProjectMap.get(threadId);
        if (!projectPaths.isEmpty()) {
            for (String projectPath : projectPaths) {
                ProjectLock projectLock = getProjectLock(projectPath);
                if (projectLock != null && projectLock.hasLock()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean hasLock(String projectPath) {
        Long threadId = Thread.currentThread().getId();
        Collection<String> projectPaths = threadProjectMap.get(threadId);
        if (!projectPaths.isEmpty()) {
            for (String nextProjectPath : projectPaths) {
                if (nextProjectPath.equals(projectPath)) {
                    ProjectLock projectLock = getProjectLock(projectPath);
                    if (projectLock != null && projectLock.hasLock()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void withProjectLock(String projectPath, Runnable runnable) {
        Long threadId = Thread.currentThread().getId();
        if (!hasLock(projectPath)) {
            threadProjectMap.put(threadId, projectPath);
            lockProject(projectPath);
            try {
                runnable.run();
            } finally {
                unlockProject(projectPath);
                threadProjectMap.remove(threadId, projectPath);
            }
        } else {
            runnable.run();
        }
    }

    @Override
    public boolean tryWithProjectLock(String projectPath, Runnable runnable) {
        Long threadId = Thread.currentThread().getId();
        if (!hasLock(projectPath)) {
            threadProjectMap.put(threadId, projectPath);
            if (tryLockProject(projectPath, threadId)) {
                try {
                    runnable.run();
                } finally {
                    unlockProject(projectPath);
                    threadProjectMap.remove(threadId, projectPath);
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
    public void withoutProjectLock(Runnable runnable) {
        if (hasLock()) {
            Long threadId = Thread.currentThread().getId();
            for (String projectPath : threadProjectMap.get(threadId)) {
                unlockProject(projectPath);
            }
            try {
                runnable.run();
            } finally {
                for (String projectPath : threadProjectMap.get(threadId)) {
                    lockProject(projectPath);
                }
            }
        } else {
            runnable.run();
        }
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
