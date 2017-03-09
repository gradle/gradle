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
import org.gradle.internal.progress.BuildOperationExecutor.Operation;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProjectLockService implements ProjectLockService {
    private final boolean parallelEnabled;
    private final ConcurrentMap<String, ProjectLock> projectLocks = Maps.newConcurrentMap();
    private final ConcurrentMap<Operation, String> operationProjectMap = Maps.newConcurrentMap();
    private final ProjectLock buildLock = new ProjectLock();

    public DefaultProjectLockService(boolean parallelEnabled) {
        this.parallelEnabled = parallelEnabled;
    }

    private ProjectLock getProjectLock(String projectPath) {
        if (!parallelEnabled) {
            return buildLock;
        } else {
            return projectLocks.get(projectPath);
        }
    }

    private void lockProject(Operation operation) {
        String projectPath = operationProjectMap.get(operation);
        if (projectPath != null) {
            projectLocks.putIfAbsent(projectPath, new ProjectLock());
            ProjectLock projectLock = getProjectLock(projectPath);
            projectLock.lock(operation);
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
    }

    private boolean unlockProject(Operation operation) {
        String projectPath = operationProjectMap.get(operation);
        if (projectPath != null) {
            ProjectLock projectLock = getProjectLock(projectPath);
            if (projectLock == null) {
                return false;
            } else {
                return projectLock.unlock(operation);
            }
        } else {
            throw new IllegalStateException("This operation is not associated with a project");
        }
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

    private static class ProjectLock {
        private Operation holdingOperation;
        private ReentrantLock lock = new ReentrantLock();

        void lock(Operation operation) {
            if (!hasLock(operation)) {
                lock.lock();
                this.holdingOperation = operation;
            }
        }

        boolean unlock(Operation operation) {
            if (hasLock(operation)) {
                this.holdingOperation = null;
                lock.unlock();
                return true;
            } else {
                return false;
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
            return holdingOperation != null;
        }
    }
}
