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

import java.util.*;

/**
 * Groups the nodes of a graph based on their reachability from a set of starting nodes.
 */
public class GraphAggregator<N> {
    private final CachingDirectedGraphWalker<N, N> graphWalker;

    public GraphAggregator(DirectedGraph<N, ?> graph) {
        graphWalker = new CachingDirectedGraphWalker<N, N>(new ConnectedNodesAsValuesDirectedGraph<N>(graph));
    }

    public Result<N> group(Collection<? extends N> startNodes, Collection<? extends N> allNodes) {
        Map<N, Set<N>> reachableByNode = new HashMap<N, Set<N>>();
        Set<N> topLevelNodes = new LinkedHashSet<N>(allNodes);
        for (N node : allNodes) {
            Set<N> reachableNodes = graphWalker.add(node).findValues();
            reachableByNode.put(node, reachableNodes);
            topLevelNodes.removeAll(reachableNodes);
        }
        topLevelNodes.addAll(startNodes);
        Map<N, Set<N>> nodes = new HashMap<N, Set<N>>();
        for (N node : topLevelNodes) {
            nodes.put(node, calculateReachableNodes(reachableByNode, node, topLevelNodes));
        }
        return new Result<N>(nodes, topLevelNodes);
    }

    private Set<N> calculateReachableNodes(Map<N, Set<N>> nodes, N node, Set<N> topLevelNodes) {
        Set<N> reachableNodes = nodes.get(node);
        reachableNodes.add(node);
        Set<N> reachableStartNodes = new LinkedHashSet<N>(topLevelNodes);
        reachableStartNodes.retainAll(reachableNodes);
        reachableStartNodes.remove(node);
        for (N startNode : reachableStartNodes) {
            reachableNodes.removeAll(calculateReachableNodes(nodes, startNode, topLevelNodes));
        }
        return reachableNodes;
    }

    public static class Result<N> {
        private final Map<N, Set<N>> nodes;
        private final Set<N> topLevelNodes;

        public Result(Map<N, Set<N>> nodes, Set<N> topLevelNodes) {
            this.nodes = nodes;
            this.topLevelNodes = topLevelNodes;
        }

        public Set<N> getNodes(N startNode) {
            return nodes.get(startNode);
        }

        public Set<N> getTopLevelNodes() {
            return topLevelNodes;
        }
    }

    private static class ConnectedNodesAsValuesDirectedGraph<N> implements DirectedGraph<N, N> {
        private final DirectedGraph<N, ?> graph;

        private ConnectedNodesAsValuesDirectedGraph(DirectedGraph<N, ?> graph) {
            this.graph = graph;
        }

        public void getNodeValues(N node, Collection<? super N> values, Collection<? super N> connectedNodes) {
            Set<N> edges = new LinkedHashSet<N>();
            graph.getNodeValues(node, new ArrayList(), edges);
            values.addAll(edges);
            connectedNodes.addAll(edges);
        }
    }
}
