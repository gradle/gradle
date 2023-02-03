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
package org.gradle.internal.graph

import spock.lang.Specification

class CachingDirectedGraphWalkerTest extends Specification {
    private final DirectedGraphWithEdgeValues<Integer, String> graph = Mock()
    private final CachingDirectedGraphWalker walker = new CachingDirectedGraphWalker(graph)

    def collectsValuesForASingleNode() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1' }
        0 * _._
        values == ['1'] as Set
    }

    def collectsValuesForEachSuccessorNode() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 2; args[2] << 3 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3' }
        3 * graph.getEdgeValues(_, _, _)
        0 * _._
        values == ['1', '2', '3'] as Set
    }

    def collectsValuesForEachEdgeTraversed() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[2] << 2; args[2] << 3 }
        1 * graph.getEdgeValues(1, 2, _) >> { args -> args[2] << '1->2' }
        1 * graph.getEdgeValues(1, 3, _) >> { args -> args[2] << '1->3' }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[2] << 3 }
        1 * graph.getEdgeValues(2, 3, _) >> { args -> args[2] << '2->3' }
        1 * graph.getNodeValues(3, _, _)
        0 * _._
        values == ['1->2', '1->3', '2->3'] as Set
    }

    def collectsValuesForAllStartNodes() {
        when:
        walker.add(1, 2)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 3 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3' }
        2 * graph.getEdgeValues(_, _, _)
        0 * _._
        values == ['1', '2', '3'] as Set
    }

    def collectsValuesWhenCycleIsPresentInGraph() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 2 }
        1 * graph.getEdgeValues(1, 2, _) >> { args -> args[2] << '1->2' }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3 }
        1 * graph.getEdgeValues(2, 3, _) >> { args -> args[2] << '2->3' }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3'; args[2] << 1 }
        1 * graph.getEdgeValues(3, 1, _) >> { args -> args[2] << '3->1' }
        0 * _._
        values == ['1', '1->2', '2', '2->3', '3', '3->1'] as Set
    }

    def collectsValuesWhenNodeConnectedToItself() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 1 }
        1 * graph.getEdgeValues(1, 1, _) >> { args -> args[2] << '1->1' }
        0 * _._
        values == ['1', '1->1'] as Set
    }

    def collectsValuesWhenMultipleCyclesInGraph() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 1; args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3; args[2] << 4 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3'; args[2] << 2 }
        1 * graph.getNodeValues(4, _, _) >> { args -> args[1] << '4' }
        5 * graph.getEdgeValues(_, _, _) >> { args -> args[2] << "${args[0]}->${args[1]}".toString() }
        0 * _._
        values == ['1', '1->1', '1->2', '2', '2->3', '2->4', '3', '3->2', '4'] as Set
    }

    def collectsValuesAndCyclesInComplexCyclicGraph() {
        when:
        walker.add(0)

        and:
        _ * graph.getNodeValues(0, _, _) >> { args -> args[2] << 4; args[2] << 1 }
        _ * graph.getNodeValues(1, _, _) >> { args -> args[2] << 4; args[2] << 2 }
        _ * graph.getNodeValues(2, _, _) >> { args -> args[2] << 1; args[2] << 3 }
        _ * graph.getNodeValues(3, _, _) >> { args -> args[2] << 2 }
        _ * graph.getNodeValues(4, _, _) >> { args -> args[2] << 5 }
        _ * graph.getNodeValues(5, _, _) >> { args -> args[2] << 3 }

        _ * graph.getEdgeValues(_, _, _) >> { args -> args[2] << "${args[0]}->${args[1]}".toString() }

        then:
        walker.findValues() == ['0->1', '0->4', '1->2', '1->4', '2->1', '2->3', '3->2', '4->5', '5->3'] as Set
        walker.findCycles() == [[1, 2, 3, 4, 5] as Set]
    }

    def locatesCyclesWhenSingleCycleInGraph() {
        when:
        walker.add(1)
        def values = walker.findCycles()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[2] << 1; args[2] << 4 }
        1 * graph.getNodeValues(4, _, _) >> { args -> }
        _ * graph.getEdgeValues(_, _, _)
        0 * _._

        and:
        values == [[1, 2, 3] as Set]
    }

    def locatesCyclesWhenCycleContainsASingleNode() {
        when:
        walker.add(1)
        def values = walker.findCycles()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[2] << 2 }
        _ * graph.getEdgeValues(_, _, _)
        0 * _._

        and:
        values == [[2] as Set]
    }

    def locatesCyclesWhenCycleContainsCycles() {
        when:
        walker.add(1)
        def values = walker.findCycles()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[2] << 4; args[2] << 1 }
        1 * graph.getNodeValues(4, _, _) >> { args -> args[2] << 3; args[2] << 5 }
        1 * graph.getNodeValues(5, _, _) >> { args -> args[2] << 4; args[2] << 1 }
        _ * graph.getEdgeValues(_, _, _)
        0 * _._

        and:
        values == [[1, 2, 3, 4, 5] as Set]
    }

    def locatesCyclesWhenMultipleCyclesInGraph() {
        when:
        walker.add(1)
        def values = walker.findCycles()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[2] << 1; args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[2] << 1; args[2] << 4 }
        1 * graph.getNodeValues(4, _, _) >> { args -> args[2] << 4; args[2] << 5; }
        1 * graph.getNodeValues(5, _, _) >> { args -> args[2] << 6 }
        1 * graph.getNodeValues(6, _, _) >> { args -> args[2] << 5 }
        _ * graph.getEdgeValues(_, _, _)
        0 * _._

        and:
        values == [[5, 6] as Set, [4] as Set, [1, 2, 3] as Set]
    }

    def canReuseWalkerForMultipleSearches() {
        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 2; args[2] << 3 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3' }
        3 * graph.getEdgeValues(_, _, _)
        0 * _._
        values == ['1', '2', '3'] as Set

        // Cached node (1) is reachable via 2 separate new paths (4->5->1 and 4->6->1)
        when:
        walker.add(4)
        values = walker.findValues()

        then:
        1 * graph.getNodeValues(4, _, _) >> { args -> args[1] << '4'; args[2] << 5; args[2] << 6 }
        1 * graph.getNodeValues(5, _, _) >> { args -> args[1] << '5'; args[2] << 1 }
        1 * graph.getNodeValues(6, _, _) >> { args -> args[1] << '6'; args[2] << 1 }
        4 * graph.getEdgeValues(_, _, _) >> { args -> args[2] << "${args[0]}->${args[1]}".toString() }
        0 * _._
        values == ['4', '4->5', '4->6', '5', '5->1', '6', '6->1', '1', '2', '3'] as Set

        when:
        walker.add(2)
        values = walker.findValues()

        then:
        values == ['2', '3'] as Set
    }

    def canReuseWalkerWhenGraphContainsACycle() {
        when:
        walker.add(1)
        walker.findValues()

        then:
        1 * graph.getNodeValues(1, _, _) >> { args -> args[1] << '1'; args[2] << 2 }
        1 * graph.getNodeValues(2, _, _) >> { args -> args[1] << '2'; args[2] << 3 }
        1 * graph.getNodeValues(3, _, _) >> { args -> args[1] << '3'; args[2] << 1; args[2] << 4 }
        1 * graph.getNodeValues(4, _, _) >> { args -> args[1] << '4'; args[2] << 2 }
        5 * graph.getEdgeValues(_, _, _)
        0 * _._

        when:
        walker.add(1)
        def values = walker.findValues()

        then:
        values == ['1', '2', '3', '4'] as Set

        when:
        walker.add(2)
        values = walker.findValues()

        then:
        values == ['1', '2', '3', '4'] as Set

        when:
        walker.add(3)
        values = walker.findValues()

        then:
        values == ['1', '2', '3', '4'] as Set

        when:
        walker.add(4)
        values = walker.findValues()

        then:
        values == ['1', '2', '3', '4'] as Set
    }
}
