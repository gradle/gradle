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

import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.*

class ClassSetAnalysisTest extends Specification {

    ClassSetAnalysis analysis(Map<String, DependentsSet> dependents,
                              Map<String, IntSet> classToConstants = [:],
                              Map<String, Set<String>> classesToChildren = [:], DependentsSet dependentsOnAll = empty(), String fullRebuildCause = null) {
        new ClassSetAnalysis(new ClassSetAnalysisData([:], dependents, classToConstants, classesToChildren, dependentsOnAll, fullRebuildCause))
    }

    def "returns empty analysis"() {
        def a = analysis([:])
        expect: a.getRelevantDependents("Foo", IntSets.EMPTY_SET).dependentClasses.isEmpty()
    }

    def "does not recurse if root class is a dependency to all"() {
        def a = analysis(["Foo": dependentSet(true, ["Bar"])])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.dependencyToAll

        when: deps.getDependentClasses()
        then: thrown(UnsupportedOperationException)
    }

    def "marks as dependency to all only if root class is a dependency to all"() {
        def a = analysis([
            "a": dependentSet(false, ['b']),
            'b': dependentSet(true, []),
            "c": dependentSet(true, [])
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ['b'] as Set
        !deps.dependencyToAll
    }

    def "recurses nested dependencies"() {
        def a = analysis([
            "Foo": dependents("Bar"),
            "Bar": dependents("Baz"),
            "Baz": dependents(),
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["Bar", "Baz"] as Set
        a.getRelevantDependents("Bar", IntSets.EMPTY_SET).dependentClasses == ["Baz"] as Set
        a.getRelevantDependents("Baz", IntSets.EMPTY_SET).dependentClasses == [] as Set
    }

    def "recurses multiple dependencies"() {
        def a = analysis([
            "a": dependents("b", "c"),
            "b": dependents("d"),
            "c": dependents("e"),
            "d": dependents(),
            "e": dependents()
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["b", "c", "d", "e"] as Set
    }

    def "removes self from dependents"() {
        def a = analysis([
            "Foo": dependents("Foo")
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == [] as Set
    }

    def "handles dependency cycles"() {
        def a = analysis([
            "Foo": dependents("Bar"),
            "Bar": dependents("Baz"),
            "Baz": dependents("Foo"),
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["Bar", "Baz"] as Set
    }

    def "recurses but filters out inner classes"() {
        def a = analysis([
            "a": dependents('a$b', 'c'),
            'a$b': dependents('d'),
            "c": dependents(),
            "d": dependents(),
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["c", "d"] as Set
    }

    def "handles cycles with inner classes"() {
        def a = analysis([
            "a": dependents('a$b'),
            'a$b': dependents('a$b', 'c'),
            "c": dependents()
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["c"] as Set
    }

    def "provides dependents of all input classes"() {
        def a = analysis([
            "A": dependents("B"), "B": dependents(),
            "C": dependents("D"), "D": dependents(),
            "E": dependents("D"), "F": dependents(),
        ])
        def deps = a.getRelevantDependents(["A", "E"], IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["D", "B"] as Set
    }

    def "provides recursive dependents of all input classes"() {
        def a = analysis([
            "A": dependents("B"), "B": dependents("C"), "C": dependents(),
            "D": dependents("E"), "E": dependents(),
            "F": dependents("G"), "G": dependents(),
        ])
        def deps = a.getRelevantDependents(["A", "D"], IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["E", "B", "C"] as Set
    }

    def "some classes may depend on any change"() {
        def a = analysis([
            "A": dependents("B"), "B": empty(), "DependsOnAny" : dependents("C")
        ], [:], [:], dependents("DependsOnAny") )
        def deps = a.getRelevantDependents(["A"], IntSets.EMPTY_SET)

        expect:
        deps.dependentClasses == ["DependsOnAny", "B", "C"] as Set
    }

    def "knows when any of the input classes is a dependency to all"() {
        def a = analysis([
            "A": dependents("B"), "B": dependents(),
            "C": dependentSet(true, []),
            "D": dependents("E"), "E": dependents(),
        ])
        def deps = a.getRelevantDependents(["A", "C", "will not be reached"], IntSets.EMPTY_SET)

        expect:
        deps.dependencyToAll
    }

    def "knows when input class is a dependency to all"() {
        def a = analysis([
            "A": dependents("B"), "B": dependents(),
            "C": dependentSet(true, []),
        ])
        expect:
        !a.isDependencyToAll("A")
        a.isDependencyToAll("C")
        !a.isDependencyToAll("Unknown")
    }

    def "all classes are dependencies to all if a full rebuild cause is given"() {
        def a = analysis(
            [:], [:], [:], empty(), "Some cause"
        )

        expect:
        a.isDependencyToAll("DoesNotMatter")
    }

    private static DependentsSet dependentSet(boolean dependencyToAll, Collection<String> dependentClasses) {
        dependencyToAll ? DependentsSet.dependencyToAll() : dependents(dependentClasses as Set)
    }
}
