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

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;

/**
 * Tracks the nodes that have a hard dependency on a given node.
 */
public interface DependentNodesSet {
    DependentNodesSet EMPTY = new DependentNodesSet() {
        @Override
        public DependentNodesSet addDependencyPredecessors(Node fromNode) {
            return new DependencyPredecessorsOnlyNodeSet().addDependencyPredecessors(fromNode);
        }

        @Override
        public DependentNodesSet addFinalizer(Node finalizer) {
            return new ComplexDependentNodesSet(new DependencyPredecessorsOnlyNodeSet()).addFinalizer(finalizer);
        }

        @Override
        public DependentNodesSet addMustPredecessor(Node fromNode) {
            return new DependencyPredecessorsOnlyNodeSet().addMustPredecessor(fromNode);
        }

        @Override
        public void visitAllNodes(Consumer<Node> visitor) {
        }
    };

    /**
     * The dependency predecessors, in order.
     */
    default SortedSet<Node> getDependencyPredecessors() {
        return ImmutableSortedSet.of();
    }

    DependentNodesSet addDependencyPredecessors(Node fromNode);

    default SortedSet<Node> getFinalizers() {
        return ImmutableSortedSet.of();
    }

    DependentNodesSet addFinalizer(Node finalizer);

    default Set<Node> getMustPredecessors() {
        return Collections.emptySet();
    }

    DependentNodesSet addMustPredecessor(Node fromNode);

    /**
     * Visits all nodes in this set.
     * Should visit the nodes in a deterministic order, but the order can be whatever best makes sense for the node implementation.
     */
    void visitAllNodes(Consumer<Node> visitor);
}
