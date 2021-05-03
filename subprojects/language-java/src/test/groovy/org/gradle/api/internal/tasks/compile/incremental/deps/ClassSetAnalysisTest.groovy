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

import com.google.common.collect.Maps
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.dependentClasses
import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.empty

class ClassSetAnalysisTest extends Specification {

    static def hash = HashCode.fromInt(0)

    CompilerApiData compilerApiData = Stub(CompilerApiData)
    ClassSetAnalysis analysis(Map<String, DependentsSet> dependents,
                                     Map<String, IntSet> classToConstants = [:],
                                     DependentsSet aggregatedTypes = empty(), DependentsSet dependentsOnAll = empty(), String fullRebuildCause = null) {
        new ClassSetAnalysis(
            new ClassSetAnalysisData(Maps.transformValues(dependents) { hash }, dependents, classToConstants, fullRebuildCause),
            new AnnotationProcessingData([:], aggregatedTypes.getAllDependentClasses(), dependentsOnAll.getAllDependentClasses(), [:], dependentsOnAll.dependentResources, null),
            compilerApiData
        )
    }

    static ClassSetAnalysis snapshot(Map<String, HashCode> hashes) {
        new ClassSetAnalysis(new ClassSetAnalysisData(hashes, [:], [:], null))
    }

    def "knows when there are no affected classes since some other snapshot"() {
        ClassSetAnalysis s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)])
        ClassSetAnalysis s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)])

        expect:
        s1.findChangesSince(s2).dependents.isEmpty()
    }

    def "knows when there are changed classes since other snapshot"() {
        ClassSetAnalysis s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb), "C": HashCode.fromInt(0xcc)])
        ClassSetAnalysis s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbbbb)])

        expect:
        s1.findChangesSince(s2).dependents.allDependentClasses == ["B"] as Set
        s2.findChangesSince(s1).dependents.allDependentClasses == ["B", "C"] as Set
    }

    def "returns empty analysis"() {
        def a = analysis([:])
        expect: a.findTransitiveDependents("Foo", IntSets.EMPTY_SET).getAllDependentClasses().isEmpty()
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
        def deps = a.findTransitiveDependents("a", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == ['b'] as Set

        when:
        deps = a.findTransitiveDependents("b", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == ['d'] as Set
        deps.privateDependentClasses == ['c'] as Set

        when:
        deps = a.findTransitiveDependents("c", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == [] as Set

        when:
        deps = a.findTransitiveDependents("d", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == ['e'] as Set

        when:
        deps = a.findTransitiveDependents("e", IntSets.EMPTY_SET)
        then:
        deps.accessibleDependentClasses == [] as Set
        deps.privateDependentClasses == [] as Set
    }

    def "does not recurse if root class is a dependency to all"() {
        def a = analysis(["Foo": dependentSet(true, [], ["Bar"])])
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

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
        def deps = a.findTransitiveDependents("a", IntSets.EMPTY_SET)

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
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["Bar", "Baz"] as Set
        a.findTransitiveDependents("Bar", IntSets.EMPTY_SET).getAllDependentClasses() == ["Baz"] as Set
        a.findTransitiveDependents("Baz", IntSets.EMPTY_SET).getAllDependentClasses() == [] as Set
    }

    def "recurses multiple dependencies"() {
        def a = analysis([
            "a": dependentClasses([] as Set, ["b", "c"] as Set),
            "b": dependentClasses([] as Set, ["d"] as Set),
            "c": dependentClasses([] as Set, ["e"] as Set),
            "d": dependentClasses([] as Set, [] as Set),
            "e": dependentClasses([] as Set, [] as Set)
        ])
        def deps = a.findTransitiveDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["b", "c", "d", "e"] as Set
    }

    def "removes self from dependents"() {
        def a = analysis([
            "Foo": dependentClasses([] as Set, ["Foo"] as Set)
        ])
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == [] as Set
    }

    def "handles dependency cycles"() {
        def a = analysis([
            "Foo": dependentClasses([] as Set, ["Bar"] as Set),
            "Bar": dependentClasses([] as Set, ["Baz"] as Set),
            "Baz": dependentClasses([] as Set, ["Foo"] as Set),
        ])
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

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
        def deps = a.findTransitiveDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["c", "d", 'a$b'] as Set
    }

    def "handles cycles with inner classes"() {
        def a = analysis([
            "a": dependentClasses([] as Set, ['a$b'] as Set),
            'a$b': dependentClasses([] as Set, ['a$b', 'c'] as Set),
            "c": dependentClasses([] as Set, [] as Set)
        ])
        def deps = a.findTransitiveDependents("a", IntSets.EMPTY_SET)

        expect:
        deps.getAllDependentClasses() == ["c", 'a$b'] as Set
    }

    def "provides dependents of all input classes"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, [] as Set),
            "C": dependentClasses([] as Set, ["D"] as Set), "D": dependentClasses([] as Set, [] as Set),
            "E": dependentClasses([] as Set, ["D"] as Set), "F": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.findTransitiveDependents(["A", "E"], [:])

        expect:
        deps.getAllDependentClasses() == ["D", "B"] as Set
    }

    def "provides recursive dependents of all input classes"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, ["C"] as Set), "C": dependentClasses([] as Set, [] as Set),
            "D": dependentClasses([] as Set, ["E"] as Set), "E": dependentClasses([] as Set, [] as Set),
            "F": dependentClasses([] as Set, ["G"] as Set), "G": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.findTransitiveDependents(["A", "D"], [:])

        expect:
        deps.getAllDependentClasses() == ["E", "B", "C"] as Set
    }

    def "some classes may depend on any change"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": empty(), "DependsOnAny" : dependentClasses([] as Set, ["C"] as Set)
        ], [:], dependentClasses([] as Set, ["A"] as Set), dependentClasses([] as Set, ["DependsOnAny"] as Set) )
        def deps = a.findTransitiveDependents(["A"], [:])

        expect:
        deps.getAllDependentClasses() == ["DependsOnAny", "B", "C"] as Set
    }

    def "knows when any of the input classes is a dependency to all"() {
        def a = analysis([
            "A": dependentClasses([] as Set, ["B"] as Set), "B": dependentClasses([] as Set, [] as Set),
            "C": dependentSet(true, [], []),
            "D": dependentClasses([] as Set, ["E"] as Set), "E": dependentClasses([] as Set, [] as Set),
        ])
        def deps = a.findTransitiveDependents(["A", "C", "will not be reached"], [:])

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

    def "marks as dependency to all if constants has change and compilerApi is not available"() {
        given:
        def a = analysis([:], [:], empty(), empty(), null)
        compilerApiData.isAvailable() >> false

        when:
        def deps = a.findTransitiveDependents("Foo", IntSet.of(1))

        then:
        deps.isDependencyToAll()
    }

    def "find class constant dependents"() {
        given:
        def a = analysis([:], [:], empty(), empty(), null)
        compilerApiData.isAvailable() >> true
        compilerApiData.accessibleConstantDependentsForClass("Foo") >> ["Bar"]
        compilerApiData.privateConstantDependentsForClass("Foo") >> ["BarBar"]

        when:
        def deps = a.findTransitiveDependents("Foo", IntSet.of(1))

        then:
        deps.getAccessibleDependentClasses() == ["Bar"] as Set
        deps.getPrivateDependentClasses() == ["BarBar"] as Set
    }

    def "find class constant dependents when constants hash analysis returns empty set"() {
        given:
        def a = analysis([:], [:], empty(), empty(), null)
        compilerApiData.isAvailable() >> true
        compilerApiData.accessibleConstantDependentsForClass("Foo") >> ["Bar"]

        when:
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

        then:
        deps.getAccessibleDependentClasses() == ["Bar"] as Set
    }

    def "find class constant dependents recursively"() {
        given:
        def a = analysis([:], [:], empty(), empty(), null)
        compilerApiData.isAvailable() >> true
        compilerApiData.accessibleConstantDependentsForClass("Foo") >> ["Bar"]
        compilerApiData.accessibleConstantDependentsForClass("Bar") >> ["FooBar"]
        compilerApiData.accessibleConstantDependentsForClass("FooBar") >> ["BarFoo"]
        compilerApiData.accessibleConstantDependentsForClass("X") >> ["Y"]

        when:
        def deps = a.findTransitiveDependents("Foo", IntSets.EMPTY_SET)

        then:
        deps.getAccessibleDependentClasses() == ["Bar", "FooBar", "BarFoo"] as Set
    }

    private static DependentsSet dependentSet(boolean dependencyToAll, Collection<String> privateClasses, Collection<String> accessibleClasses) {
        dependencyToAll ? DependentsSet.dependencyToAll("reason") : dependentClasses(privateClasses as Set, accessibleClasses as Set)
    }
}
