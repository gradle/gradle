/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.deps

import spock.lang.Specification

class ClassDependentsAccumulatorTest extends Specification {

    def accumulator = new ClassDependentsAccumulator()

    def "dependents map is empty by default"() {
        expect:
        accumulator.dependentsMap == [:]
    }

    def "remembers if class is dependency to all"() {
        // a -> b -> c
        accumulator.addClass("a", false, ["b"], [] as Set, [] as Set)
        accumulator.addClass("b", true,  ["c"], [] as Set, [] as Set)
        accumulator.addClass("c", false, ["a"] as Set, [] as Set, [] as Set)

        expect:
        !accumulator.dependentsMap.a.dependencyToAll
        accumulator.dependentsMap.b.dependencyToAll
        !accumulator.dependentsMap.c.dependencyToAll
    }

    def "remembers if class declares non-private constants"() {
        // a -> b -> c
        accumulator.addClass("a", false, ["b"], [1, 2, 3, 5, 8] as Set, [] as Set)
        accumulator.addClass("b", false,  ["c"], [0, 8] as Set, [] as Set)
        accumulator.addClass("c", false, [], [3, 4] as Set, [] as Set)

        expect:
        accumulator.classesToConstants.get('a') == [1, 2, 3, 5, 8] as Set
        accumulator.classesToConstants.get('b') == [0, 8] as Set
        accumulator.classesToConstants.get('c') == [3, 4] as Set
    }

    def "remembers if class has constant literals in bytecode"() {
        // a -> b -> c
        accumulator.addClass("a", false, ["b"], [] as Set, [1, 2, 3, 5, 8] as Set)
        accumulator.addClass("b", false,  ["c"], [] as Set, [0, 8] as Set)
        accumulator.addClass("c", false, [], [] as Set, [0, 3, 4] as Set)

        expect:
        accumulator.literalsToClasses.get(0) == ['b', 'c'] as Set
        accumulator.literalsToClasses.get(1) == ['a'] as Set
        accumulator.literalsToClasses.get(2) == ['a'] as Set
        accumulator.literalsToClasses.get(3) == ['a', 'c'] as Set
        accumulator.literalsToClasses.get(4) == ['c'] as Set
        accumulator.literalsToClasses.get(5) == ['a'] as Set
        accumulator.literalsToClasses.get(8) == ['a', 'b'] as Set
    }

    def "accumulates dependents"() {
        accumulator.addClass("d", true, ['x'], [] as Set, [] as Set)
        accumulator.addClass("a", false, ["b", "c"], [] as Set, [] as Set)
        accumulator.addClass("b", true,  ["c", "a"], [] as Set, [] as Set)
        accumulator.addClass("c", false, [] as Set, [] as Set, [] as Set)

        expect:
        accumulator.dependentsMap.a.dependentClasses == ['b'] as Set
        accumulator.dependentsMap.b.dependencyToAll
        accumulator.dependentsMap.c.dependentClasses == ['b', 'a'] as Set
        accumulator.dependentsMap.d.dependencyToAll
        accumulator.dependentsMap.x.dependentClasses == ['d'] as Set
    }

    def "creates keys for all encountered classes which are dependency to another"() {
        accumulator.addClass("a", false, ["x"], [] as Set, [] as Set)
        accumulator.addClass("b", true,  ["a", "b"], [] as Set, [] as Set)
        accumulator.addClass("c", true,  [] as Set, [] as Set, [] as Set)
        accumulator.addClass("e", false,  [] as Set, [] as Set, [] as Set)

        expect:
        accumulator.dependentsMap.keySet() == ["a", "b", "c", "x"] as Set
    }

    def "knows when class is dependent to all if that class is added first"() {
        accumulator.addClass("b", true,  [] as Set, [] as Set, [] as Set)
        accumulator.addClass("a", false, ["b"], [] as Set, [] as Set)

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "knows when class is dependent to all even if that class is added last"() {
        accumulator.addClass("a", false, ["b"], [] as Set, [] as Set)
        accumulator.addClass("b", true,  [] as Set, [] as Set, [] as Set)

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "filters out self dependencies"() {
        accumulator.addClass("a", false, ["a", "b"], [] as Set, [] as Set)

        expect:
        accumulator.dependentsMap["b"].dependentClasses == ["a"] as Set
        accumulator.dependentsMap["a"] == null
    }
}
