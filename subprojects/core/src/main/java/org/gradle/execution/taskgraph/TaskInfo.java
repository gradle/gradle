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

import java.util.TreeSet;

public class TaskInfo implements Comparable<TaskInfo> {

    private enum TaskExecutionState {
        UNKNOWN, NOT_REQUIRED, SHOULD_RUN, MUST_RUN, MUST_NOT_RUN, EXECUTING, EXECUTED, SKIPPED
    }

    private final TaskInternal task;
    private TaskExecutionState state;
    private Throwable executionFailure;
    private boolean dependenciesProcessed;
    private final TreeSet<TaskInfo> dependencyPredecessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> dependencySuccessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> mustSuccessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> shouldSuccessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> finalizers = new TreeSet<TaskInfo>();

    public TaskInfo(TaskInternal task) {
        this.task = task;
        this.state = TaskExecutionState.UNKNOWN;
    }

    public TaskInternal getTask() {
        return task;
    }

    public boolean isRequired() {
        return state == TaskExecutionState.SHOULD_RUN;
    }

    public boolean isMustNotRun() {
        return state == TaskExecutionState.MUST_NOT_RUN;
    }

    public boolean isIncludeInGraph() {
        return state == TaskExecutionState.NOT_REQUIRED || state == TaskExecutionState.UNKNOWN;
    }

    public boolean isReady() {
        return state == TaskExecutionState.SHOULD_RUN || state == TaskExecutionState.MUST_RUN;
    }

    public boolean isInKnownState() {
        return state != TaskExecutionState.UNKNOWN;
    }

    public boolean isComplete() {
        return state == TaskExecutionState.EXECUTED
                || state == TaskExecutionState.SKIPPED
                || state == TaskExecutionState.UNKNOWN
                || state == TaskExecutionState.NOT_REQUIRED
                || state == TaskExecutionState.MUST_NOT_RUN;
    }

    public boolean isSuccessful() {
        return (state == TaskExecutionState.EXECUTED && !isFailed())
                || state == TaskExecutionState.NOT_REQUIRED
                || state == TaskExecutionState.MUST_NOT_RUN;
    }

    public boolean isFailed() {
        return getTaskFailure() != null || getExecutionFailure() != null;
    }

    public void startExecution() {
        assert isReady();
        state = TaskExecutionState.EXECUTING;
    }

    public void finishExecution() {
        assert state == TaskExecutionState.EXECUTING;
        state = TaskExecutionState.EXECUTED;
    }

    public void skipExecution() {
        assert state == TaskExecutionState.SHOULD_RUN;
        state = TaskExecutionState.SKIPPED;
    }

    public void abortExecution() {
        assert isReady();
        state = TaskExecutionState.SKIPPED;
    }

    public void require() {
        state = TaskExecutionState.SHOULD_RUN;
    }

    public void doNotRequire() {
        state = TaskExecutionState.NOT_REQUIRED;
    }

    public void mustNotRun() {
        state = TaskExecutionState.MUST_NOT_RUN;
    }

    public void enforceRun() {
        assert state == TaskExecutionState.SHOULD_RUN || state == TaskExecutionState.MUST_NOT_RUN || state == TaskExecutionState.MUST_RUN;
        state = TaskExecutionState.MUST_RUN;
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
        for (TaskInfo dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        for (TaskInfo dependency : dependencySuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        return true;
    }

    public boolean allDependenciesSuccessful() {
        for (TaskInfo dependency : dependencySuccessors) {
            if (!dependency.isSuccessful()) {
                return false;
            }
        }
        return true;
    }

    public TreeSet<TaskInfo> getDependencyPredecessors() {
        return dependencyPredecessors;
    }

    public TreeSet<TaskInfo> getDependencySuccessors() {
        return dependencySuccessors;
    }

    public TreeSet<TaskInfo> getMustSuccessors() {
        return mustSuccessors;
    }

    public TreeSet<TaskInfo> getFinalizers() {
        return finalizers;
    }

    public TreeSet<TaskInfo> getShouldSuccessors() {
        return shouldSuccessors;
    }

    public boolean getDependenciesProcessed() {
        return dependenciesProcessed;
    }

    public void dependenciesProcessed() {
        dependenciesProcessed = true;
    }

    public void addDependencySuccessor(TaskInfo toNode) {
        dependencySuccessors.add(toNode);
        toNode.dependencyPredecessors.add(this);
    }

    public void addMustSuccessor(TaskInfo toNode) {
        mustSuccessors.add(toNode);
    }

    public void addFinalizer(TaskInfo finalizerNode) {
        finalizers.add(finalizerNode);
    }

    public void addShouldSuccessor(TaskInfo toNode) {
        shouldSuccessors.add(toNode);
    }

    public void removeShouldRunAfterSuccessor(TaskInfo toNode) {
        shouldSuccessors.remove(toNode);
    }

    public int compareTo(TaskInfo otherInfo) {
        return task.compareTo(otherInfo.getTask());
    }

    public String toString() {
        return task.getPath();
    }
}
