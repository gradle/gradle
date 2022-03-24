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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.Set;

public abstract class TaskNode extends Node {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskNode.class);
    public static final int UNKNOWN_ORDINAL = -1;

    private final NavigableSet<Node> mustSuccessors = Sets.newTreeSet();
    private final Set<Node> mustPredecessors = Sets.newHashSet();
    private final NavigableSet<Node> shouldSuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> finalizers = Sets.newTreeSet();
    private final NavigableSet<Node> finalizingSuccessors = Sets.newTreeSet();
    private int ordinal = UNKNOWN_ORDINAL;

    @Override
    public boolean doCheckDependenciesComplete() {
        if (!super.doCheckDependenciesComplete()) {
            return false;
        }

        for (Node dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        for (Node finalized : finalizingSuccessors) {
            if (!finalized.isComplete()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean allDependenciesSuccessful() {
        if (!super.allDependenciesSuccessful()) {
            return false;
        }
        if (finalizingSuccessors.isEmpty()) {
            return true;
        }

        // If any finalized node has executed, then this node can execute
        for (Node finalized : finalizingSuccessors) {
            if (finalized.isExecuted()) {
                return true;
            }
        }
        return false;
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

    @Override
    public void addFinalizingSuccessors(Collection<Node> finalizingSuccessors) {
        this.finalizingSuccessors.addAll(finalizingSuccessors);
        for (Node finalized : finalizingSuccessors) {
            addFinalizingSuccessor(finalized);
        }
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
            finalizingSuccessors,
            mustSuccessors,
            super.getAllSuccessors()
        );
    }

    @Override
    public Iterable<Node> getHardSuccessors() {
        return Iterables.concat(
            finalizingSuccessors,
            mustSuccessors,
            super.getHardSuccessors()
        );
    }

    @Override
    public Iterable<Node> getAllSuccessorsInReverseOrder() {
        return Iterables.concat(
            super.getAllSuccessorsInReverseOrder(),
            mustSuccessors.descendingSet(),
            finalizingSuccessors.descendingSet(),
            shouldSuccessors.descendingSet()
        );
    }

    @Override
    public Iterable<Node> getAllPredecessors() {
        return Iterables.concat(mustPredecessors, finalizers, super.getAllPredecessors());
    }

    @Override
    public boolean hasHardSuccessor(Node successor) {
        if (super.hasHardSuccessor(successor)) {
            return true;
        }
        if (!(successor instanceof TaskNode)) {
            return false;
        }
        return getMustSuccessors().contains(successor)
            || getFinalizingSuccessors().contains(successor);
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

    public int getOrdinal() {
        return ordinal;
    }

    public void maybeSetOrdinal(int ordinal) {
        if (this.ordinal == UNKNOWN_ORDINAL || this.ordinal > ordinal) {
            this.ordinal = ordinal;
        }
    }

    public void maybeInheritOrdinalAsDependency(TaskNode node) {
        maybeSetOrdinal(node.getOrdinal());
    }

    public void maybeInheritOrdinalAsFinalizer(TaskNode node) {
        if (this.ordinal == UNKNOWN_ORDINAL || this.ordinal < node.getOrdinal()) {
            this.ordinal = node.getOrdinal();
        }
    }
}
