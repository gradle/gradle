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

import org.gradle.execution.plan.Node;

import java.util.NavigableSet;

import static org.gradle.execution.plan.NodeSets.newSortedNodeSet;

public class ComplexDependencyNodesSet implements DependencyNodesSet {
    private final DependencySuccessorsOnlyNodeSet delegate;
    private final NavigableSet<Node> orderedMustSuccessors = newSortedNodeSet();

    public ComplexDependencyNodesSet(DependencySuccessorsOnlyNodeSet delegate) {
        this.delegate = delegate;
    }

    @Override
    public NavigableSet<Node> getDependencySuccessors() {
        return delegate.getDependencySuccessors();
    }

    @Override
    public DependencyNodesSet addDependency(Node toNode) {
        delegate.addDependency(toNode);
        return this;
    }

    @Override
    public NavigableSet<Node> getMustSuccessors() {
        return orderedMustSuccessors;
    }

    @Override
    public DependencyNodesSet addMustSuccessor(Node toNode) {
        orderedMustSuccessors.add(toNode);
        return this;
    }

    @Override
    public void onNodeComplete(Node node, Node dependency) {
        delegate.onNodeComplete(node, dependency);
    }

    @Override
    public Node.DependenciesState getState(Node node) {
        Node.DependenciesState state = delegate.getState(node);
        if (state != Node.DependenciesState.COMPLETE_AND_SUCCESSFUL) {
            return state;
        }

        for (Node dependency : orderedMustSuccessors) {
            if (!dependency.isComplete()) {
                return Node.DependenciesState.NOT_COMPLETE;
            }
        }

        return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
    }

    @Override
    public void healthDiagnostics(StringBuilder builder) {
        delegate.healthDiagnostics(builder);
        if (!orderedMustSuccessors.isEmpty()) {
            builder.append(", must-run-after=").append(Node.formatNodes(orderedMustSuccessors));
        }
    }
}
