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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.VerificationException;
import org.gradle.internal.resources.ResourceLock;

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
    @VisibleForTesting
    enum ExecutionState {
        // Node is not scheduled to run in any plan
        // Nodes may be moved back into this state when the execution plan is cancelled or aborted due to a failure
        NOT_SCHEDULED,
        // Node has been scheduled in an execution plan and should run if possible (depending on failures in other nodes)
        SHOULD_RUN,
        // Node is currently executing
        EXECUTING,
        // Node has been executed, and possibly failed, in an execution plan (not necessarily the current)
        EXECUTED,
        // Node cannot be executed because of a failed dependency
        FAILED_DEPENDENCY
    }

    public enum DependenciesState {
        // Still waiting for dependencies to complete
        NOT_COMPLETE,
        // All dependencies complete, can run this node
        COMPLETE_AND_SUCCESSFUL,
        // All dependencies complete, but cannot run this node due to failure
        COMPLETE_AND_NOT_SUCCESSFUL,
        // All dependencies complete, but this node does not need to run
        COMPLETE_AND_CAN_SKIP
    }

    private ExecutionState state = ExecutionState.NOT_SCHEDULED;
    private boolean dependenciesProcessed;
    private DependenciesState dependenciesState = DependenciesState.NOT_COMPLETE;
    private Throwable executionFailure;
    private boolean filtered;
    private final NavigableSet<Node> dependencySuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> dependencyPredecessors = Sets.newTreeSet();
    private final MutationInfo mutationInfo = new MutationInfo(this);
    private NodeGroup group = NodeGroup.DEFAULT_GROUP;

    @VisibleForTesting
    ExecutionState getState() {
        return state;
    }

    String healthDiagnostics() {
        if (isComplete()) {
            return this + " (state=" + state + ")";
        } else {
            String specificState = nodeSpecificHealthDiagnostics();
            if (!specificState.isEmpty()) {
                specificState = ", " + specificState;
            }
            return this + " (state=" + state + ", dependencies=" + dependenciesState + specificState + ", group=" + group + ", successors=" + getHardSuccessors() + ")";
        }
    }

    protected String nodeSpecificHealthDiagnostics() {
        return "";
    }

    public NodeGroup getGroup() {
        return group;
    }

    public void setGroup(NodeGroup group) {
        if (this.group != group) {
            this.group.removeMember(this);
            this.group = group;
            this.group.addMember(this);
        }
    }

    @Nullable
    public OrdinalGroup getOrdinal() {
        return group.asOrdinal();
    }

    /**
     * Potentially update the ordinal group of this node when it is reachable from the given group.
     */
    public void maybeInheritOrdinalAsDependency(NodeGroup candidate) {
        // This is called prior to updating the groups of finalizers and their dependencies. So both this node and the candidate can be:
        // - in the "default" group (ie not-a-group) -> use the candidate
        // - in an ordinal group -> use the group with the lowest ordinal
        //
        if (group == candidate || candidate == NodeGroup.DEFAULT_GROUP) {
            return;
        }
        if (group == NodeGroup.DEFAULT_GROUP) {
            setGroup(candidate);
            return;
        }

        OrdinalGroup candidateOrdinal = (OrdinalGroup) candidate;
        OrdinalGroup currentOrdinal = (OrdinalGroup) group;
        if (candidateOrdinal.getOrdinal() < currentOrdinal.getOrdinal()) {
            setGroup(candidate);
        }
    }

    /**
     * Maybe update the group for this node when it is a finalizer for the given node.
     *
     * <p>When this method is called, the group of each node that depends on this node has been updated.</p>
     */
    public void updateGroupOfFinalizer() {
        NodeGroup newGroup = group;
        for (Node predecessor : getDependencyPredecessors()) {
            if (predecessor.getGroup() instanceof HasFinalizers) {
                newGroup = maybeInheritGroupAsFinalizerDependency((HasFinalizers) predecessor.getGroup(), newGroup);
            }
        }
        if (newGroup != group) {
            setGroup(newGroup);
        }
    }

    private static HasFinalizers maybeInheritGroupAsFinalizerDependency(HasFinalizers finalizers, NodeGroup current) {
        if (current == finalizers || current == NodeGroup.DEFAULT_GROUP) {
            return finalizers;
        }

        if (current instanceof OrdinalGroup) {
            return new CompositeNodeGroup(current, finalizers.getFinalizerGroups());
        }

        HasFinalizers currentFinalizers = (HasFinalizers) current;
        if (currentFinalizers.isReachableFromEntryPoint() == finalizers.isReachableFromEntryPoint() && currentFinalizers.getFinalizerGroups().containsAll(finalizers.getFinalizerGroups())) {
            return currentFinalizers;
        }

        ImmutableSet.Builder<FinalizerGroup> builder = ImmutableSet.builder();
        builder.addAll(currentFinalizers.getFinalizerGroups());
        builder.addAll(finalizers.getFinalizerGroups());
        return new CompositeNodeGroup(currentFinalizers.getOrdinalGroup(), builder.build());
    }

    public void maybeUpdateOrdinalGroup() {
        OrdinalGroup ordinal = getGroup().asOrdinal();
        OrdinalGroup newOrdinal = ordinal;
        for (Node successor : getHardSuccessors()) {
            OrdinalGroup successorOrdinal = successor.getGroup().asOrdinal();
            if (successorOrdinal != null && (ordinal == null || successorOrdinal.getOrdinal() > ordinal.getOrdinal())) {
                newOrdinal = successorOrdinal;
            }
        }
        if (newOrdinal != ordinal) {
            setGroup(getGroup().withOrdinalGroup(newOrdinal));
        }
    }

    @Nullable
    public FinalizerGroup getFinalizerGroup() {
        return group.asFinalizer();
    }

    public boolean isRequired() {
        return state == ExecutionState.SHOULD_RUN;
    }

    public boolean isDoNotIncludeInPlan() {
        return filtered || state == ExecutionState.NOT_SCHEDULED || isCannotRunInAnyPlan();
    }

    public boolean isCannotRunInAnyPlan() {
        return state == ExecutionState.EXECUTED || state == ExecutionState.FAILED_DEPENDENCY;
    }

    /**
     * Is this node ready to execute? Note: does not consider the dependencies of the node.
     */
    public boolean isReady() {
        return state == ExecutionState.SHOULD_RUN;
    }

    public boolean isCanCancel() {
        return true;
    }

    public boolean isInKnownState() {
        return state != ExecutionState.NOT_SCHEDULED;
    }

    public boolean isExecuting() {
        return state == ExecutionState.EXECUTING;
    }

    /**
     * Is it possible for this node to run in the current plan? Returns {@code true} if this node definitely will not run, {@code false} if it is still possible for the node to run.
     *
     * <p>A node may be complete for several reasons, for example:</p>
     * <ul>
     *     <li>when its actions have been executed, or when its outputs have been considered up-to-date or loaded from the build cache</li>
     *     <li>when it cannot run due to a failure in a dependency</li>
     *     <li>when it is cancelled due to a failure in some other node and not running with --continue</li>
     *     <li>when it is a finalizer of tasks that have all completed but did not run</li>
     * </ul>
     */
    public boolean isComplete() {
        return state == ExecutionState.EXECUTED
            || state == ExecutionState.FAILED_DEPENDENCY
            || state == ExecutionState.NOT_SCHEDULED
            || filtered;
    }

    public boolean isSuccessful() {
        return filtered || (state == ExecutionState.EXECUTED && !isFailed());
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
     * Returns true when this node should be executed as soon as its dependencies are ready, rather than at its default point in
     * the execution plan. Does not affect the dependencies of this node.
     *
     * <p>Use sparingly, and only for fast work that requires access to some project or other resource.</p>
     */
    public boolean isPriority() {
        return false;
    }

    /**
     * Returns any error that happened during the execution of the node itself,
     * i.e. a task action has thrown an exception.
     */
    @Nullable
    public abstract Throwable getNodeFailure();

    public void startExecution(Consumer<Node> nodeStartAction) {
        assert allDependenciesComplete() && allDependenciesSuccessful();
        state = ExecutionState.EXECUTING;
        nodeStartAction.accept(this);
    }

    public void finishExecution(Consumer<Node> completionAction) {
        assert state == ExecutionState.EXECUTING;
        state = ExecutionState.EXECUTED;
        completionAction.accept(this);
    }

    public void markFailedDueToDependencies(Consumer<Node> completionAction) {
        assert state == ExecutionState.SHOULD_RUN;
        state = ExecutionState.FAILED_DEPENDENCY;
        completionAction.accept(this);
    }

    public void cancelExecution(Consumer<Node> completionAction) {
        if (isCannotRunInAnyPlan()) {
            throw new IllegalStateException("Cannot cancel node " + this);
        }
        state = ExecutionState.NOT_SCHEDULED;
        completionAction.accept(this);
    }

    public void require() {
        if (isCannotRunInAnyPlan()) {
            return;
        }
        if (state != ExecutionState.SHOULD_RUN) {
            // When the state changes to `SHOULD_RUN`, the dependencies need to be reprocessed since they also may be required now.
            dependenciesProcessed = false;
            state = ExecutionState.SHOULD_RUN;
        }
    }

    /**
     * Mark this node as filtered from the current plan. The node will be considered complete and successful.
     */
    public void filtered() {
        if (isCannotRunInAnyPlan()) {
            return;
        }
        filtered = true;
    }

    /**
     * Discards any plan specific state for this node, so that it can potentially be added to another execution plan.
     */
    public void reset() {
        group = NodeGroup.DEFAULT_GROUP;
        if (!isCannotRunInAnyPlan()) {
            filtered = false;
            dependenciesProcessed = false;
            state = ExecutionState.NOT_SCHEDULED;
            dependenciesState = DependenciesState.NOT_COMPLETE;
        }
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

    public Iterable<Node> getDependencySuccessorsInReverseOrder() {
        return dependencySuccessors.descendingSet();
    }

    public void addDependencySuccessor(Node toNode) {
        dependencySuccessors.add(toNode);
        toNode.getDependencyPredecessors().add(this);
    }

    @OverridingMethodsMustInvokeSuper
    protected DependenciesState doCheckDependenciesComplete() {
        for (Node dependency : dependencySuccessors) {
            if (!dependency.isComplete()) {
                return DependenciesState.NOT_COMPLETE;
            } else if (!shouldContinueExecution(dependency)) {
                return DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL;
            }
        }
        // All dependencies are complete and successful, delegate to the group
        return group.checkSuccessorsCompleteFor(this);
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
        dependenciesState = doCheckDependenciesComplete();
    }

    /**
     * Is this node ready to execute or discard (eg because a dependency has failed)?
     */
    public boolean allDependenciesComplete() {
        return state == ExecutionState.SHOULD_RUN && dependenciesState != DependenciesState.NOT_COMPLETE;
    }

    /**
     * Can this node execute or should it be discarded? Should only be called when {@link #allDependenciesComplete()} returns true.
     */
    public boolean allDependenciesSuccessful() {
        return dependenciesState == DependenciesState.COMPLETE_AND_SUCCESSFUL;
    }

    /**
     * Should this node be cancelled or marked as failed? Should only be called when {@link #allDependenciesSuccessful()} returns false.
     */
    public boolean shouldCancelExecutionDueToDependencies() {
        return dependenciesState == DependenciesState.COMPLETE_AND_CAN_SKIP;
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

    /**
     * Visits all nodes whose {@link #allDependenciesComplete()} state depends in some way on the completion of this node.
     * Should visit the nodes in a deterministic order, but the order can be whatever best makes sense for the node implementation.
     */
    protected void visitAllNodesWaitingForThisNode(Consumer<Node> visitor) {
        for (Node node : getDependencyPredecessors()) {
            visitor.accept(node);
        }
    }

    /**
     * Called when this node is added to the work graph, prior to resolving its dependencies.
     *
     * @param monitor An action that should be called when this node is ready to execute, when the dependencies for this node are executed outside
     * the work graph that contains this node (for example, when the node represents a task in an included build).
     */
    public void prepareForExecution(Action<Node> monitor) {
    }

    public abstract void resolveDependencies(TaskDependencyResolver dependencyResolver);

    public boolean getDependenciesProcessed() {
        return dependenciesProcessed;
    }

    public void dependenciesProcessed() {
        dependenciesProcessed = true;
    }

    @OverridingMethodsMustInvokeSuper
    public Iterable<Node> getAllSuccessors() {
        return getHardSuccessors();
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

    public Set<Node> getFinalizers() {
        return Collections.emptySet();
    }

    public void addFinalizer(Node finalizer) {
    }

    public Set<Node> getFinalizingSuccessors() {
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
    public ResourceLock getProjectToLock() {
        return null;
    }

    /**
     * Returns the project which this node belongs to, and requires access to the execution services of.
     * Returning non-null does not imply that the project must be locked when this node executes. Use {@link #getProjectToLock()} instead for that.
     *
     * TODO - this should return some kind of abstract 'action context' instead of a mutable project.
     */
    @Nullable
    public ProjectInternal getOwningProject() {
        return null;
    }

    /**
     * Returns the resources which should be locked before starting this node.
     */
    public List<? extends ResourceLock> getResourcesToLock() {
        return Collections.emptyList();
    }

    @Override
    public abstract String toString();

}
