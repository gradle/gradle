/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import spock.lang.Specification
import spock.lang.Subject

class ConflictContainerTest extends Specification {

    @Subject container = new ConflictContainer<String>()

    def "contains few unconflicted elements"() {
        container.newElement("a", null)
        container.newElement("b", null)
        container.newElement("c", "d")

        expect:
        container.size == 0
    }

    def "pops conflicts orderly"() {
        container.newElement("c", "f")
        container.newElement("f", null)
        container.newElement("b", "e")
        container.newElement("e", null)
        container.newElement("a", "d")
        container.newElement("d", null)

        expect:
        container.size == 3
        container.popConflict().toList() == ["c", "f"]
        container.popConflict().toList() == ["b", "e"]
        container.popConflict().toList() == ["a", "d"]
    }

    def "contains replacement conflict"() {
        container.newElement("a", "b")
        container.newElement("b", null)

        expect:
        container.size == 1
        container.popConflict().toList() == ["a", "b"]
    }

    def "contains replacement conflict reversed"() {
        container.newElement("b", null)
        container.newElement("a", "b")

        expect:
        container.size == 1
        container.popConflict().toList() == ["a", "b"]
    }

    def "contains replacement conflict interleaving"() {
        container.newElement("a", null)
        container.newElement("b", null)
        container.newElement("c", "a")
        container.newElement("d", null)

        expect:
        container.size == 1
        container.popConflict().toList() == ["c", "a"]
    }

    def "contains chained conflicts"() {
        container.newElement("a", "b") //a->b->c
        container.newElement("b", "c")
        container.newElement("c", null)

        expect:
        container.conflicts.size() == 1
        container.popConflict().toList() == ["a", "b", "c"]
    }

    def "allows registering multiple elements with the same replacedBy before the replacedBy"() {
        container.newElement("a", "c")
        container.newElement("b", "c")
        container.newElement("c", null)

        expect:
        container.conflicts.size() == 1
        container.popConflict().toList() == ["a", "b", "c"]
    }

    def "allows registering multiple elements with the same replacedBy after the replacedBy"() {
        container.newElement("c", null)
        container.newElement("a", "c")
        container.newElement("b", "c")

        expect:
        container.conflicts.size() == 1
        container.popConflict().toList() == ["a", "c", "b"]
    }

    def "contains chained conflicts registered in any order"() {
        def replacedBy = [a: "b", b: "c", c: null]
        order.each { container.newElement(it, replacedBy[it]) }

        expect:
        container.size == 1
        container.popConflict() == ["a", "b", "c"] as Set

        where:
        order << ["a", "b", "c"].permutations()
    }

    def "merges conflicts that a later element joins"() {
        container.newElement("a", "x")
        container.newElement("x", "z")
        container.newElement("b", "y")
        container.newElement("y", "z")
        container.newElement("z", null)

        expect:
        container.size == 1
        container.popConflict().toList() == ["a", "x", "b", "y", "z"]
    }

}
