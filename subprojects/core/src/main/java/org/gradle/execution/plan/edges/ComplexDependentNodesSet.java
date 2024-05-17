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
import org.gradle.execution.plan.NodeSets;

import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;

class ComplexDependentNodesSet implements DependentNodesSet {
    private final DependencyPredecessorsOnlyNodeSet dependencyPredecessors;
    private final SortedSet<Node> mustPredecessors = NodeSets.newSortedNodeSet();
    private final SortedSet<Node> finalizers = NodeSets.newSortedNodeSet();

    public ComplexDependentNodesSet(DependencyPredecessorsOnlyNodeSet dependencyPredecessors) {
        this.dependencyPredecessors = dependencyPredecessors;
    }

    @Override
    public SortedSet<Node> getDependencyPredecessors() {
        return dependencyPredecessors.getDependencyPredecessors();
    }

    @Override
    public DependentNodesSet addDependencyPredecessors(Node fromNode) {
        dependencyPredecessors.addDependencyPredecessors(fromNode);
        return this;
    }

    @Override
    public SortedSet<Node> getFinalizers() {
        return finalizers;
    }

    @Override
    public DependentNodesSet addFinalizer(Node finalizer) {
        finalizers.add(finalizer);
        return this;
    }

    @Override
    public Set<Node> getMustPredecessors() {
        return mustPredecessors;
    }

    @Override
    public DependentNodesSet addMustPredecessor(Node fromNode) {
        mustPredecessors.add(fromNode);
        return this;
    }

    @Override
    public void visitAllNodes(Consumer<Node> visitor) {
        dependencyPredecessors.visitAllNodes(visitor);
        for (Node node : mustPredecessors) {
            visitor.accept(node);
        }
        for (Node node : finalizers) {
            node.getFinalizerGroup().visitAllMembers(visitor);
        }
    }
}
