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



package org.gradle.api.internal.tasks.compile.incremental.jar

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo
import org.gradle.api.internal.tasks.compile.incremental.deps.DependencyToAll
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet.dependents

class JarSnapshotTest extends Specification {

    def info = Stub(ClassDependencyInfo)

    def setup() {
        info.getRelevantDependents(_ as String) >> Stub(DependentsSet)
    }

    def "knows when there are no affected classes since some other snapshot"() {
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes], info)

        expect:
        s1.getAffectedClassesSince(s2).dependentClasses.isEmpty()
    }

    def "knows when there are extra/missing classes since some other snapshot"() {
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes], info)

        expect:
        s1.getAffectedClassesSince(s2).dependentClasses.isEmpty() //ignore class additions
        s2.getAffectedClassesSince(s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when there are changed classes since other snapshot"() {
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes, "B": "BB".bytes], info)

        expect:
        s1.getAffectedClassesSince(s2).dependentClasses == ["B"] as Set
        s2.getAffectedClassesSince(s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class change"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes, "C": "CC".bytes], info)

        info.getRelevantDependents("C") >> dependents("B")

        expect:
        s1.getAffectedClassesSince(s2).dependentClasses == ["B", "C"] as Set
        s2.getAffectedClassesSince(s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class removal"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes], info)

        info.getRelevantDependents("C") >> dependents("B")

        expect:
        s1.getAffectedClassesSince(s2).dependentClasses.isEmpty()
        s2.getAffectedClassesSince(s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when class is dependency to all"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = new JarSnapshot(["A": "A".bytes, "B": "B".bytes], info)
        JarSnapshot s2 = new JarSnapshot(["A": "A".bytes, "B": "BB".bytes], info)

        info.getRelevantDependents("B") >> new DependencyToAll()

        expect:
        s1.getAffectedClassesSince(s2).isDependencyToAll()
        s2.getAffectedClassesSince(s1).isDependencyToAll()
    }
}
