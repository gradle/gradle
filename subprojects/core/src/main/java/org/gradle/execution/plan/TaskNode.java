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

package org.gradle.execution.plan;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class TaskNode extends Node {
    private final NavigableSet<Node> mustSuccessors = Sets.newTreeSet();
    private final Set<Node> mustPredecessors = Sets.newHashSet();
    private final NavigableSet<Node> shouldSuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> finalizers = Sets.newTreeSet();
    private final NavigableSet<Node> finalizingSuccessors = Sets.newTreeSet();

    @Override
    public DependenciesState doCheckDependenciesComplete() {
        DependenciesState state = super.doCheckDependenciesComplete();
        if (state != DependenciesState.COMPLETE_AND_SUCCESSFUL) {
            return state;
        }

        for (Node dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return DependenciesState.NOT_COMPLETE;
            }
        }

        return DependenciesState.COMPLETE_AND_SUCCESSFUL;
    }

    public Set<Node> getMustSuccessors() {
        return mustSuccessors;
    }

    public abstract Set<Node> getLifecycleSuccessors();

    public abstract void setLifecycleSuccessors(Set<Node> successors);

    @Override
    public Set<Node> getFinalizers() {
        return finalizers;
    }

    @Override
    public void addFinalizer(Node finalizer) {
        finalizers.add(finalizer);
    }

    @Override
    public Set<Node> getFinalizingSuccessors() {
        return finalizingSuccessors;
    }

    public Set<Node> getShouldSuccessors() {
        return shouldSuccessors;
    }

    public void addMustSuccessor(TaskNode toNode) {
        deprecateLifecycleHookReferencingNonLocalTask("mustRunAfter", toNode);
        mustSuccessors.add(toNode);
        toNode.mustPredecessors.add(this);
    }

    public void addFinalizingSuccessor(Node finalized) {
        finalizingSuccessors.add(finalized);
        finalized.addFinalizer(this);
    }

    public void addShouldSuccessor(Node toNode) {
        deprecateLifecycleHookReferencingNonLocalTask("shouldRunAfter", toNode);
        shouldSuccessors.add(toNode);
    }

    public void removeShouldSuccessor(TaskNode toNode) {
        shouldSuccessors.remove(toNode);
    }

    @Override
    public Iterable<Node> getAllSuccessors() {
        return Iterables.concat(
            shouldSuccessors,
            getGroup().getSuccessorsFor(this),
            mustSuccessors,
            super.getAllSuccessors()
        );
    }

    @Override
    public Iterable<Node> getHardSuccessors() {
        return Iterables.concat(
            getGroup().getSuccessorsFor(this),
            mustSuccessors,
            super.getHardSuccessors()
        );
    }

    @Override
    public Iterable<Node> getAllSuccessorsInReverseOrder() {
        return Iterables.concat(
            super.getAllSuccessorsInReverseOrder(),
            mustSuccessors.descendingSet(),
            getGroup().getSuccessorsInReverseOrderFor(this),
            shouldSuccessors.descendingSet()
        );
    }

    @Override
    protected void visitAllNodesWaitingForThisNode(Consumer<Node> visitor) {
        super.visitAllNodesWaitingForThisNode(visitor);
        for (Node node : mustPredecessors) {
            visitor.accept(node);
        }
        for (Node node : finalizers) {
            node.getFinalizerGroup().visitAllMembers(visitor);
        }
    }

    public abstract TaskInternal getTask();

    protected void deprecateLifecycleHookReferencingNonLocalTask(String hookName, Node taskNode) {
        if (taskNode instanceof TaskInAnotherBuild) {
            DeprecationLogger.deprecateAction("Using " + hookName + " to reference tasks from another build")
                .willBecomeAnErrorInGradle8()
                .withUpgradeGuideSection(6, "referencing_tasks_from_included_builds")
                .nagUser();
        }
    }

    @Override
    public void updateGroupOfFinalizer() {
        super.updateGroupOfFinalizer();
        if (!getFinalizingSuccessors().isEmpty()) {
            // This node is a finalizer, decorate the current group to add finalizer behaviour
            NodeGroup oldGroup = getGroup();
            FinalizerGroup finalizerGroup = new FinalizerGroup(this, oldGroup);
            setGroup(finalizerGroup);
        }
    }
}
