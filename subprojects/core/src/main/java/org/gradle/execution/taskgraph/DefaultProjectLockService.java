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
import org.gradle.api.Task;
import org.gradle.internal.progress.BuildOperationExecutor.Operation;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProjectLockService implements ProjectLockService {
    private final ConcurrentMap<String, ProjectLock> projectLocks = Maps.newConcurrentMap();
    private final ConcurrentMap<Operation, Task> operationTaskMap = Maps.newConcurrentMap();

    @Override
    public void lockProject(Operation operation) {
        Task task = operationTaskMap.get(operation);
        if (task != null) {
            lockProject(task);
        } else {
            throw new IllegalStateException("There is no task associated with the provided operation");
        }
    }

    @Override
    public void lockProject(Task task) {
        String projectPath = task.getProject().getPath();
        projectLocks.putIfAbsent(projectPath, new ProjectLock());
        ProjectLock projectLock = projectLocks.get(projectPath);
        projectLock.lock(task.getPath());
    }

    @Override
    public boolean unlockProject(Task task) {
        String projectPath = task.getProject().getPath();
        ProjectLock projectLock = projectLocks.get(projectPath);
        if (projectLock == null) {
            return false;
        } else {
            return projectLock.unlock(task.getPath());
        }
    }

    @Override
    public boolean unlockProject(Operation operation) {
        Task task = operationTaskMap.get(operation);
        if (task != null) {
            return unlockProject(task);
        } else {
            throw new IllegalStateException("There is no task associated with the provided operation");
        }
    }

    @Override
    public boolean isLocked(String projectPath) {
        ProjectLock projectLock = projectLocks.get(projectPath);
        if (projectLock == null) {
            return false;
        } else {
            return projectLock.isLocked();
        }
    }

    @Override
    public boolean hasLock(Task task) {
        String projectPath = task.getProject().getPath();
        ProjectLock projectLock = projectLocks.get(projectPath);
        if (projectLock == null) {
            return false;
        } else {
            return projectLock.hasLock(task.getPath());
        }
    }

    @Override
    public boolean hasLock(Operation operation) {
        Task task = operationTaskMap.get(operation);
        if (task != null) {
            return hasLock(task);
        } else {
            return false;
        }
    }

    @Override
    public void withProjectLock(Task task, Operation operation, Runnable runnable) {
        operationTaskMap.put(operation, task);
        lockProject(task);
        try {
            runnable.run();
        } finally {
            unlockProject(task);
            operationTaskMap.remove(operation);
        }
    }

    @Override
    public void clear() {
        projectLocks.clear();
        operationTaskMap.clear();
    }

    private static class ProjectLock {
        private String holdingTask;
        private ReentrantLock lock = new ReentrantLock();

        void lock(String taskPath) {
            if (hasLock(taskPath)) {
                return;
            } else {
                lock.lock();
                this.holdingTask = taskPath;
            }
        }

        boolean unlock(String taskPath) {
            if (hasLock(taskPath)) {
                this.holdingTask = null;
                lock.unlock();
                return true;
            } else {
                return false;
            }
        }

        boolean hasLock(String taskPath) {
            return taskPath.equals(this.holdingTask);
        }

        boolean isLocked() {
            return holdingTask != null;
        }
    }
}
