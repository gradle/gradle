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

    private JarSnapshot snapshot(Map<String, byte[]> hashes, ClassDependencyInfo info) {
        new JarSnapshot(new JarSnapshotData(new byte[0], hashes, info))
    }

    private DependentsSet altered(JarSnapshot s1, JarSnapshot s2) {
        s1.getAffectedClassesSince(s2).altered
    }

    def "knows when there are no affected classes since some other snapshot"() {
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes, "B": "B".bytes], info)

        expect:
        altered(s1, s2).dependentClasses.isEmpty()
    }

    def "knows when there are extra/missing classes since some other snapshot"() {
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes], info)

        expect:
        altered(s1, s2).dependentClasses.isEmpty() //ignore class additions
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when there are changed classes since other snapshot"() {
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes, "B": "BB".bytes], info)

        expect:
        altered(s1, s2).dependentClasses == ["B"] as Set
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class change"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "CC".bytes], info)

        info.getRelevantDependents("C") >> dependents("B")

        expect:
        altered(s1, s2).dependentClasses == ["B", "C"] as Set
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class removal"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes, "B": "B".bytes], info)

        info.getRelevantDependents("C") >> dependents("B")

        expect:
        altered(s1, s2).dependentClasses.isEmpty()
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when class is dependency to all"() {
        def info = Mock(ClassDependencyInfo)
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes, "B": "BB".bytes], info)

        info.getRelevantDependents("B") >> new DependencyToAll()

        expect:
        altered(s1, s2).isDependencyToAll()
        altered(s2, s1).isDependencyToAll()
    }

    def "knows added classes"() {
        JarSnapshot s1 = snapshot(["A": "A".bytes, "B": "B".bytes, "C": "C".bytes], info)
        JarSnapshot s2 = snapshot(["A": "A".bytes], info)
        JarSnapshot s3 = snapshot([:], info)

        expect:
        s1.getAffectedClassesSince(s2).added == ["B", "C"] as Set
        s2.getAffectedClassesSince(s1).added == [] as Set
        s1.getAffectedClassesSince(s3).added == ["A", "B", "C"] as Set
    }
}
