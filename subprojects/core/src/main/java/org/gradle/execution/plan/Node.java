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
import org.gradle.api.tasks.VerificationException;
import org.gradle.internal.resources.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.Collections;
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
        // Node has not been added to any execution plan
        UNKNOWN,
        // Node has been filtered from the current execution plan and must not execute
        NOT_REQUIRED,
        SHOULD_RUN,
        MUST_RUN,
        MUST_NOT_RUN,
        EXECUTING,
        // Node has been executed (and possibly failed) in an execution plan (not necessarily the current)
        EXECUTED,
        // Either cannot be executed because of a failed dependency or was skipped because the execution plan was aborted
        // Should split this into two separate states, or perhaps use NOT_REQUIRED for the abort case
        SKIPPED
    }

    enum DependenciesState {
        NOT_COMPLETE,
        COMPLETE_AND_SUCCESSFUL,
        COMPLETE_AND_NOT_SUCCESSFUL
    }

    private ExecutionState state;
    private boolean dependenciesProcessed;
    private DependenciesState dependenciesState = DependenciesState.NOT_COMPLETE;
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
        return state != ExecutionState.NOT_REQUIRED && state != ExecutionState.UNKNOWN && state != ExecutionState.EXECUTED && state != ExecutionState.SKIPPED;
    }

    public boolean isAlreadyExecuted() {
        return state == ExecutionState.EXECUTED || state == ExecutionState.SKIPPED;
    }

    /**
     * Is this node ready to execute? Note: does not consider the dependencies of the node.
     */
    public boolean isReady() {
        return state == ExecutionState.SHOULD_RUN || state == ExecutionState.MUST_RUN;
    }

    public boolean isInKnownState() {
        return state != ExecutionState.UNKNOWN;
    }

    public boolean isExecuting() {
        return state == ExecutionState.EXECUTING;
    }

    /**
     * Is it possible for this node to run? Returns {@code true} if this node definitely will not run, {@code false} if it is still possible for the node to run.
     *
     * <p>A node may be complete for several reasons, for example when its actions have been executed, or when its outputs have been considered up-to-date or loaded from the build cache,
     * or when it cannot run due to a failure in a dependency.</p>
     */
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

    /**
     * Whether this node failed with a verification failure.
     *
     * @return true if failed and threw {@link VerificationException}, false otherwise
     */
    public boolean isVerificationFailure() {
        return getNodeFailure() != null && getNodeFailure().getCause() instanceof VerificationException;
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
        assert state == ExecutionState.SHOULD_RUN || state == ExecutionState.MUST_RUN;
        state = ExecutionState.SKIPPED;
        completionAction.accept(this);
    }

    public void abortExecution(Consumer<Node> completionAction) {
        assert isReady();
        state = ExecutionState.SKIPPED;
        completionAction.accept(this);
    }

    public void require() {
        if (state == ExecutionState.EXECUTED) {
            return;
        }
        if (state != ExecutionState.SHOULD_RUN) {
            // When the state changes to `SHOULD_RUN`, the dependencies need to be reprocessed since they also may be required now.
            dependenciesProcessed = false;
            state = ExecutionState.SHOULD_RUN;
        }
    }

    public void doNotRequire() {
        if (state == ExecutionState.EXECUTED) {
            return;
        }
        state = ExecutionState.NOT_REQUIRED;
    }

    public void mustNotRun() {
        assert state == ExecutionState.UNKNOWN;
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
        if (dependenciesState == DependenciesState.NOT_COMPLETE) {
            forceAllDependenciesCompleteUpdate();
            return dependenciesState != DependenciesState.NOT_COMPLETE;
        }
        return false;
    }

    public void forceAllDependenciesCompleteUpdate() {
        if (doCheckDependenciesComplete()) {
            if (dependencySuccessors.stream().allMatch(this::shouldContinueExecution)) {
                dependenciesState = DependenciesState.COMPLETE_AND_SUCCESSFUL;
            } else {
                dependenciesState = DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL;
            }
        } else {
            dependenciesState = DependenciesState.NOT_COMPLETE;
        }
    }

    public boolean allDependenciesComplete() {
        return dependenciesState != DependenciesState.NOT_COMPLETE;
    }

    public boolean allDependenciesSuccessful() {
        return dependenciesState == DependenciesState.COMPLETE_AND_SUCCESSFUL;
    }

    /**
     * This {@link Node} may continue execution if the successor Node was successful, or if non-successful when two specific criteria are met:
     * <ol>
     *     <li>The successor node failure is a "verification failure"</li>
     *     <li>The relationship to the successor Node is via task output/task input wiring, not an explicit dependsOn relationship (which are discouraged)</li>
     * </ol>
     *
     * @param dependency a successor node in the execution plan
     * @return true if the successor task was successful, or failed but a "recoverable" verification failure and this Node may continue execution; false otherwise
     * @see <a href="https://github.com/gradle/gradle/issues/18912">gradle/gradle#18912</a>
     */
    protected boolean shouldContinueExecution(Node dependency) {
        return dependency.isSuccessful() || (dependency.isVerificationFailure() && !dependsOnOutcome(dependency));
    }

    /**
     * Can be overridden to indicate the relationship between this {@link Node} and a successor Node.
     *
     * @param dependency a non-successful successor node in the execution plan
     * @return Always returns false unless overridden.
     */
    protected boolean dependsOnOutcome(Node dependency) {
        return false;
    }

    @OverridingMethodsMustInvokeSuper
    protected Iterable<Node> getAllPredecessors() {
        return getDependencyPredecessors();
    }

    /**
     * Called when this node is added to the work graph, prior to resolving its dependencies.
     *
     * @param monitor An action that should be called when this node is ready to execute, when the dependencies for this node are executed outside
     * the work graph that contains this node (for example, when the node represents a task in an included build).
     */
    public void prepareForExecution(Action<Node> monitor) {
    }

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

    /**
     * Returns all the nodes which are hard successors, i.e. which have a non-removable relationship to the current node.
     *
     * For example, for tasks `shouldRunAfter` isn't a hard successor while `mustRunAfter` is.
     */
    @OverridingMethodsMustInvokeSuper
    public Iterable<Node> getHardSuccessors() {
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

    public Set<Node> getFinalizers() {
        return Collections.emptySet();
    }

    /**
     * Returns a node that should be executed prior to this node, once this node is ready to execute and it dependencies complete.
     */
    @Nullable
    public Node getPrepareNode() {
        return null;
    }

    public MutationInfo getMutationInfo() {
        return mutationInfo;
    }

    public boolean isPublicNode() {
        return false;
    }

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
