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

import java.util.*;

/**
 * Groups the nodes of a graph based on their reachability from a set of starting nodes.
 */
public class GraphAggregator<N> {
    private final DirectedGraph<N, N> graph;

    public GraphAggregator(DirectedGraph<N, ?> graph) {
        this.graph = new ConnectedNodesAsValuesDirectedGraph<N>(graph);
    }

    public Result<N> group(Iterable<? extends N> startNodes) {
        Map<N, Set<N>> nodes = new HashMap<N, Set<N>>();
        CachingDirectedGraphWalker<N, N> graphWalker = new CachingDirectedGraphWalker<N, N>(graph);
        for (N node : startNodes) {
            nodes.put(node, graphWalker.add(node).findValues());
        }
        for (N node : startNodes) {
            calculateReachableNodes(nodes, node);
        }
        return new Result<N>(nodes);
    }

    private Set<N> calculateReachableNodes(Map<N, Set<N>> nodes, N node) {
        Set<N> reachableNodes = nodes.get(node);
        Set<N> reachableStartNodes = new LinkedHashSet<N>(nodes.keySet());
        reachableStartNodes.retainAll(reachableNodes);
        reachableStartNodes.remove(node);
        for (N startNode : reachableStartNodes) {
            reachableNodes.removeAll(calculateReachableNodes(nodes, startNode));
        }
        return reachableNodes;
    }

    public static class Result<N> {
        private final Map<N, Set<N>> nodes;

        public Result(Map<N, Set<N>> nodes) {
            this.nodes = nodes;
        }

        public Set<N> getNodes(N startNode) {
            return nodes.get(startNode);
        }
    }

    private static class ConnectedNodesAsValuesDirectedGraph<N> implements DirectedGraph<N, N> {
        private final DirectedGraph<N, ?> graph;

        private ConnectedNodesAsValuesDirectedGraph(DirectedGraph<N, ?> graph) {
            this.graph = graph;
        }

        @Override
        public void getNodeValues(N node, Collection<N> values, Collection<N> connectedNodes) {
            Set<N> edges = new LinkedHashSet<N>();
            graph.getNodeValues(node, new ArrayList(), edges);
            values.add(node);
            values.addAll(edges);
            connectedNodes.addAll(edges);
        }

        @Override
        public void getEdgeValues(N from, N to, Collection<N> values) {
        }
    }
}
