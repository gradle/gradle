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

import static org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet.dependents

class ClassDependencyInfoTest extends Specification {

    def "returns empty info"() {
        def info = new ClassDependencyInfo([:])
        expect: info.getRelevantDependents("Foo").dependentClasses.isEmpty()
    }

    def "does not recurse if root class is a dependency to all"() {
        def info = new ClassDependencyInfo(["Foo": new DefaultDependentsSet(true, ["Bar"])])
        def deps = info.getRelevantDependents("Foo")

        expect:
        deps.dependencyToAll

        when: deps.getDependentClasses()
        then: thrown(UnsupportedOperationException)
    }

    def "marks as dependency to all only if root class is a dependency to all"() {
        def info = new ClassDependencyInfo([
                "a":   new DefaultDependentsSet(false, ['b']),
                'b': new DefaultDependentsSet(true, ['c']),
                "c": new DefaultDependentsSet(true, [])
        ])
        def deps = info.getRelevantDependents("a")

        expect:
        deps.dependentClasses == ['b', 'c'] as Set
        !deps.dependencyToAll
    }

    def "recurses nested dependencies"() {
        def info = new ClassDependencyInfo([
                "Foo": dependents("Bar"),
                "Bar": dependents("Baz"),
                "Baz": dependents(),
        ])
        def deps = info.getRelevantDependents("Foo")

        expect:
        deps.dependentClasses == ["Bar", "Baz"] as Set
        info.getRelevantDependents("Bar").dependentClasses == ["Baz"] as Set
        info.getRelevantDependents("Baz").dependentClasses == [] as Set
    }

    def "recurses multiple dependencies"() {
        def info = new ClassDependencyInfo([
                "a": dependents("b", "c"),
                "b": dependents("d"),
                "c": dependents("e"),
                "d": dependents(),
                "e": dependents()
        ])
        def deps = info.getRelevantDependents("a")

        expect:
        deps.dependentClasses == ["b", "c", "d", "e"] as Set
    }

    def "removes self from dependents"() {
        def info = new ClassDependencyInfo([
                "Foo": dependents("Foo")
        ])
        def deps = info.getRelevantDependents("Foo")

        expect:
        deps.dependentClasses == [] as Set
    }

    def "handles dependency cycles"() {
        def info = new ClassDependencyInfo([
                "Foo": dependents("Bar"),
                "Bar": dependents("Baz"),
                "Baz": dependents("Foo"),
        ])
        def deps = info.getRelevantDependents("Foo")

        expect:
        deps.dependentClasses == ["Bar", "Baz"] as Set
    }

    def "recurses but filters out inner classes"() {
        def info = new ClassDependencyInfo([
                "a":   dependents('a$b', 'c'),
                'a$b': dependents('d'),
                "c": dependents(),
                "d": dependents(),
        ])
        def deps = info.getRelevantDependents("a")

        expect:
        deps.dependentClasses == ["c", "d"] as Set
    }

    def "handles cycles with inner classes"() {
        def info = new ClassDependencyInfo([
                "a":   dependents('a$b'),
                'a$b': dependents('a', 'c'),
                "c": dependents()
        ])
        def deps = info.getRelevantDependents("a")

        expect:
        deps.dependentClasses == ["c"] as Set
    }
}
