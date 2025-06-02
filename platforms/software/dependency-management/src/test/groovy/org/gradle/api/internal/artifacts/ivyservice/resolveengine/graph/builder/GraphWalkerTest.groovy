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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder


import spock.lang.Specification

class GraphWalkerTest extends Specification {

    class Node {
        final String name
        final List<Edge> edges = []

        Node(String name) {
            this.name = name
        }

        void targets(Node node) {
            edges.add(new Edge(node))
        }

        @Override
        String toString() {
            return name
        }
    }

    class Edge {
        final Node target
        Edge(Node target) {
            this.target = target
        }

        @Override
        String toString() {
            return "${target.name}"
        }
    }

    def graph = new GraphWalker.Graph<Node, Edge>() {
        @Override
        List<Edge> getEdges(Node node) {
            return node.edges
        }

        @Override
        Node getNode(Edge edge) {
            return edge.target
        }
    }

    GraphWalker<Node, Edge> underTest = new GraphWalker<>(graph)

    void "handles cycles"() {
        given:
        Node a = new Node('a')
        Node b = new Node('b')
        Node c = new Node('c')
        Node d = new Node('d')

        a.targets(b)
        a.targets(c)
        b.targets(d)
        c.targets(d)
        d.targets(a) // cycle

        expect:
        expect(a, [d, b, c, a])
    }

    void "handles random graph"() {
        int graphSize = 1000
        int edgesPerNode = 100
        Node[] nodes = new Node[graphSize]
        for (int i = 0; i < graphSize; i++) {
            nodes[i] = new Node("$i")
        }
        Random random = new Random(0)
        for (int i = 0; i < graphSize; i++) {
            for (int j = 0; j < edgesPerNode; j++) {
                int targetIndex = random.nextInt(graphSize)
                nodes[i].targets(nodes[targetIndex])
            }
        }

//        Node root = new Node("root")
//        root.targets(nodes[0])

        when:
        def results = walkAll(nodes[0])

        then:
        for (int i = 0; i < results.size(); i++) {
            for (int j = 0; j < results.size(); j++) {
                if (i != j) {
                    assert results[i] == results[j]
                }
            }
        }
//        for (List<Node> thing : results) {
//            println("Trying")
//            assertTopological(nodes[0], thing)
//        }
    }

    void expect(Node root, List<Node> expected) {
        walkAll(root).each {
            assert it == expected
        }
    }

    private List<List<Node>> walkAll(Node root) {
        [
            walk(root, underTest::visitRecursive),
            walk(root, underTest::visitIterativeStackFrame),
            walk(root, underTest::visitIterativeStackFrame2),
            walk(root, underTest::visitIterativeStackFrame3)
        ]
    }

    interface BiCallable<A, B> {
        void call(A arg1, B arg2);
    }

    List<Node> walk(Node start, BiCallable<Node, GraphWalker.GraphVisitor<Node>> walker) {
        List<Node> results = []
        walker.call(start, new GraphWalker.GraphVisitor<Node>() {
            @Override
            void preVisit(Node node) {
                results.add(node)
            }

            @Override
            void postVisit(Node node) {
            }
        })
        return results
    }

    void assertTopological(Node root, List<Node> order) {
        assert order.size() > 0
        assert order[0] == root

        Set<Node> visited = new HashSet<>()
        for (int i = 0; i < order.size(); i++) {
            Node node = order[i]
            assert !visited.contains(node) : "Node ${node} was visited multiple times"
            visited.add(node)

            for (Edge edge : node.edges) {
                Node target = edge.target
                int targetIndex = order.indexOf(target)
                if (targetIndex < i) {
                    assert false : "Node ${target} is not reachable from ${node} in the correct order"
                }
            }
        }
    }
}
