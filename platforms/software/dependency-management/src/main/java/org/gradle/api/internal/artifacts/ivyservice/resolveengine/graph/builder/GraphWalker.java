/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class GraphWalker<N, E> {

    private final Graph<N, E> graph;

    public interface GraphVisitor<N> {
        void preVisit(N node);
        void postVisit(N node);
    }

    public interface Graph<N, E> {
        List<E> getEdges(N node);
        @Nullable N getNode(E e);
    }

    public GraphWalker(Graph<N, E> graph) {
        this.graph = graph;
    }

    public void visitRecursive(N root, GraphVisitor<N> visitor) {
        Set<N> seen = new HashSet<>();
        seen.add(root);
        visitor.preVisit(root);
        visitRecursiveHelper(root, visitor, seen);
    }

    private void visitRecursiveHelper(N node, GraphVisitor<N> visitor, Set<N> seen) {
        for (E edge : graph.getEdges(node)) {
            N child = graph.getNode(edge);
            if (child != null && seen.add(child)) {
                visitor.preVisit(child);
                visitRecursiveHelper(child, visitor, seen);
            }
        }

        visitor.postVisit(node);
    }

    private class StackFrame {
        final N node;
        final Iterator<E> edgeIterator;

        public StackFrame(N node) {
            this.node = node;
            this.edgeIterator = graph.getEdges(node).iterator();
        }
    }

    public void visitIterativeStackFrame(N root, GraphVisitor<N> visitor) {
        Deque<StackFrame> stack = new ArrayDeque<>();
        Set<N> seen = new HashSet<>();

        seen.add(root);
        visitor.preVisit(root);
        stack.push(new StackFrame(root));

        while (!stack.isEmpty()) {
            StackFrame currentFrame = stack.peek();
            Iterator<E> currentEdgeIterator = currentFrame.edgeIterator;

            if (currentEdgeIterator.hasNext()) {
                N child = graph.getNode(currentEdgeIterator.next());
                if (child != null && seen.add(child)) {
                    visitor.preVisit(child);
                    stack.push(new StackFrame(child));
                }
            } else {
                StackFrame processedFrame = stack.pop();
                visitor.postVisit(processedFrame.node);
            }
        }
    }

    private class StackFrame2 {
        final N node;
        int edgeIndex;

        public StackFrame2(N node) {
            this.node = node;
            this.edgeIndex = 0;
        }
    }

    public void visitIterativeStackFrame2(N root, GraphVisitor<N> visitor) {
        Deque<StackFrame2> stack = new ArrayDeque<>();
        Set<N> seen = new HashSet<>();

        seen.add(root);
        visitor.preVisit(root);
        stack.push(new StackFrame2(root));

        while (!stack.isEmpty()) {
            StackFrame2 currentFrame = stack.peek();

            boolean foundChild = false;
            List<E> edges = graph.getEdges(currentFrame.node);
            for (int i = currentFrame.edgeIndex; i < edges.size(); i++) {
                E edge = edges.get(i);
                N child = graph.getNode(edge);
                if (child != null && seen.add(child)) {
                    visitor.preVisit(child);
                    stack.push(new StackFrame2(child));
                    foundChild = true;
                    currentFrame.edgeIndex = i + 1; // Update index for next iteration
                    break;
                }
            }

            if (!foundChild) {
                StackFrame2 processedFrame = stack.pop();
                visitor.postVisit(processedFrame.node);
            }
        }
    }

    private class StackFrame3 {
        final N node;
        int edgeIndex;
        final List<E> edges;

        public StackFrame3(N node) {
            this.node = node;
            this.edges = graph.getEdges(node);
            this.edgeIndex = 0;
        }
    }

    public void visitIterativeStackFrame3(N root, GraphVisitor<N> visitor) {
        Deque<StackFrame3> stack = new ArrayDeque<>();
        Set<N> seen = new HashSet<>();

        seen.add(root);
        visitor.preVisit(root);
        stack.push(new StackFrame3(root));

        while (!stack.isEmpty()) {
            StackFrame3 currentFrame = stack.peek();

            boolean foundChild = false;
            List<E> edges = currentFrame.edges;
            for (int i = currentFrame.edgeIndex; i < edges.size(); i++) {
                E edge = edges.get(i);
                N child = graph.getNode(edge);
                if (child != null && seen.add(child)) {
                    visitor.preVisit(child);
                    stack.push(new StackFrame3(child));
                    foundChild = true;
                    currentFrame.edgeIndex = i + 1; // Update index for next iteration
                    break;
                }
            }

            if (!foundChild) {
                StackFrame3 processedFrame = stack.pop();
                visitor.postVisit(processedFrame.node);
            }
        }
    }
}
