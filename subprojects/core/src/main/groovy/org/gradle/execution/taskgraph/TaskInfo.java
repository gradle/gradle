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

class TaskInfo {

    private enum TaskExecutionState {
        READY, EXECUTING, EXECUTED, SKIPPED
    }

    private final TaskInternal task;
    private final Set<TaskInfo> dependencies;
    private TaskExecutionState state;
    private Throwable executionFailure;

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
        return state == TaskExecutionState.EXECUTED || state == TaskExecutionState.SKIPPED;
    }

    public boolean isSuccessful() {
        return state == TaskExecutionState.EXECUTED && !isFailed();
    }

    public boolean isFailed() {
        return getTaskFailure() != null || getExecutionFailure() != null;
    }

    public void startExecution() {
        assert state == TaskExecutionState.READY;
        state = TaskExecutionState.EXECUTING;
    }

    public void finishExecution() {
        assert state == TaskExecutionState.EXECUTING;
        state = TaskExecutionState.EXECUTED;
    }

    public void skipExecution() {
        assert state == TaskExecutionState.READY;
        state = TaskExecutionState.SKIPPED;
    }

    public void setExecutionFailure(Throwable failure) {
        assert state == TaskExecutionState.EXECUTING;
        this.executionFailure = failure;
    }

    public Throwable getExecutionFailure() {
        return this.executionFailure;
    }

    public Throwable getTaskFailure() {
        return this.getTask().getState().getFailure();
    }

    public boolean allDependenciesComplete() {
        for (TaskInfo dependency : getDependencies()) {
            if (!dependency.isComplete()) {
                return false;
            }
        }
        return true;
    }

    public boolean allDependenciesSuccessful() {
        for (TaskInfo dependency : getDependencies()) {
            if (!dependency.isSuccessful()) {
                return false;
            }
        }
        return true;
    }
}
