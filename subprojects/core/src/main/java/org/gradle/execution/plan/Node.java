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

package org.gradle.execution.plan;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.NavigableSet;
import java.util.Set;

/**
 * A node in the execution graph that represents some executable code with potential dependencies on other nodes.
 */
public abstract class Node implements Comparable<Node> {

    protected Iterable<Node> getAllPredecessors() {
        return getDependencyPredecessors();
    }

    @VisibleForTesting
    enum ExecutionState {
        UNKNOWN, NOT_REQUIRED, SHOULD_RUN, MUST_RUN, MUST_NOT_RUN, EXECUTING, EXECUTED, SKIPPED
    }

    private ExecutionState state;
    private boolean dependenciesProcessed;
    private Throwable executionFailure;
    private final NavigableSet<Node> dependencySuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> dependencyPredecessors = Sets.newTreeSet();

    public Node() {
        this.state = ExecutionState.UNKNOWN;
    }

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
        return state != ExecutionState.NOT_REQUIRED && state != ExecutionState.UNKNOWN;
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
        return getNodeFailure() != null || getExecutionFailure() != null;
    }

    /**
     * Returns any error that happened during the execution of the node itself,
     * i.e. a task action has thrown an exception.
     */
    @Nullable
    public abstract Throwable getNodeFailure();

    public abstract void rethrowNodeFailure();

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
        if (state != ExecutionState.SHOULD_RUN) {
            // When the state changes to `SHOULD_RUN`, the dependencies need to be reprocessed since they also may be required now.
            dependenciesProcessed = false;
            state = ExecutionState.SHOULD_RUN;
        }
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

    /**
     * Returns any error that happened in the execution engine while processing this node,
     * i.e. there was a {@link NullPointerException} in the {@link ExecutionPlan} code.
     * Always leads to the abortion of the build.
     */
    @Nullable
    public Throwable getExecutionFailure() {
        return this.executionFailure;
    }

    public Set<Node> getDependencyPredecessors() {
        return dependencyPredecessors;
    }

    public Set<Node> getDependencySuccessors() {
        return dependencySuccessors;
    }

    protected void addDependencySuccessor(Node toNode) {
        dependencySuccessors.add(toNode);
        toNode.dependencyPredecessors.add(this);
    }

    @OverridingMethodsMustInvokeSuper
    public boolean allDependenciesComplete() {
        for (Node dependency : dependencySuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        return true;
    }

    public boolean allDependenciesSuccessful() {
        for (Node dependency : dependencySuccessors) {
            if (!dependency.isSuccessful()) {
                return false;
            }
        }
        return true;
    }

    public abstract void prepareForExecution();

    public abstract void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor);

    public boolean getDependenciesProcessed() {
        return dependenciesProcessed;
    }

    public void dependenciesProcessed() {
        dependenciesProcessed = true;
    }

    @OverridingMethodsMustInvokeSuper
    public Iterable<Node> getAllSuccessors() {
        return dependencySuccessors;
    }

    @OverridingMethodsMustInvokeSuper
    public Iterable<Node> getAllSuccessorsInReverseOrder() {
        return dependencySuccessors.descendingSet();
    }

    /**
     * Returns if the node has the given node as a hard successor, i.e. a non-removable relationship.
     */
    @OverridingMethodsMustInvokeSuper
    public boolean hasHardSuccessor(Node successor) {
        return dependencySuccessors.contains(successor);
    }

    public abstract Set<Node> getFinalizers();

    public abstract boolean isPublicNode();

    /**
     * Whether the task needs to be queried if it is completed.
     *
     * Everything where the value of {@link #isComplete()} depends on some other state, like another task in an included build.
     */
    public abstract boolean requiresMonitoring();

    /**
     * Returns the project which the node requires access to, if any.
     *
     * This should return an identifier or the {@link org.gradle.api.internal.project.ProjectState} container, or some abstract resource, rather than the mutable project state itself.
     */
    @Nullable
    public abstract Project getProject();

    @Override
    public abstract String toString();

}
