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
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.NavigableSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.execution.plan.NodeSets.newSortedNodeSet;

public abstract class TaskNode extends Node {
    private final NavigableSet<Node> shouldSuccessors = newSortedNodeSet();
    private final NavigableSet<Node> finalizingSuccessors = newSortedNodeSet();

    @Override
    protected void nodeSpecificHealthDiagnostics(StringBuilder builder) {
        if (!finalizingSuccessors.isEmpty()) {
            builder.append(", finalizes=").append(formatNodes(finalizingSuccessors));
        }
    }

    public Set<Node> getMustSuccessors() {
        return getDependencyNodes().getMustSuccessors();
    }

    public abstract Set<Node> getLifecycleSuccessors();

    public abstract void setLifecycleSuccessors(Set<Node> successors);

    @Override
    public Set<Node> getFinalizingSuccessors() {
        return finalizingSuccessors;
    }

    public Set<Node> getShouldSuccessors() {
        return shouldSuccessors;
    }

    public void addMustSuccessor(Node toNode) {
        deprecateLifecycleHookReferencingNonLocalTask("mustRunAfter", toNode);
        updateDependencyNodes(getDependencyNodes().addMustSuccessor(toNode));
        toNode.addMustPredecessor(this);
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
            getMustSuccessors(),
            super.getAllSuccessors()
        );
    }

    @Override
    public Iterable<Node> getHardSuccessors() {
        return Iterables.concat(
            finalizingSuccessors,
            getMustSuccessors(),
            super.getHardSuccessors()
        );
    }

    @Override
    public void visitHardSuccessors(Consumer<? super Node> visitor) {
        finalizingSuccessors.forEach(visitor);
        getMustSuccessors().forEach(visitor);
        super.visitHardSuccessors(visitor);
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
    public void maybeInheritFinalizerGroups() {
        super.maybeInheritFinalizerGroups();
        if (!getFinalizingSuccessors().isEmpty()) {
            // This node is a finalizer, decorate the current group to add finalizer behaviour
            NodeGroup oldGroup = getGroup();
            FinalizerGroup finalizerGroup = new FinalizerGroup(this, oldGroup);
            setGroup(finalizerGroup);
        }
    }
}
