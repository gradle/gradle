/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.graph;

import org.gradle.util.GUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A graph walker which collects the values reachable from a given set of start nodes. Handles cycles in the graph. Can
 * be reused to perform multiple searches, and reuses the results of previous searches.
 *
 * Uses a variation of Tarjan's algorithm: http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 */
public class CachingDirectedGraphWalker<N, T> {
    private final DirectedGraphWithEdgeValues<N, T> graph;
    private List<N> startNodes = new ArrayList<N>();
    private Set<NodeDetails<N, T>> strongComponents = new LinkedHashSet<NodeDetails<N, T>>();
    private final Map<N, Set<T>> cachedNodeValues = new HashMap<N, Set<T>>();

    public CachingDirectedGraphWalker(DirectedGraph<N, T> graph) {
        this.graph = new GraphWithEmptyEdges<N, T>(graph);
    }

    public CachingDirectedGraphWalker(DirectedGraphWithEdgeValues<N, T> graph) {
        this.graph = graph;
    }

    /**
     * Adds some start nodes.
     */
    public CachingDirectedGraphWalker<N, T> add(N... values) {
        add(Arrays.asList(values));
        return this;
    }

    /**
     * Adds some start nodes.
     */
    public CachingDirectedGraphWalker add(Iterable<? extends N> values) {
        GUtil.addToCollection(startNodes, values);
        return this;
    }

    /**
     * Calculates the set of values of nodes reachable from the start nodes.
     */
    public Set<T> findValues() {
        try {
            return doSearch();
        } finally {
            startNodes.clear();
        }
    }

    /**
     * Returns the set of cycles seen in the graph.
     */
    public List<Set<N>> findCycles() {
        findValues();
        List<Set<N>> result = new ArrayList<Set<N>>();
        for (NodeDetails<N, T> nodeDetails : strongComponents) {
            Set<N> componentMembers = new LinkedHashSet<N>();
            for (NodeDetails<N, T> componentMember : nodeDetails.componentMembers) {
                componentMembers.add(componentMember.node);
            }
            result.add(componentMembers);
        }
        return result;
    }

    private Set<T> doSearch() {
        int componentCount = 0;
        Map<N, NodeDetails<N, T>> seenNodes = new HashMap<N, NodeDetails<N, T>>();
        Map<Integer, NodeDetails<N, T>> components = new HashMap<Integer, NodeDetails<N, T>>();
        Deque<N> queue = new ArrayDeque<N>(startNodes);

        while (!queue.isEmpty()) {
            N node = queue.getFirst();
            NodeDetails<N, T> details = seenNodes.get(node);
            if (details == null) {
                // Have not visited this node yet. Push its successors onto the queue in front of this node and visit
                // them

                details = new NodeDetails<N, T>(node, componentCount++);
                seenNodes.put(node, details);
                components.put(details.component, details);

                Set<T> cacheValues = cachedNodeValues.get(node);
                if (cacheValues != null) {
                    // Already visited this node
                    details.values = cacheValues;
                    details.finished = true;
                    queue.removeFirst();
                    continue;
                }

                graph.getNodeValues(node, details.values, details.successors);
                for (N connectedNode : details.successors) {
                    NodeDetails<N, T> connectedNodeDetails = seenNodes.get(connectedNode);
                    if (connectedNodeDetails == null) {
                        // Have not visited the successor node, so add to the queue for visiting
                        queue.addFirst(connectedNode);
                    } else if (!connectedNodeDetails.finished) {
                        // Currently visiting the successor node - we're in a cycle
                        details.stronglyConnected = true;
                    }
                    // Else, already visited
                }
            } else {
                // Have visited all of this node's successors
                queue.removeFirst();

                if (cachedNodeValues.containsKey(node)) {
                    continue;
                }

                for (N connectedNode : details.successors) {
                    NodeDetails<N, T> connectedNodeDetails = seenNodes.get(connectedNode);
                    if (!connectedNodeDetails.finished) {
                        // part of a cycle : use the 'minimum' component as the root of the cycle
                        int minSeen = Math.min(details.minSeen, connectedNodeDetails.minSeen);
                        details.minSeen = minSeen;
                        connectedNodeDetails.minSeen = minSeen;
                        details.stronglyConnected = true;
                    }
                    details.values.addAll(connectedNodeDetails.values);
                    graph.getEdgeValues(node, connectedNode, details.values);
                }

                if (details.minSeen != details.component) {
                    // Part of a strongly connected component (ie cycle) - move values to root of the component
                    // The root is the first node of the component we encountered
                    NodeDetails<N, T> rootDetails = components.get(details.minSeen);
                    rootDetails.values.addAll(details.values);
                    details.values.clear();
                    rootDetails.componentMembers.addAll(details.componentMembers);
                } else {
                    // Not part of a strongly connected component or the root of a strongly connected component
                    for (NodeDetails<N, T> componentMember : details.componentMembers) {
                        cachedNodeValues.put(componentMember.node, details.values);
                        componentMember.finished = true;
                        components.remove(componentMember.component);
                    }
                    if (details.stronglyConnected) {
                        strongComponents.add(details);
                    }
                }
            }
        }

        Set<T> values = new LinkedHashSet<T>();
        for (N startNode : startNodes) {
            values.addAll(cachedNodeValues.get(startNode));
        }
        return values;
    }

    private static class NodeDetails<N, T> {
        private final int component;
        private final N node;
        private Set<T> values = new LinkedHashSet<T>();
        private List<N> successors = new ArrayList<N>();
        private Set<NodeDetails<N, T>> componentMembers = new LinkedHashSet<NodeDetails<N, T>>();
        private int minSeen;
        private boolean stronglyConnected;
        private boolean finished;

        public NodeDetails(N node, int component) {
            this.node = node;
            this.component = component;
            minSeen = component;
            componentMembers.add(this);
        }
    }

    private static class GraphWithEmptyEdges<N, T> implements DirectedGraphWithEdgeValues<N, T> {
        private final DirectedGraph<N, T> graph;

        public GraphWithEmptyEdges(DirectedGraph<N, T> graph) {
            this.graph = graph;
        }

        @Override
        public void getEdgeValues(N from, N to, Collection<T> values) {
        }

        @Override
        public void getNodeValues(N node, Collection<? super T> values, Collection<? super N> connectedNodes) {
            graph.getNodeValues(node, values, connectedNodes);
        }
    }
}
