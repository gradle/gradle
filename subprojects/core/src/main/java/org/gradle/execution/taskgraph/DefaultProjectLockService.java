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
import org.gradle.internal.progress.BuildOperationExecutor.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProjectLockService implements ProjectLockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProjectLockService.class);

    private final boolean parallelEnabled;
    private final ConcurrentMap<String, ProjectLock> projectLocks = Maps.newConcurrentMap();
    private final ConcurrentMap<Operation, String> operationProjectMap = Maps.newConcurrentMap();
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

    @Override
    public boolean lockProject(String projectPath) {
        projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
        ProjectLock projectLock = getProjectLock(projectPath);
        return projectLock.lock();
    }

    @Override
    public void unlockProject(String projectPath) {
        ProjectLock projectLock = getProjectLock(projectPath);
        if (projectLock != null) {
            projectLock.unlock();
        }
    }

    private void lockProject(Operation operation) {
        String projectPath = operationProjectMap.get(operation);
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock(projectPath));
            ProjectLock projectLock = getProjectLock(projectPath);
            projectLock.lock(operation);
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    private void unlockProject(Operation operation) {
        String projectPath = operationProjectMap.get(operation);
        if (projectPath != null) {
            ProjectLock projectLock = getProjectLock(projectPath);
            if (projectLock != null) {
                projectLock.unlock(operation);
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
    public boolean hasLock(Operation operation) {
        String projectPath = getProjectPathForOperation(operation);
        if (projectPath != null) {
            ProjectLock projectLock = getProjectLock(projectPath);
            if (projectLock == null) {
                return false;
            } else {
                return projectLock.hasLock(operation);
            }
        } else {
            return false;
        }
    }

    @Override
    public void withProjectLock(String projectPath, Operation operation, Runnable runnable) {
        if (!hasLock(operation)) {
            operationProjectMap.put(operation, projectPath);
            lockProject(operation);
            try {
                runnable.run();
            } finally {
                unlockProject(operation);
                operationProjectMap.remove(operation);
            }
        } else {
            runnable.run();
        }
    }

    @Override
    public void withoutProjectLock(Operation operation, Runnable runnable) {
        if (hasLock(operation)) {
            String projectPath = getProjectPathForOperation(operation);
            ProjectLock projectLock = getProjectLock(projectPath);
            Operation holdingOperation = projectLock.getHoldingOperation();
            unlockProject(holdingOperation);
            try {
                runnable.run();
            } finally {
                lockProject(holdingOperation);
            }
        } else {
            runnable.run();
        }
    }

    private String getProjectPathForOperation(Operation operation) {
        if (operation == null) {
            return null;
        }

        String projectPath = operationProjectMap.get(operation);
        if (projectPath == null) {
            return getProjectPathForOperation(operation.getParentOperation());
        } else {
            return projectPath;
        }
    }

    private class ProjectLock {
        private final String projectPath;
        private Operation holdingOperation;
        private ReentrantLock lock = new ReentrantLock();

        public ProjectLock(String projectPath) {
            this.projectPath = projectPath;
        }

        boolean lock() {
            if (lock.tryLock()) {
                LOGGER.debug("{}: acquired project lock on {}", Thread.currentThread().getName(), projectPath);
                return true;
            } else {
                return false;
            }
        }

        void unlock() {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.debug("{}: released project lock on {}", Thread.currentThread().getName(), projectPath);
                projectLockBroadcast.onProjectUnlock(projectPath);
            }
        }

        void lock(Operation operation) {
            if (!hasLock(operation)) {
                if (!lock.isHeldByCurrentThread()) {
                    lock.lock();
                    LOGGER.debug("{}: acquired project lock on {}", Thread.currentThread().getName(), projectPath);
                }
                this.holdingOperation = operation;
            }
        }

        void unlock(Operation operation) {
            if (hasLock(operation)) {
                this.holdingOperation = null;
                unlock();
            }
        }

        boolean hasLock(Operation operation) {
            if (operation == null) {
                return false;
            }
            if (operation.equals(this.holdingOperation)) {
                return true;
            } else {
                return hasLock(operation.getParentOperation());
            }
        }

        Operation getHoldingOperation() {
            return holdingOperation;
        }

        boolean isLocked() {
            return lock.isLocked();
        }
    }
}
