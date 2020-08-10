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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.resources.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A node in the execution graph that represents some executable code with potential dependencies on other nodes.
 */
public abstract class Node implements Comparable<Node> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);

    @VisibleForTesting
    enum ExecutionState {
        UNKNOWN, NOT_REQUIRED, SHOULD_RUN, MUST_RUN, MUST_NOT_RUN, EXECUTING, EXECUTED, SKIPPED
    }

    private ExecutionState state;
    private boolean dependenciesProcessed;
    private boolean allDependenciesComplete;
    private Throwable executionFailure;
    private final NavigableSet<Node> dependencySuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> dependencyPredecessors = Sets.newTreeSet();
    private final MutationInfo mutationInfo = new MutationInfo(this);

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

    public boolean isExecuted() {
        return state == ExecutionState.EXECUTED;
    }

    /**
     * Returns any error that happened during the execution of the node itself,
     * i.e. a task action has thrown an exception.
     */
    @Nullable
    public abstract Throwable getNodeFailure();

    public abstract void rethrowNodeFailure();

    public void startExecution(Consumer<Node> nodeStartAction) {
        assert isReady();
        state = ExecutionState.EXECUTING;
        nodeStartAction.accept(this);
    }

    public void finishExecution(Consumer<Node> completionAction) {
        assert state == ExecutionState.EXECUTING;
        state = ExecutionState.EXECUTED;
        completionAction.accept(this);
    }

    public void skipExecution(Consumer<Node> completionAction) {
        assert state == ExecutionState.SHOULD_RUN;
        state = ExecutionState.SKIPPED;
        completionAction.accept(this);
    }

    public void abortExecution(Consumer<Node> completionAction) {
        assert isReady();
        state = ExecutionState.SKIPPED;
        completionAction.accept(this);
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

    public void addDependencySuccessor(Node toNode) {
        dependencySuccessors.add(toNode);
        toNode.getDependencyPredecessors().add(this);
    }

    @OverridingMethodsMustInvokeSuper
    protected boolean doCheckDependenciesComplete() {
        LOGGER.debug("Checking if all dependencies are complete for {}", this);
        for (Node dependency : dependencySuccessors) {
            if (!dependency.isComplete()) {
                LOGGER.debug("Dependency {} for {} not yet completed", dependency, this);
                return false;
            }
        }

        LOGGER.debug("All dependencies are complete for {}", this);
        return true;
    }

    /**
     * Returns if all dependencies completed, but have not been completed in the last check.
     */
    public boolean updateAllDependenciesComplete() {
        if (!allDependenciesComplete) {
            forceAllDependenciesCompleteUpdate();
            return allDependenciesComplete;
        }
        return false;
    }

    public void forceAllDependenciesCompleteUpdate() {
        allDependenciesComplete = doCheckDependenciesComplete();
    }

    public boolean allDependenciesComplete() {
        return allDependenciesComplete;
    }

    public boolean allDependenciesSuccessful() {
        for (Node dependency : dependencySuccessors) {
            if (!dependency.isSuccessful()) {
                return false;
            }
        }
        return true;
    }

    @OverridingMethodsMustInvokeSuper
    protected Iterable<Node> getAllPredecessors() {
        return getDependencyPredecessors();
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

    public MutationInfo getMutationInfo() {
        return mutationInfo;
    }

    public abstract void resolveMutations();

    public abstract boolean isPublicNode();

    /**
     * Whether the task needs to be queried if it is completed.
     *
     * Everything where the value of {@link #isComplete()} depends on some other state, like another task in an included build.
     */
    public abstract boolean requiresMonitoring();

    /**
     * Returns the project state that this node requires mutable access to, if any.
     */
    @Nullable
    public abstract ResourceLock getProjectToLock();

    /**
     * Returns the project which this node belongs to, and requires access to the execution services of.
     * Returning non-null does not imply that the project must be locked when this node executes. Use {@link #getProjectToLock()} instead for that.
     *
     * TODO - this should return some kind of abstract 'action context' instead of a mutable project.
     */
    @Nullable
    public abstract ProjectInternal getOwningProject();

    /**
     * Returns the resources which should be locked before starting this node.
     */
    public abstract List<? extends ResourceLock> getResourcesToLock();

    @Override
    public abstract String toString();

}
