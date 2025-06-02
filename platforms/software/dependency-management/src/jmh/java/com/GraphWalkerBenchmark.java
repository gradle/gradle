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

package com;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.GraphWalker;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class GraphWalkerBenchmark {

   @Param({"0.1", "0.5", "0.9"})
    // @Param({"0.2"})
    private float complexity;

    // Parameters for different graph types and sizes
   @Param({"10", "100", "1000", "10000"})
    // @Param({"1000"})
    private int nodeCount;

   @Param({"WIDE_TREE", "DAG", "CYCLIC", "RANDOM", "DEPENDENCY"})
    // @Param({"DEPENDENCY"})
    private GraphType graphType;

    private GraphWalker<Node, Edge> walker;
    private NoOpVisitor visitor;
    private SimpleGraph graph;

    public enum GraphType {
        WIDE_TREE,      // Each node has many children (10-20)
        DAG,            // Directed acyclic graph with multiple paths
        CYCLIC,          // Graph with cycles
        RANDOM,          // Random graph with configurable edges per node
        DEPENDENCY      // Simulates library dependency graphs
    }

    // Simple edge class
    static class Edge {
        final Node target;

        Edge(Node target) {
            this.target = target;
        }
    }

    static class Node {
        final Integer id;
        final List<Edge> edges = new ArrayList<>();
        Node(Integer id) {
            this.id = id;
        }
    }

    // Simple graph implementation
    static class SimpleGraph implements GraphWalker.Graph<Node, Edge> {
        private final List<Node> nodes = new ArrayList<>();

        public SimpleGraph(int size) {
            for (int i = 0; i < size; i++) {
                nodes.add(new Node(i));
            }
        }

        public Node getRoot() {
            return nodes.get(0);
        }

        public void addEdge(int from, int to) {
            nodes.get(from).edges.add(new Edge(nodes.get(to)));
        }

        @Override
        public List<Edge> getEdges(Node node) {
            return node.edges;
        }

        @Override
        @Nullable
        public Node getNode(Edge edge) {
            return edge.target;
        }
    }

    // No-op visitor to focus on traversal performance
    static class NoOpVisitor implements GraphWalker.GraphVisitor<Node> {
        private int visitCount = 0;

        @Override
        public void preVisit(Node node) {
            visitCount++; // Minimal work to prevent optimization
        }

        @Override
        public void postVisit(Node node) {
            visitCount++; // Minimal work to prevent optimization
        }

        public int getVisitCount() {
            return visitCount;
        }

        public void reset() {
            visitCount = 0;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        graph = new SimpleGraph(nodeCount);

        switch (graphType) {
            case WIDE_TREE:
                buildWideTree(graph, nodeCount, complexity);
                break;
            case DAG:
                buildDAG(graph, nodeCount, complexity);
                break;
            case CYCLIC:
                buildCyclicGraph(graph, nodeCount, complexity);
                break;
            case RANDOM:
                buildRandomGraph(graph, nodeCount, complexity);
                break;
            case DEPENDENCY:
                buildDistributedHubGraph(graph, nodeCount, complexity);
                break;
        }

        walker = new GraphWalker<>(graph);
        visitor = new NoOpVisitor();
    }

    private static void buildWideTree(SimpleGraph graph, int n, float complexity) {
        int numChildren = 1 + (int) ((n - 1) * complexity);

        int nodesAdded = 1;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(0);

        while (!queue.isEmpty() && nodesAdded < n) {
            Integer parent = queue.poll();
            for (int i = 0; i < numChildren && nodesAdded < n; i++) {
                graph.addEdge(parent, nodesAdded);
                queue.offer(nodesAdded);
                nodesAdded++;
            }
        }
    }

    private static void buildDAG(SimpleGraph graph, int n, float complexity) {
        // Create a DAG where each node connects to several nodes ahead of it
        // Complexity controls how many forward connections
        for (int i = 0; i < n; i++) {
            int remainingNodes = n - i - 1;
            if (remainingNodes > 0) {
                // Connect to 2-8 nodes based on complexity
                int connections = 1 + (int) ((remainingNodes - 1) * complexity);
                for (int j = 0; j < connections; j++) {
                    // Distribute connections among all forward nodes
                    int target = i + 1 + ((j * 31) % remainingNodes);
                    graph.addEdge(i, target);
                }
            }
        }
    }

    private static void buildCyclicGraph(SimpleGraph graph, int n, float complexity) {
        // Build a graph with cycles
        // Base structure: linear chain
        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(i, i + 1);
        }

        // Add cycles based on complexity
        // More complexity = more back edges and cross edges
        int cycleFrequency = Math.max(2, (int)(20 / (1 + 9 * complexity))); // 20 to 2
        int crossEdgeFrequency = Math.max(3, (int)(30 / (1 + 9 * complexity))); // 30 to 3

        for (int i = 0; i < n; i++) {
            // Add back edges to create cycles
            if (i % cycleFrequency == 0 && i > 0) {
                graph.addEdge(i, i / 2);
                if (complexity > 0.5) {
                    graph.addEdge(i, i / 3); // Extra cycle for high complexity
                }
            }
            // Add forward edges
            if (i % crossEdgeFrequency == 0 && i + 10 < n) {
                graph.addEdge(i, i + 10);
                if (complexity > 0.7 && i + 20 < n) {
                    graph.addEdge(i, i + 20); // Extra forward edge
                }
            }
        }

        // Add edge from last to first to ensure cycle
        graph.addEdge(n - 1, 0);
    }

    private void buildRandomGraph(SimpleGraph graph, int n, float complexity) {
        Random random = new Random(0); // Fixed seed for reproducibility
        int edgesPerNode = (int) (complexity * nodeCount);

        // Create all nodes first (they exist implicitly as integers 0 to n-1)
        // Then add random edges from each node
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < edgesPerNode; j++) {
                int targetIndex = random.nextInt(n);
                graph.addEdge(i, targetIndex);
            }
        }
    }

    public static void buildDistributedHubGraph(SimpleGraph graph, int n, float complexity) {
        Random random = new Random(0); // Fixed seed for reproducible graphs

        // This score determines how "attractive" a node is as a dependency.
        double[] dependabilityScores = new double[n];
        for (int j = 0; j < n; j++) {
            // A score close to 1.0 (strong hub) becomes rarer as HUB_SCORE_SKEW_EXPONENT increases.
            dependabilityScores[j] = Math.pow(random.nextDouble(), 2.0);
        }

        for (int i = 0; i < n; i++) { // Source node
            for (int j = 0; j < n; j++) { // Potential target node
                float relativeAge = (float) (i - j) / (n - 1);
                double ageInfluence = (relativeAge + 1.0) / 2.0;
                double influence = dependabilityScores[j] * ageInfluence;
                double edgeProbability = complexity + (1.0 - complexity) * influence;

                if (random.nextDouble() < edgeProbability) {
                    graph.addEdge(i, j);
                }
            }
        }
    }

    @Setup(Level.Invocation)
    public void resetVisitor() {
        visitor.reset();
    }

    @Benchmark
    public int visitRecursive() {
        walker.visitRecursive(graph.getRoot(), visitor);
        return visitor.getVisitCount();
    }

    @Benchmark
    public int visitIterativeStackFrame() {
        walker.visitIterativeStackFrame(graph.getRoot(), visitor);
        return visitor.getVisitCount();
    }

    @Benchmark
    public int visitIterativeStackFrame2() {
        walker.visitIterativeStackFrame2(graph.getRoot(), visitor);
        return visitor.getVisitCount();
    }

    @Benchmark
    public int visitIterativeStackFrame3() {
        walker.visitIterativeStackFrame3(graph.getRoot(), visitor);
        return visitor.getVisitCount();
    }
}
