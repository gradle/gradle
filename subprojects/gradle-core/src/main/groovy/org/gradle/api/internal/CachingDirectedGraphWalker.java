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

package org.gradle.api.internal;

import org.gradle.util.GUtil;

import java.util.*;

/**
 * A graph walker which collects the values reachable from a given set of start nodes. Handles cycles in the graph. Can
 * be reused to perform multiple searches, and reuses the results of previous searches.
 *
 * Uses a variation on Tarjan's algorithm: http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 */
public class CachingDirectedGraphWalker<N, T> {
    private final DirectedGraph<N, T> graph;
    private List<N> startNodes = new LinkedList<N>();
    private final Map<N, Set<T>> cachedNodeValues = new HashMap<N, Set<T>>();

    public CachingDirectedGraphWalker(DirectedGraph<N, T> graph) {
        this.graph = graph;
    }

    public void add(N... values) {
        add(Arrays.asList(values));
    }
    
    public void add(Iterable<? extends N> values) {
        GUtil.addToCollection(startNodes, values);
    }

    public Set<T> findValues() {
        int componentCount = 0;
        Map<N, NodeDetails<N, T>> seenNodes = new HashMap<N, NodeDetails<N, T>>();
        Map<Integer, NodeDetails<N, T>> componentValues = new HashMap<Integer, NodeDetails<N,T>>();
        LinkedList<N> queue = new LinkedList<N>(startNodes);

        while (!queue.isEmpty()) {
            N node = queue.getFirst();
            NodeDetails<N, T> details = seenNodes.get(node);
            if (details == null) {
                // Have not visited this node yet. Push its successors onto the queue in front of this node and visit
                // them

                details = new NodeDetails<N, T>(componentCount++);
                seenNodes.put(node, details);

                Set<T> cacheValues = cachedNodeValues.get(node);
                if (cacheValues != null) {
                    // Already visited this node
                    details.values = cacheValues;
                    queue.removeFirst();
                    continue;
                }

                componentValues.put(details.component, details);

                graph.getNodeValues(node, details.values);
                graph.getConnectedNodes(node, details.successors);
                for (N connectedNode : details.successors) {
                    if (!seenNodes.containsKey(connectedNode)) {
                        queue.add(0, connectedNode);
                    }
                    // Else, already visiting the successor node, don't add it to the queue (we're in a cycle)
                }
            }
            else {
                // Have visited all of this node's successors
                queue.removeFirst();

                if (cachedNodeValues.containsKey(node)) {
                    continue;
                }

                for (N connectedNode : details.successors) {
                    NodeDetails<N, T> connectedNodeDetails = seenNodes.get(connectedNode);
                    if (connectedNodeDetails.component != connectedNodeDetails.minSeen) {
                        // A cycle
                        details.minSeen = Math.min(details.minSeen, connectedNodeDetails.minSeen);
                    }
                    details.values.addAll(connectedNodeDetails.values);
                    graph.getEdgeValues(node, connectedNode, details.values);
                }
                componentValues.remove(details.component);
                cachedNodeValues.put(node, details.values);
            }
        }

        Set<T> values = new LinkedHashSet<T>();
        for (N startNode : startNodes) {
            values.addAll(cachedNodeValues.get(startNode));
        }
        startNodes.clear();
        return values;
    }

    private static class NodeDetails<N, T> {
        private final int component;
        private Set<T> values = new LinkedHashSet<T>();
        private List<N> successors = new ArrayList<N>();
        private int minSeen;

        public NodeDetails(int component) {
            this.component = component;
            minSeen = component;
        }
    }
}
