/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;

import java.util.Set;

public class TaskInfo {

    private enum TaskExecutionState {
        READY, EXECUTING, SUCCEEDED, FAILED
    }

    private final TaskInternal task;
    private final Set<TaskInfo> dependencies;
    private TaskExecutionState state;

    public TaskInfo(TaskInternal task, Set<TaskInfo> dependencies) {
        this.task = task;
        this.dependencies = dependencies;
        this.state = TaskExecutionState.READY;
    }

    public TaskInternal getTask() {
        return task;
    }

    public Set<TaskInfo> getDependencies() {
        return dependencies;
    }

    public boolean isReady() {
        return state == TaskExecutionState.READY;
    }

    public boolean isComplete() {
        return state == TaskExecutionState.SUCCEEDED || state == TaskExecutionState.FAILED;
    }

    public boolean isFailed() {
        return state == TaskExecutionState.FAILED;
    }

    public void startExecution() {
        assert state == TaskExecutionState.READY;
        state = TaskExecutionState.EXECUTING;
    }

    public void executionSucceeded() {
        assert state == TaskExecutionState.EXECUTING;
        state = TaskExecutionState.SUCCEEDED;
    }

    public void executionFailed() {
        assert state == TaskExecutionState.EXECUTING;
        state = TaskExecutionState.FAILED;
    }

    public boolean dependenciesExecuted() {
        for (TaskInfo dependency : getDependencies()) {
            if (!dependency.isComplete()) {
                System.out.printf("Cannot start %s because %s is not yet executed\n", getTask().getPath(), dependency.getTask().getPath());
                return false;
            }
        }
        return true;
    }

    public boolean dependenciesFailed() {
        for (TaskInfo dependency : getDependencies()) {
            if (dependency.isFailed()) {
                return true;
            }
        }
        return false;
    }

}
