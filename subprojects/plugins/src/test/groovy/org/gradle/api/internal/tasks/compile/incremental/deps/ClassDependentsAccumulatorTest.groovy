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

    def accumulator = new ClassDependentsAccumulator("")

    def "dependents map is empty by default"() {
        expect:
        accumulator.dependentsMap == [:]
    }

    def "remembers if class is dependency to all"() {
        // a -> b -> c
        accumulator.addClass("a", false, ["b"])
        accumulator.addClass("b", true,  ["c"])
        accumulator.addClass("c", false, [])

        expect:
        !accumulator.dependentsMap.a.dependencyToAll
        accumulator.dependentsMap.b.dependencyToAll
        !accumulator.dependentsMap.c.dependencyToAll
    }

    def "accumulates dependents"() {
        accumulator.addClass("d", true, ['x'])
        accumulator.addClass("a", false, ["b", "c"])
        accumulator.addClass("b", true,  ["c", "a"])
        accumulator.addClass("c", false, [])

        expect:
        accumulator.dependentsMap.a.dependentClasses == ['b'] as Set
        accumulator.dependentsMap.b.dependentClasses == ['a'] as Set
        accumulator.dependentsMap.c.dependentClasses == ['b', 'a'] as Set
        accumulator.dependentsMap.d.dependentClasses == [] as Set
        accumulator.dependentsMap.x.dependentClasses == ['d'] as Set
    }

    def "creates keys for all encountered classes"() {
        accumulator.addClass("a", false, ["x"])
        accumulator.addClass("b", true,  ["a", "b"])
        accumulator.addClass("c", true,  [])

        expect:
        accumulator.dependentsMap.keySet() == ["a", "b", "c", "x"] as Set
    }

    def "knows when class is dependent to all if that class is added first"() {
        accumulator.addClass("b", true,  [])
        accumulator.addClass("a", false, ["b"])

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "knows when class is dependent to all even if that class is added last"() {
        accumulator.addClass("a", false, ["b"])
        accumulator.addClass("b", true,  [])

        expect:
        accumulator.dependentsMap.b.dependencyToAll
    }

    def "uses package prefix filter for classes"() {
        accumulator = new ClassDependentsAccumulator("org.gradle")
        accumulator.addClass("gradle.Foo", true, ["org.gradle.Foo"])

        expect:
        accumulator.dependentsMap["gradle.Foo"] == null
        accumulator.dependentsMap["org.gradle.Foo"].dependentClasses.isEmpty()
    }

    def "uses package prefix filter for dependencies"() {
        accumulator = new ClassDependentsAccumulator("org.gradle")
        accumulator.addClass("org.gradle.Foo", false, ["gradle.Bar"])

        expect:
        accumulator.dependentsMap["gradle.Bar"] == null
        accumulator.dependentsMap["org.gradle.Foo"].dependentClasses.isEmpty()
    }

    def "filters out self dependencies"() {
        accumulator.addClass("a", false, ["a", "b"])

        expect:
        accumulator.dependentsMap["b"].dependentClasses == ["a"] as Set
        accumulator.dependentsMap["a"].dependentClasses.isEmpty()
    }
}
