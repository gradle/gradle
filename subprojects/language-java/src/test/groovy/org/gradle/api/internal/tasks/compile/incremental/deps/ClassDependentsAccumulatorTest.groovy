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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class ClassDependentsAccumulatorTest extends Specification {

    def hash = TestHashCodes.hashCodeFrom(0)
    def accumulator = new ClassDependentsAccumulator()

    def "dependents map is empty by default"() {
        expect:
        accumulator.dependentsMap == [:]
    }

    def "remembers if class is dependency to all"() {
        // a -> b -> c
        accumulator.addClass("a", hash, null, [], ["b"], IntSets.EMPTY_SET)
        accumulator.addClass("b", hash, "reason",  [], ["c"], IntSets.EMPTY_SET)
        accumulator.addClass("c", hash, null, [], ["a"] as Set, IntSets.EMPTY_SET)

        expect:
        !accumulator.dependentsMap.a.dependencyToAll
        accumulator.dependentsMap.b.dependencyToAll
        !accumulator.dependentsMap.c.dependencyToAll
    }

    def "remembers if class declares non-private constants"() {
        // a -> b -> c
        accumulator.addClass("a", hash, null, [], ["b"], new IntOpenHashSet(1, 2, 3, 5, 8))
        accumulator.addClass("b", hash, null, [], ["c"], new IntOpenHashSet([0, 8]))
        accumulator.addClass("c", hash, null, [], [], new IntOpenHashSet([3, 4]))

        expect:
        accumulator.classesToConstants.get('a') == [1, 2, 3, 5, 8] as Set
        accumulator.classesToConstants.get('b') == [0, 8] as Set
        accumulator.classesToConstants.get('c') == [3, 4] as Set
    }

    def "accumulates accessible and private dependents"() {
        accumulator.addClass("d", hash, "reason",  [], ['x'], IntSets.EMPTY_SET)
        accumulator.addClass("a", hash, null, ["b"], ["c"], IntSets.EMPTY_SET)
        accumulator.addClass("b", hash, null, ["c"], ["a"], IntSets.EMPTY_SET)
        accumulator.addClass("c", hash, null, [], [] as Set, IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap.a.privateDependentClasses == [] as Set
        accumulator.dependentsMap.a.accessibleDependentClasses == ['b'] as Set
        accumulator.dependentsMap.b.privateDependentClasses == ['a'] as Set
        accumulator.dependentsMap.b.accessibleDependentClasses == [] as Set
        accumulator.dependentsMap.c.privateDependentClasses == ['b'] as Set
        accumulator.dependentsMap.c.accessibleDependentClasses == ['a'] as Set
        accumulator.dependentsMap.d.dependencyToAll
        accumulator.dependentsMap.x.privateDependentClasses == [] as Set
        accumulator.dependentsMap.x.accessibleDependentClasses == ['d'] as Set
    }

    def "accumulates dependents"() {
        accumulator.addClass("d", hash, "reason",  [], ['x'], IntSets.EMPTY_SET)
        accumulator.addClass("a", hash, null, [], ["b", "c"], IntSets.EMPTY_SET)
        accumulator.addClass("b", hash, "reason",  [], ["c", "a"], IntSets.EMPTY_SET)
        accumulator.addClass("c", hash, null, [], [] as Set, IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap.a.privateDependentClasses == [] as Set
        accumulator.dependentsMap.a.accessibleDependentClasses == ['b'] as Set
        accumulator.dependentsMap.b.dependencyToAll
        accumulator.dependentsMap.c.privateDependentClasses == [] as Set
        accumulator.dependentsMap.c.accessibleDependentClasses == ['b', 'a'] as Set
        accumulator.dependentsMap.d.dependencyToAll
        accumulator.dependentsMap.x.privateDependentClasses == [] as Set
        accumulator.dependentsMap.x.accessibleDependentClasses == ['d'] as Set
    }

    def "creates keys for all encountered classes which are dependency to another"() {
        accumulator.addClass("a", hash, null, [], ["x"], IntSets.EMPTY_SET)
        accumulator.addClass("b", hash, "reason",  [], ["a", "b"], IntSets.EMPTY_SET)
        accumulator.addClass("c", hash, "reason",  [], [] as Set, IntSets.EMPTY_SET)
        accumulator.addClass("e", hash, null, [], [] as Set, IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap.keySet() == ["a", "b", "c", "x"] as Set
    }

    def "knows when class is dependent to all if that class is added first"() {
        accumulator.addClass("b", hash, "reason",  [], [] as Set, IntSets.EMPTY_SET)
        accumulator.addClass("a", hash, null, [], ["b"], IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "knows when class is dependent to all even if that class is added last"() {
        accumulator.addClass("a", hash, null, [], ["b"], IntSets.EMPTY_SET)
        accumulator.addClass("b", hash, "reason",  [], [] as Set, IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "filters out self dependencies"() {
        accumulator.addClass("a", hash, null, [], ["a", "b"], IntSets.EMPTY_SET)

        expect:
        accumulator.dependentsMap["b"].privateDependentClasses == [] as Set
        accumulator.dependentsMap["b"].accessibleDependentClasses == ["a"] as Set
        accumulator.dependentsMap["a"] == null
    }
}
