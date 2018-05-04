/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public class Graph {
    private static final Logger LOGGER = LoggerFactory.getLogger(Graph.class);
    private static final Comparator<? super Edge> EDGE_TYPE_PRECEDENCE = new Comparator<Edge>() {
        @Override
        public int compare(Edge edge1, Edge edge2) {
            return edge1.getType().compareTo(edge2.getType());
        }
    };

    private final SetMultimap<Node, Edge> incomingEdges = LinkedHashMultimap.create();
    private final SetMultimap<Node, Edge> outgoingEdges = LinkedHashMultimap.create();
    private final Set<Node> rootNodes = Sets.newLinkedHashSet();

    public boolean hasNodes() {
        return !rootNodes.isEmpty() || !incomingEdges.isEmpty();
    }

    @VisibleForTesting
    List<Node> getAllNodes() {
        return ImmutableList.copyOf(Iterables.concat(rootNodes, incomingEdges.keySet()));
    }

    @VisibleForTesting
    List<Edge> getAllEdges() {
        return ImmutableList.copyOf(incomingEdges.values());
    }

    public Iterable<Node> getRootNodes() {
        return ImmutableList.copyOf(rootNodes);
    }

    public void addNode(Node node) {
        if (rootNodes.contains(node) || incomingEdges.containsKey(node)) {
            throw new IllegalArgumentException("Node is already present in graph: " + node);
        }
        rootNodes.add(node);
    }

    public void processOutgoingEdges(Node node, EdgeAction action) {
        Set<Edge> outgoingFromNode = outgoingEdges.get(node);
        if (!outgoingFromNode.isEmpty()) {
            for (Edge outgoing : Lists.newArrayList(outgoingFromNode)) {
                if (action.process(outgoing) == EdgeActionResult.REMOVE) {
                    removeEdge(outgoing);
                }
            }
        }
    }

    public void removeNodeWithOutgoingEdges(Node node, Action<? super Edge> removalAction) {
        if (rootNodes.contains(node)) {
            Set<Edge> outgoing = outgoingEdges.get(node);
            if (!outgoing.isEmpty()) {
                for (Edge edge : Lists.newArrayList(outgoing)) {
                    removeEdge(edge);
                    removalAction.execute(edge);
                }
            }
            rootNodes.remove(node);
        } else if (incomingEdges.containsKey(node)) {
            throw new IllegalStateException("Node to be removed has incoming edges: " + node);
        } else {
            throw new IllegalArgumentException("Node is not present in the graph: " + node);
        }
    }

    public void breakCycles(GraphCycleReporter cycleReporter) {
        Set<Node> visitedNodes = Sets.newHashSet();
        Set<Node> path = Sets.newLinkedHashSet();
        Deque<Edge> removableEdges = new ArrayDeque<Edge>();

        Set<Node> nonRootNodes = incomingEdges.keySet();
        for (Node node : Lists.newArrayList(nonRootNodes)) {
            breakCycles(node, path, removableEdges, visitedNodes, cycleReporter);
        }
    }

    private void breakCycles(Node node, final Set<Node> path, Deque<Edge> softOrderingEdges, Set<Node> visitedNodes, GraphCycleReporter cycleReporter) {
        if (!path.add(node)) {
            // We have a cycle
            if (softOrderingEdges.isEmpty()) {
                Deque<Node> nodes = new ArrayDeque<Node>(path);
                nodes.addFirst(nodes.removeLast());
                throw cycleReporter.throwException(Lists.reverse(ImmutableList.copyOf(nodes)));
            } else {
                // TODO ignore the part of the path from here on that is before the removed edge's target node
                Edge edgeToRemove = softOrderingEdges.getLast();
                LOGGER.debug("Removing edge {}", edgeToRemove);
                removeEdge(edgeToRemove);
            }
        }
        try {
            if (!visitedNodes.add(node)) {
                return;
            }

            Set<Node> visitedFromHere = Sets.newHashSet();
            Set<Edge> incomingEdgesToNode = incomingEdges.get(node);
            if (incomingEdgesToNode.isEmpty()) {
                return;
            }

            List<Edge> incomingEdgesToNodeSorted = Lists.newArrayList(incomingEdgesToNode);
            Collections.sort(incomingEdgesToNodeSorted, EDGE_TYPE_PRECEDENCE);

            for (Edge incomingEdge : incomingEdgesToNodeSorted) {
                Node source = incomingEdge.getSource();
                if (visitedFromHere.add(source)) {
                    boolean removableToBreakCycles = incomingEdge.getType().isRemovableToBreakCycles();
                    if (removableToBreakCycles) {
                        softOrderingEdges.push(incomingEdge);
                    }
                    LOGGER.debug("Checking edge {} for cycles", incomingEdge);
                    breakCycles(source, path, softOrderingEdges, visitedNodes, cycleReporter);
                    if (removableToBreakCycles) {
                        softOrderingEdges.pop();
                    }
                }
            }
        } finally {
            path.remove(node);
        }
    }

    public void addEdge(Edge edge) {
        Node source = edge.getSource();
        Node target = edge.getTarget();
        boolean targetWasRootNode = rootNodes.contains(target);
        if (!targetWasRootNode && !incomingEdges.containsKey(target)) {
            throw new IllegalArgumentException("Target node for edge to be added is not present in graph: " + edge);
        }
        if (!rootNodes.contains(source) && !incomingEdges.containsKey(source)) {
            throw new IllegalArgumentException("Source node for edge to be added is not present in graph: " + edge);
        }
        if (incomingEdges.containsEntry(target, edge)) {
            throw new IllegalArgumentException("Edge already present in graph: " + edge);
        }
        incomingEdges.put(target, edge);
        outgoingEdges.put(source, edge);
        if (targetWasRootNode) {
            rootNodes.remove(target);
        }
    }

    private void removeEdge(Edge edge) {
        Node source = edge.getSource();
        Node target = edge.getTarget();
        if (!incomingEdges.remove(target, edge)) {
            throw new IllegalArgumentException("Edge not part of the graph: " + edge);
        }
        if (!outgoingEdges.remove(source, edge)) {
            throw new AssertionError("Edge was present in incoming edges but not in outgoing edges: " + edge);
        }
        if (!incomingEdges.containsKey(target)) {
            rootNodes.add(target);
        }
    }

    public interface EdgeAction {
        /**
         * Processes the given edge and returns whether or not to keep it.
         */
        EdgeActionResult process(Edge edge);
    }

    public enum EdgeActionResult {
        KEEP, REMOVE
    }
}
