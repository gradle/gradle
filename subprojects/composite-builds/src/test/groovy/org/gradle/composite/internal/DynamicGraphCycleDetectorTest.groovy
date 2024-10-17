/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.composite.internal

import spock.lang.Specification

class DynamicGraphCycleDetectorTest extends Specification {

    def 'can detect immediate cycles'() {
        given:
        def graph = new DynamicGraphCycleDetector<String>()

        when:
        def result = graph.addEdge("A", "A")

        then:
        result.present

        and:
        result.get().format({ ":$it".toString() }) == ":A -> :A"
    }

    def 'can detect direct cycles'() {
        given:
        def graph = new DynamicGraphCycleDetector<String>()

        when:
        def result1 = graph.addEdge("A", "B")

        then:
        result1.empty

        then:
        def result2 = graph.addEdge("B", "A")

        then:
        result2.present

        and:
        result2.get().format({ ":$it".toString() }) == ":B -> :A -> :B"
    }

    def 'can detect indirect cycles'() {
        given:
        def graph = new DynamicGraphCycleDetector<String>()

        when:
        graph.addEdge("A", "B")
        graph.addEdge("B", "C")

        then:
        def result = graph.addEdge("C", "A")
        result.present

        and:
        result.get().format({ ":$it".toString() }) == ":C -> :A -> :B -> :C"
    }

    def 'can detect long indirect cycles'() {
        given:
        def graph = new DynamicGraphCycleDetector<String>()

        when:
        graph.addEdge("A", "B")
        graph.addEdge("B", "C")
        graph.addEdge("C", "D")

        then:
        def result = graph.addEdge("D", "A")
        result.present

        and:
        result.get().format({ ":$it".toString() }) == ":D -> :A -> :B -> :C -> :D"
    }
}
