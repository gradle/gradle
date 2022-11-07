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

import java.util.NavigableSet;

public interface DependencyNodesSet {
    DependencyNodesSet EMPTY = new DependencyNodesSet() {
        @Override
        public NavigableSet<Node> getDependencySuccessors() {
            return ImmutableSortedSet.of();
        }

        @Override
        public DependencyNodesSet addDependency(Node toNode) {
            return new DependencySuccessorsOnlyNodeSet().addDependency(toNode);
        }

        @Override
        public NavigableSet<Node> getMustSuccessors() {
            return ImmutableSortedSet.of();
        }

        @Override
        public DependencyNodesSet addMustSuccessor(Node toNode) {
            return new DependencySuccessorsOnlyNodeSet().addMustSuccessor(toNode);
        }

        @Override
        public void onNodeComplete(Node node, Node dependency) {
        }

        @Override
        public Node.DependenciesState getState(Node node) {
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }

        @Override
        public void healthDiagnostics(StringBuilder builder) {
            builder.append("no dependencies");
        }
    };

    NavigableSet<Node> getDependencySuccessors();

    DependencyNodesSet addDependency(Node toNode);

    NavigableSet<Node> getMustSuccessors();

    DependencyNodesSet addMustSuccessor(Node toNode);

    void onNodeComplete(Node node, Node dependency);

    Node.DependenciesState getState(Node node);

    void healthDiagnostics(StringBuilder builder);
}
