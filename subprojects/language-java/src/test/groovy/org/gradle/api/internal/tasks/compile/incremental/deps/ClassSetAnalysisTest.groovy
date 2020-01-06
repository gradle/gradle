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
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.*

class ClassSetAnalysisTest extends Specification {

    ClassSetAnalysis analysis(Map<String, DependentsSet> dependents,
                              Map<String, IntSet> classToConstants = [:],
                              DependentsSet aggregatedTypes = empty(), DependentsSet dependentsOnAll = empty(), String fullRebuildCause = null) {
        new ClassSetAnalysis(
            new ClassSetAnalysisData(dependents.keySet(), dependents, classToConstants, fullRebuildCause),
            new AnnotationProcessingData([:], aggregatedTypes.getAllDependentClasses(), dependentsOnAll.getAllDependentClasses(), [:], dependentsOnAll.dependentResources, null)
        )
    }

    def "returns empty analysis"() {
        def a = analysis([:])
        expect: a.getRelevantDependents("Foo", IntSets.EMPTY_SET).getAllDependentClasses().isEmpty()
    }

    def "does not recurse private dependencies"() {
        def a = analysis([
            "a": dependentSet(false, ["b"], []),
            "b": dependentSet(false, ["c"], ["d"]),
            "c": dependentSet(false, [], []),
            "d": dependentSet(false, ["e"], []),
            "e": dependentSet(false, [], [])
        ])
        /*
        Class a {
            private b _b;
        }
        Class b {
            private c _c;
            public d _d;
        }
        Class c {
        }
        Class d {
            private e _e;
        }
        Class e {
        }
         */

        when:
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == ['b'] as Set

        when:
        deps = a.getRelevantDependents("b", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == ['d'] as Set
        deps.privateDependentClasses == ['c'] as Set

        when:
        deps = a.getRelevantDependents("c", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == [] as Set

        when:
        deps = a.getRelevantDependents("d", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == ['e'] as Set

        when:
        deps = a.getRelevantDependents("e", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == [] as Set
    }

    def "does not recurse if root class is a dependency to all"() {
        def a = analysis(["Foo": dependentSet(true, [], ["Bar"])])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.dependencyToAll

        when: deps.getAllDependentClasses()
        then: thrown(UnsupportedOperationException)
    }

    def "marks as dependency to all only if root class is a dependency to all"() {
        def a = analysis([
            "a": dependentSet(false, [], ['b']),
            'b': dependentSet(true, [], []),
            "c": dependentSet(true, [], [])
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ['b'] as Set
        !deps.dependencyToAll
    }

    def "recurses nested dependencies"() {
        def a = analysis([
            "Foo": dependentClasses([] as Set, ["Bar"] as Set),
            "Bar": dependentClasses([] as Set, ["Baz"] as Set),
            "Baz": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["Bar", "Baz"] as Set
        a.getRelevantDependents("Bar", IntSets.EMPTY_SET).getAllDependentClasses() == ["Baz"] as Set
        a.getRelevantDependents("Baz", IntSets.EMPTY_SET).getAllDependentClasses() == [] as Set
    }

    def "recurses multiple dependencies"() {
        def a = analysis([
            "a": dependentClasses([] as Set, ["b", "c"] as Set),
            "b": dependentClasses([] as Set, ["d"] as Set),
            "c": dependentClasses([] as Set, ["e"] as Set),
            "d": dependentClasses([] as Set, [] as Set),
            "e": dependentClasses([] as Set, [] as Set)
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["b", "c", "d", "e"] as Set
    }

    def "removes self from dependents"() {
        def a = analysis([
            "Foo": dependentClasses([] as Set, ["Foo"] as Set)
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == [] as Set
    }

    def "handles dependency cycles"() {
        def a = analysis([
            "Foo": dependentClasses([] as Set, ["Bar"] as Set),
            "Bar": dependentClasses([] as Set, ["Baz"] as Set),
            "Baz": dependentClasses([] as Set, ["Foo"] as Set),
        ])
        def deps = a.getRelevantDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["Bar", "Baz"] as Set
    }

    def "recurses but filters out inner classes"() {
        def a = analysis([
            "a": dependentClasses([] as Set, ['a$b', 'c'] as Set),
            'a$b': dependentClasses([] as Set, ['d'] as Set),
            "c": dependentClasses([] as Set, [] as Set),
            "d": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["c", "d", 'a$b'] as Set
    }

    def "handles cycles with inner classes"() {
        def a = analysis([
            "a": dependentClasses([] as Set, ['a$b'] as Set),
            'a$b': dependentClasses([] as Set, ['a$b', 'c'] as Set),
            "c": dependentClasses([] as Set, [] as Set)
        ])
        def deps = a.getRelevantDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["c", 'a$b'] as Set
    }

    def "provides dependents of all input classes"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, [] as Set),
            "C": dependentClasses([] as Set, ["D"] as Set), "D": dependentClasses([] as Set, [] as Set),
            "E": dependentClasses([] as Set, ["D"] as Set), "F": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.getRelevantDependents(["A", "E"], IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["D", "B"] as Set
    }

    def "provides recursive dependents of all input classes"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, ["C"] as Set), "C": dependentClasses([] as Set, [] as Set),
            "D": dependentClasses([] as Set, ["E"] as Set), "E": dependentClasses([] as Set, [] as Set),
            "F": dependentClasses([] as Set, ["G"] as Set), "G": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.getRelevantDependents(["A", "D"], IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["E", "B", "C"] as Set
    }

    def "some classes may depend on any change"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": empty(), "DependsOnAny" : dependentClasses([] as Set, ["C"] as Set)
        ], [:], empty(), dependentClasses([] as Set, ["DependsOnAny"] as Set) )
        def deps = a.getRelevantDependents(["A"], IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["DependsOnAny", "B", "C"] as Set
    }

    def "knows when any of the input classes is a dependency to all"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, [] as Set),
            "C": dependentSet(true, [], []),
            "D": dependentClasses([] as Set, ["E"] as Set), "E": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.getRelevantDependents(["A", "C", "will not be reached"], IntSets.EMPTY_SET)

        expect:
        deps.dependencyToAll
    }

    def "knows when input class is a dependency to all"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, [] as Set),
            "C": dependentSet(true, [], []),
        ])
        expect:
        !a.isDependencyToAll("A")
        a.isDependencyToAll("C")
        !a.isDependencyToAll("Unknown")
    }

    def "all classes are dependencies to all if a full rebuild cause is given"() {
        def a = analysis(
            [:], [:], empty(), empty(), "Some cause"
        )

        expect:
        a.isDependencyToAll("DoesNotMatter")
    }

    private static DependentsSet dependentSet(boolean dependencyToAll, Collection<String> privateClasses, Collection<String> accessibleClasses) {
        dependencyToAll ? DependentsSet.dependencyToAll() : dependentClasses(privateClasses as Set, accessibleClasses as Set)
    }
}
