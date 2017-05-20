/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

/**
 * Receives the result of dependency graph resolution, as a series of events.
 *
 * Implementations should make copies of whatever state they need to retain.
 */
public interface DependencyGraphVisitor {
    /**
     * Starts traversal of the graph.
     */
    void start(DependencyGraphNode root);

    /**
     * Visits a node of the graph. Includes the root. This method is called for all nodes before {@link #visitEdges(DependencyGraphNode)} is called.
     */
    void visitNode(DependencyGraphNode node);

    /**
     * Visits a selector. This method is called for all selectors before {@link #visitEdges(DependencyGraphNode)} is called.
     */
    void visitSelector(DependencyGraphSelector selector);

    /**
     * Visits edges to/from a node of the graph. Includes the root. This method is called for all nodes after {@link #visitNode(DependencyGraphNode)} has been called for all nodes.
     * Nodes are visited in consumer-first order.
     */
    void visitEdges(DependencyGraphNode node);

    /**
     * Completes traversal of the graph.
     */
    void finish(DependencyGraphNode root);

    DependencyGraphVisitor NO_OP = new DependencyGraphVisitor() {
        @Override
        public void start(DependencyGraphNode root) {
        }

        @Override
        public void visitNode(DependencyGraphNode node) {
        }

        @Override
        public void visitSelector(DependencyGraphSelector selector) {
        }

        @Override
        public void visitEdges(DependencyGraphNode node) {
        }

        @Override
        public void finish(DependencyGraphNode root) {
        }
    };
}
