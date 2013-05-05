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

class GraphAggregatorTest extends Specification {
    private final DirectedGraph<String, String> graph = Mock()
    private final GraphAggregator aggregator = new GraphAggregator(graph)

    def groupsNodeWithTheEntryNodeItIsReachableFrom() {
        when:
        def result = aggregator.group(['a'], ['a', 'b'])

        then:
        1 * graph.getNodeValues('a', !null, !null) >> { args -> args[2].add('b') }
        result.getNodes('a') == ['a', 'b'] as Set
    }

    def groupsNodeWithTheClosesEntryNodeItIsReachableFrom() {
        when:
        def result = aggregator.group(['a', 'b'], ['a', 'b', 'c'])

        then:
        1 * graph.getNodeValues('a', !null, !null) >> { args -> args[2].add('b') }
        1 * graph.getNodeValues('b', !null, !null) >> { args -> args[2].add('c') }
        result.getNodes('a') == ['a'] as Set
        result.getNodes('b') == ['b', 'c'] as Set
    }

    def groupsNodeWithMultipleEntryNodesWhenTheNodeHasMultipleClosesNodes() {
        when:
        def result = aggregator.group(['a', 'b'], ['a', 'b', 'c'])

        then:
        1 * graph.getNodeValues('a', !null, !null) >> { args -> args[2].add('c') }
        1 * graph.getNodeValues('b', !null, !null) >> { args -> args[2].add('c') }
        result.getNodes('a') == ['a', 'c'] as Set
        result.getNodes('b') == ['b', 'c'] as Set
    }

    def groupsNodesWhichAreNotReachableFromStartNodes() {
        when:
        def result = aggregator.group(['a', 'b'], ['a', 'b', 'c', 'd'])

        then:
        1 * graph.getNodeValues('a', !null, !null) >> { args -> args[2].add('b') }
        1 * graph.getNodeValues('c', !null, !null) >> { args -> args[2].add('d') }
        result.topLevelNodes == ['a', 'b', 'c'] as Set
        result.getNodes('c') == ['c', 'd'] as Set
    }
}
