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

package org.gradle.internal.scheduler

import org.gradle.internal.Cast
import spock.lang.Specification

import static org.gradle.internal.scheduler.EdgeType.AVOID_STARTING_BEFORE
import static org.gradle.internal.scheduler.EdgeType.DEPENDENT

class GraphTest extends Specification {
    def graph = new Graph()

    def "can add node to empty graph"() {
        def test = node("test")
        when:
        graph.addNode(test)

        then:
        graph.allNodes == [test]
        graph.rootNodes == [test]
        graph.allEdges == []
    }

    def "can remove node"() {
        def test = node("test")
        graph.addNode(test)
        when:
        graph.removeNodeWithOutgoingEdges(test) { edge ->
            throw new RuntimeException("shouldn't remove any edges")
        }

        then:
        graph.allNodes == []
        graph.rootNodes == []
        graph.allEdges == []
    }

    def "cannot remove non-existent node"() {
        def test = node("test")
        when:
        graph.removeNodeWithOutgoingEdges(test) { edge ->
            throw new RuntimeException("shouldn't remove any edges")
        }

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Node is not present in the graph: test"
    }

    def "cannot add same node twice"() {
        def test = node("test")
        graph.addNode(test)
        when:
        graph.addNode(test)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Node is already present in graph: test"
    }

    def "cannot add queal node twice"() {
        graph.addNode(node("test"))
        when:
        graph.addNode(node("test"))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Node is already present in graph: test"
    }

    def "can connect nodes"() {
        def source = node("source")
        def target = node("target")
        def edge = new Edge(source, target, DEPENDENT)
        graph.addNode(source)
        graph.addNode(target)

        when:
        graph.addEdge(edge)

        then:
        graph.allNodes == [source, target]
        graph.rootNodes == [source]
        graph.allEdges == [edge]
    }

    def "cannot add edge to non-existent target"() {
        def source = node("source")
        def target = node("target")
        def edge = new Edge(source, target, DEPENDENT)
        graph.addNode(source)

        when:
        graph.addEdge(edge)

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Target node for edge to be added is not present in graph: source --DEPENDENT--> target"
    }

    def "cannot add edge from non-existent source"() {
        def source = node("source")
        def target = node("target")
        def edge = new Edge(source, target, DEPENDENT)
        graph.addNode(target)

        when:
        graph.addEdge(edge)

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Source node for edge to be added is not present in graph: source --DEPENDENT--> target"
    }

    def "can detect simple cycle"() {
        def a = addNode("a")
        def b = addNode("b")
        def c = addNode("c")
        addEdge(a, b, DEPENDENT)
        addEdge(b, c, DEPENDENT)
        addEdge(c, a, DEPENDENT)
        def cycleReporter = Mock(GraphCycleReporter)

        when:
        graph.breakCycles(cycleReporter)

        then:
        def ex = thrown IllegalStateException
        ex.message == "There was a cycle"
        1 * cycleReporter.throwException(_) >> { List args ->
            Collection<Node> cycle = Cast.uncheckedCast(args[0])
            assert cycle == [a, b, c]
            throw new IllegalStateException("There was a cycle")
        }
        0 * _
    }

    def "can break simple cycle"() {
        def a = addNode("a")
        def b = addNode("b")
        def c = addNode("c")
        def ab = addEdge(a, b, DEPENDENT)
        def bc = addEdge(b, c, DEPENDENT)
        addEdge(c, a, AVOID_STARTING_BEFORE)
        def cycleReporter = Mock(GraphCycleReporter)

        when:
        graph.breakCycles(cycleReporter)

        then:
        graph.allNodes == [a, b, c]
        graph.rootNodes == [a]
        graph.allEdges == [ab, bc]
    }

    private Node addNode(String name) {
        def n = node(name)
        graph.addNode(n)
        return n
    }

    private Edge addEdge(Node source, Node target, EdgeType type) {
        def edge = new Edge(source, target, type)
        graph.addEdge(edge)
        return edge
    }

    private static Node node(String name) {
        return new TestNode(name)
    }

    static class TestNode extends Node {
        final String name

        TestNode(String name) {
            this.name = name
        }

        @Override
        boolean canExecuteInParallelWith(Node other) {
            throw new UnsupportedOperationException()
        }

        @Override
        void execute() {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            TestNode testNode = (TestNode) o

            if (name != testNode.name) return false

            return true
        }

        @Override
        int hashCode() {
            return name.hashCode()
        }

        @Override
        String toString() {
            return name
        }
    }
}
