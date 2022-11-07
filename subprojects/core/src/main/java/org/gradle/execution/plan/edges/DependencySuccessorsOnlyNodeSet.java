/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan.edges;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.execution.plan.Node;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;

import static org.gradle.execution.plan.NodeSets.newSortedNodeSet;

/**
 * Maintains the state for the dependencies of a node.
 *
 * <p>Attempts to efficiently determine whether a node can start or not based on the state of its dependencies, by tracking those dependencies that are still to complete.</p>
 */
public class DependencySuccessorsOnlyNodeSet implements DependencyNodesSet {
    private final NavigableSet<Node> orderedDependencies = newSortedNodeSet();
    private Set<Node> waitingFor;
    private boolean nodeCannotStart;
    private boolean pruned;

    @Override
    public NavigableSet<Node> getDependencySuccessors() {
        return orderedDependencies;
    }

    @Override
    public DependencySuccessorsOnlyNodeSet addDependency(Node node) {
        orderedDependencies.add(node);
        if (waitingFor == null) {
            waitingFor = new HashSet<>();
        }
        // It would be better to discard dependencies that have already completed at this point, rather than collecting them and checking their state later
        // However, it is not always known whether a dependency will be scheduled or not when it is added here.
        // For example, the dependency may later be filtered from the graph and this set is never notified that it is complete
        // This lifecycle could be simplified and allow the dependencies to be discarded at this point
        pruned = false;
        waitingFor.add(node);
        return this;
    }

    @Override
    public NavigableSet<Node> getMustSuccessors() {
        return ImmutableSortedSet.of();
    }

    @Override
    public DependencyNodesSet addMustSuccessor(Node toNode) {
        return new ComplexDependencyNodesSet(this).addMustSuccessor(toNode);
    }

    /**
     * Notified when a node that is potentially a member of this set has completed.
     */
    public void onNodeComplete(Node node, Node dependency) {
        if (waitingFor != null) {
            if (waitingFor.remove(dependency)) {
                if (preventsNodeFromStarting(node, dependency)) {
                    nodeCannotStart = true;
                    waitingFor = null;
                }
            }
        }
    }

    public Node.DependenciesState getState(Node node) {
        if (!pruned) {
            // See the comment in addDependency() above
            discardCompletedNodes(node);
            pruned = true;
        }
        if (nodeCannotStart) {
            // A dependency did not complete successfully
            return Node.DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL;
        } else if (waitingFor == null || waitingFor.isEmpty()) {
            // No dependencies or all of them have completed successfully
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        } else {
            return Node.DependenciesState.NOT_COMPLETE;
        }
    }

    @Override
    public void healthDiagnostics(StringBuilder builder) {
        builder.append("dependencies=").append(Node.formatNodes(orderedDependencies));
        if (waitingFor != null && !waitingFor.isEmpty()) {
            builder.append(", waiting-for=").append(Node.formatNodes(waitingFor));
        }
        builder.append(", has-failed-dependency=").append(nodeCannotStart);
    }

    private void discardCompletedNodes(Node node) {
        if (waitingFor != null) {
            Iterator<Node> iterator = waitingFor.iterator();
            while (iterator.hasNext()) {
                Node dependency = iterator.next();
                if (dependency.isComplete()) {
                    iterator.remove();
                    if (preventsNodeFromStarting(node, dependency)) {
                        nodeCannotStart = true;
                        waitingFor = null;
                        break;
                    }
                }
            }
        }
    }

    private boolean preventsNodeFromStarting(Node node, Node dependency) {
        return !node.shouldContinueExecution(dependency);
    }
}
