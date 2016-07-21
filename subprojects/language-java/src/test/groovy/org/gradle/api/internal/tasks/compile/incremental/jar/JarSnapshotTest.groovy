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

import com.google.common.hash.HashCode
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.api.internal.tasks.compile.incremental.deps.DependencyToAll
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet.dependents

class JarSnapshotTest extends Specification {

    def analysis = Stub(ClassSetAnalysisData)

    private JarSnapshot snapshot(Map<String, HashCode> hashes, ClassSetAnalysisData a) {
        new JarSnapshot(new JarSnapshotData(HashCode.fromString("1234"), hashes, a))
    }

    private DependentsSet altered(JarSnapshot s1, JarSnapshot s2) {
        s1.getAffectedClassesSince(s2).altered
    }

    def "knows when there are no affected classes since some other snapshot"() {
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb")], analysis)

        expect:
        altered(s1, s2).dependentClasses.isEmpty()
    }

    def "knows when there are extra/missing classes since some other snapshot"() {
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cc")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa")], analysis)

        expect:
        altered(s1, s2).dependentClasses.isEmpty() //ignore class additions
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when there are changed classes since other snapshot"() {
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cc")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bbbb")], analysis)

        expect:
        altered(s1, s2).dependentClasses == ["B"] as Set
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class change"() {
        def analysis = Mock(ClassSetAnalysisData)
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cc")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cccc")], analysis)

        analysis.getDependents("C") >> dependents("B")
        analysis.getDependents("B") >> dependents()

        expect:
        altered(s1, s2).dependentClasses == ["B", "C"] as Set
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when transitive class is affected transitively via class removal"() {
        def analysis = Mock(ClassSetAnalysisData)
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cc")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb")], analysis)

        analysis.getDependents("C") >> dependents("B")
        analysis.getDependents("B") >> dependents()

        expect:
        altered(s1, s2).dependentClasses.isEmpty()
        altered(s2, s1).dependentClasses == ["B", "C"] as Set
    }

    def "knows when class is dependency to all"() {
        def analysis = Mock(ClassSetAnalysisData)
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bbbb")], analysis)

        analysis.getDependents("B") >> new DependencyToAll()

        expect:
        altered(s1, s2).isDependencyToAll()
        altered(s2, s1).isDependencyToAll()
    }

    def "knows added classes"() {
        JarSnapshot s1 = snapshot(["A": HashCode.fromString("aa"), "B": HashCode.fromString("bb"), "C": HashCode.fromString("cc")], analysis)
        JarSnapshot s2 = snapshot(["A": HashCode.fromString("aa")], analysis)
        JarSnapshot s3 = snapshot([:], analysis)

        expect:
        s1.getAffectedClassesSince(s2).added == ["B", "C"] as Set
        s2.getAffectedClassesSince(s1).added == [] as Set
        s1.getAffectedClassesSince(s3).added == ["A", "B", "C"] as Set
    }
}
