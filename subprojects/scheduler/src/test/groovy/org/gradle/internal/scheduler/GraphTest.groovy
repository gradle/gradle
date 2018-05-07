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

import com.google.common.collect.ImmutableList
import org.gradle.api.CircularReferenceException
import org.gradle.internal.Cast
import spock.lang.Specification

import static org.gradle.internal.scheduler.EdgeType.DEPENDENT
import static org.gradle.internal.scheduler.EdgeType.FINALIZER
import static org.gradle.internal.scheduler.EdgeType.SHOULD_RUN_AFTER

class GraphTest extends Specification {
    def graph = new Graph()

    def "can add node to empty graph"() {
        when:
        def test = addNode("test")

        then:
        graph.allNodes == [test]
        graph.rootNodes == [test]
        graph.allEdges == []
    }

    def "can remove node"() {
        def test = addNode("test")
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
        def test = addNode("test")
        when:
        graph.addNode(test)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Node is already present in graph: test"
    }

    def "cannot add equal node twice"() {
        addNode("test")
        when:
        addNode("test")
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Node is already present in graph: test"
    }

    def "can connect nodes via addEdge"() {
        def source = addNode("source")
        def target = addNode("target")

        when:
        def edge = addEdge(source, target, DEPENDENT)

        then:
        graph.allNodes == [source, target]
        graph.rootNodes == [source]
        graph.allEdges == [edge]
    }

    def "can connect nodes via addEdgeIfAbsent"() {
        def source = addNode("source")
        def target = addNode("target")
        def edge = new Edge(source, target, DEPENDENT)

        when:
        def added = graph.addEdgeIfAbsent(edge)

        then:
        added
        graph.allNodes == [source, target]
        graph.rootNodes == [source]
        graph.allEdges == [edge]
    }

    def "fails when adding already existing edge with addEdge"() {
        def source = addNode("source")
        def target = addNode("target")
        def edge = addEdge(source, target, DEPENDENT)

        when:
        graph.addEdge(edge)

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Edge already present in graph: source --DEPENDENT--> target"
    }

    def "doesn't fail when adding already existing edge with addEdgeIfAbsent"() {
        def source = addNode("source")
        def target = addNode("target")
        def edge = addEdge(source, target, DEPENDENT)

        when:
        def added = graph.addEdgeIfAbsent(edge)

        then:
        !added
        graph.allNodes == [source, target]
        graph.rootNodes == [source]
        graph.allEdges == [edge]
    }

    def "cannot add edge to non-existent target"() {
        def source = addNode("source")

        when:
        addEdge(source, node("target"), DEPENDENT)

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Target node for edge to be added is not present in graph: source --DEPENDENT--> target"
    }

    def "cannot add edge from non-existent source"() {
        def target = addNode("target")

        when:
        addEdge(node("source"), target, DEPENDENT)

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
        def cycleReporter = Mock(CycleReporter)

        when:
        graph.breakCycles(cycleReporter)

        then:
        def ex = thrown CircularReferenceException
        ex.message == "Circular dependency between the following tasks:\nThere was a cycle"
        1 * cycleReporter.reportCycle(_) >> { List args ->
            Collection<Node> cycle = Cast.uncheckedCast(args[0])
            assert cycle as Set == ([a, b, c] as Set)
            return "There was a cycle"
        }
        0 * _
    }

    def "can break simple cycle"() {
        def a = addNode("a")
        def b = addNode("b")
        def c = addNode("c")
        def ab = addEdge(a, b, DEPENDENT)
        def bc = addEdge(b, c, DEPENDENT)
        addEdge(c, a, SHOULD_RUN_AFTER)
        def cycleReporter = Mock(CycleReporter)

        when:
        def dag = graph.breakCycles(cycleReporter)

        then:
        dag.allNodes == [a, b, c]
        dag.rootNodes == [a]
        dag.allEdges == [ab, bc]
    }

    /*
     * a -> b -> c -> d
     * c => a
     * d -> a
     */
    def "can detect complex cycle after breaking up smaller one"() {
        def a = addNode("a")
        def b = addNode("b")
        def c = addNode("c")
        def d = addNode("d")
        addEdge(a, b, DEPENDENT)
        addEdge(b, c, DEPENDENT)
        addEdge(c, d, DEPENDENT)
        addEdge(c, a, SHOULD_RUN_AFTER)
        addEdge(d, a, DEPENDENT)
        def cycleReporter = Mock(CycleReporter)

        when:
        graph.breakCycles(cycleReporter)

        then:
        def ex = thrown CircularReferenceException
        ex.message == "Circular dependency between the following tasks:\nThere was a cycle"
        1 * cycleReporter.reportCycle(_) >> { List args ->
            Collection<Node> cycle = Cast.uncheckedCast(args[0])
            assert cycle as Set == ([a, b, c, d] as Set)
            return "There was a cycle"
        }
        0 * _
    }
    /*
     * a -> b => c -> d
     * c -> a
     * d -> a
     */
    def "can break double cycle"() {
        def a = addNode("a")
        def b = addNode("b")
        def c = addNode("c")
        def d = addNode("d")
        def ab = addEdge(a, b, DEPENDENT)
        addEdge(b, c, SHOULD_RUN_AFTER)
        def cd = addEdge(c, d, DEPENDENT)
        def ca = addEdge(c, a, DEPENDENT)
        def da = addEdge(d, a, DEPENDENT)
        def cycleReporter = Mock(CycleReporter)

        when:
        def dag = graph.breakCycles(cycleReporter)

        then:
        dag.allNodes as Set == ([a, b, c, d]) as Set
        dag.rootNodes == [c]
        dag.allEdges == [ab, cd, ca, da]
    }

    def "retains no live nodes when no entry nodes are given"() {
        addNode("a")
        addNode("b")
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([], { node -> true}, filteredNodes, detector)

        then:
        live.allNodes == []
        live.rootNodes == []
        live.allEdges == []
        filteredNodes.build() == []
        0 * _

    }

    def "ignores dead node"() {
        def a = addNode("a")
        addNode("b")
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([a], { node -> true }, filteredNodes, detector)

        then:
        live.allNodes == [a]
        live.rootNodes == [a]
        live.allEdges == []
        filteredNodes.build() == []
        0 * _
    }

    def "ignores filtered node"() {
        def a = addNode("a")
        def b = addNode("b")
        def ab = addEdge(a, b, DEPENDENT)
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([b], { node -> node != a }, filteredNodes, detector)

        then:
        live.allNodes == [b]
        live.rootNodes == [b]
        live.allEdges == []
        filteredNodes.build() == [a]
        1 * detector.isIncomingEdgeLive(ab) >> true
        0 * _
    }

    def "retains dependency"() {
        def a = addNode("a")
        def b = addNode("b")
        def ab = addEdge(a, b, DEPENDENT)
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([b], { node -> true }, filteredNodes, detector)

        then:
        live.allNodes == [a, b]
        live.rootNodes == [a]
        live.allEdges == [ab]
        filteredNodes.build() == []
        1 * detector.isIncomingEdgeLive(ab) >> true
        0 * _
    }

    def "retains finalizer"() {
        def finalized = addNode("finalized")
        def finalizer = addNode("finalizer")
        def finalizerEdge = addEdge(finalized, finalizer, FINALIZER)
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([finalized], { node -> true }, filteredNodes, detector)

        then:
        live.allNodes == [finalized, finalizer]
        live.rootNodes == [finalized]
        live.allEdges == [finalizerEdge]
        1 * detector.isOutgoingEdgeLive(finalizerEdge) >> true
        0 * _
    }

    def "retains finalizer dependencies"() {
        def finalized = addNode("finalized")
        def finalizer = addNode("finalizer")
        def finalizerDependency = addNode("finalizerDependency")
        def finalizerEdge = addEdge(finalized, finalizer, FINALIZER)
        def finalizerDependentEdge = addEdge(finalizerDependency, finalizer, DEPENDENT)
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([finalized], { node -> true }, filteredNodes, detector)

        then:
        live.allNodes == [finalized, finalizerDependency, finalizer]
        live.rootNodes == [finalized, finalizerDependency]
        live.allEdges == [finalizerEdge, finalizerDependentEdge]
        1 * detector.isOutgoingEdgeLive(finalizerEdge) >> true
        1 * detector.isIncomingEdgeLive(finalizerDependentEdge) >> true
        0 * _
    }


    def "retains finalizer of finalizer dependencies"() {
        def finalized = addNode("finalized")
        def finalizer = addNode("finalizer")
        def finalizerDependency = addNode("finalizerDependency")
        def finalizerDependencyFinalizer = addNode("finalizerDependencyFinalizer")
        def finalizerEdge = addEdge(finalized, finalizer, FINALIZER)
        def finalizerDependentEdge = addEdge(finalizerDependency, finalizer, DEPENDENT)
        def finalizerDependencyFinalizerEdge = addEdge(finalizerDependency, finalizerDependencyFinalizer, FINALIZER)
        def filteredNodes = ImmutableList.builder()
        def detector = Mock(Graph.LiveEdgeDetector)

        when:
        def live = graph.retainLiveNodes([finalized], { node -> true }, filteredNodes, detector)

        then:
        live.allNodes == [finalized, finalizerDependency, finalizer, finalizerDependencyFinalizer]
        live.rootNodes == [finalized, finalizerDependency]
        live.allEdges == [finalizerEdge, finalizerDependentEdge, finalizerDependencyFinalizerEdge]
        1 * detector.isOutgoingEdgeLive(finalizerEdge) >> true
        1 * detector.isIncomingEdgeLive(finalizerDependentEdge) >> true
        1 * detector.isOutgoingEdgeLive(finalizerDependencyFinalizerEdge) >> true
        0 * _
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
        Throwable execute() {
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
