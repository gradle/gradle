/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Sets;
import org.gradle.api.Task;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.NavigableSet;
import java.util.SortedSet;

public abstract class WorkInfo implements Comparable<WorkInfo> {
    @VisibleForTesting
    enum ExecutionState {
        UNKNOWN, NOT_REQUIRED, SHOULD_RUN, MUST_RUN, MUST_NOT_RUN, EXECUTING, EXECUTED, SKIPPED
    }

    private ExecutionState state;
    private Throwable executionFailure;
    private final NavigableSet<WorkInfo> dependencyPredecessors = Sets.newTreeSet();
    private final NavigableSet<WorkInfo> dependencySuccessors = Sets.newTreeSet();

    public WorkInfo() {
        this.state = ExecutionState.UNKNOWN;
    }

    /**
     * Adds the task associated with this node, if any, into the given collection.
     */
    public abstract void collectTaskInto(ImmutableCollection.Builder<Task> builder);

    @VisibleForTesting
    ExecutionState getState() {
        return state;
    }

    public boolean isRequired() {
        return state == ExecutionState.SHOULD_RUN;
    }

    public boolean isMustNotRun() {
        return state == ExecutionState.MUST_NOT_RUN;
    }

    public boolean isIncludeInGraph() {
        return state == ExecutionState.NOT_REQUIRED || state == ExecutionState.UNKNOWN;
    }

    public boolean isReady() {
        return state == ExecutionState.SHOULD_RUN || state == ExecutionState.MUST_RUN;
    }

    public boolean isInKnownState() {
        return state != ExecutionState.UNKNOWN;
    }

    public boolean isComplete() {
        return state == ExecutionState.EXECUTED
            || state == ExecutionState.SKIPPED
            || state == ExecutionState.UNKNOWN
            || state == ExecutionState.NOT_REQUIRED
            || state == ExecutionState.MUST_NOT_RUN;
    }

    public boolean isSuccessful() {
        return (state == ExecutionState.EXECUTED && !isFailed())
            || state == ExecutionState.NOT_REQUIRED
            || state == ExecutionState.MUST_NOT_RUN;
    }

    public boolean isFailed() {
        return getWorkFailure() != null || getExecutionFailure() != null;
    }

    public abstract Throwable getWorkFailure();

    public abstract void rethrowFailure();

    public void startExecution() {
        assert isReady();
        state = ExecutionState.EXECUTING;
    }

    public void finishExecution() {
        assert state == ExecutionState.EXECUTING;
        state = ExecutionState.EXECUTED;
    }

    public void skipExecution() {
        assert state == ExecutionState.SHOULD_RUN;
        state = ExecutionState.SKIPPED;
    }

    public void abortExecution() {
        assert isReady();
        state = ExecutionState.SKIPPED;
    }

    public void require() {
        state = ExecutionState.SHOULD_RUN;
    }

    public void doNotRequire() {
        state = ExecutionState.NOT_REQUIRED;
    }

    public void mustNotRun() {
        state = ExecutionState.MUST_NOT_RUN;
    }

    public void enforceRun() {
        assert state == ExecutionState.SHOULD_RUN || state == ExecutionState.MUST_NOT_RUN || state == ExecutionState.MUST_RUN;
        state = ExecutionState.MUST_RUN;
    }

    public void setExecutionFailure(Throwable failure) {
        assert state == ExecutionState.EXECUTING;
        this.executionFailure = failure;
    }

    public Throwable getExecutionFailure() {
        return this.executionFailure;
    }

    public SortedSet<WorkInfo> getDependencyPredecessors() {
        return dependencyPredecessors;
    }

    public SortedSet<WorkInfo> getDependencySuccessors() {
        return dependencySuccessors;
    }

    public void addDependencySuccessor(WorkInfo toNode) {
        dependencySuccessors.add(toNode);
        toNode.dependencyPredecessors.add(this);
    }

    @OverridingMethodsMustInvokeSuper
    public boolean allDependenciesComplete() {
        for (WorkInfo dependency : dependencySuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        return true;
    }

    public boolean allDependenciesSuccessful() {
        for (WorkInfo dependency : dependencySuccessors) {
            if (!dependency.isSuccessful()) {
                return false;
            }
        }
        return true;
    }

    @OverridingMethodsMustInvokeSuper
    public Iterable<WorkInfo> getAllSuccessors() {
        return dependencySuccessors;
    }

    @OverridingMethodsMustInvokeSuper
    public Iterable<WorkInfo> getAllSuccessorsInReverseOrder() {
        return dependencySuccessors.descendingSet();
    }

    @OverridingMethodsMustInvokeSuper
    public boolean hasSuccessor(WorkInfo successor) {
        return dependencySuccessors.contains(successor);
    }
}
