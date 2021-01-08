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

package org.gradle.api.internal.tasks.compile.incremental.classpath

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class ClasspathEntrySnapshotTest extends Specification {

    def analysis = Stub(ClassSetAnalysisData)

    private ClasspathEntrySnapshot snapshot(Map<String, HashCode> hashes, ClassSetAnalysisData a) {
        new ClasspathEntrySnapshot(new ClasspathEntrySnapshotData(HashCode.fromInt(0x1234), hashes, a))
    }

    private Set<String> altered(ClasspathEntrySnapshot s1, ClasspathEntrySnapshot s2) {
        s1.getChangedClassesSince(s2).modified
    }

    def "knows when there are no affected classes since some other snapshot"() {
        ClasspathEntrySnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)], analysis)
        ClasspathEntrySnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)], analysis)

        expect:
        altered(s1, s2).isEmpty()
    }

    def "knows when there are extra/missing classes since some other snapshot"() {
        ClasspathEntrySnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb), "C": HashCode.fromInt(0xcc)], analysis)
        ClasspathEntrySnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa)], analysis)

        expect:
        altered(s1, s2).isEmpty() //ignore class additions
        altered(s2, s1) == ["B", "C"] as Set
    }

    def "knows when there are changed classes since other snapshot"() {
        ClasspathEntrySnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb), "C": HashCode.fromInt(0xcc)], analysis)
        ClasspathEntrySnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbbbb)], analysis)

        expect:
        altered(s1, s2) == ["B"] as Set
        altered(s2, s1) == ["B", "C"] as Set
    }

    def "knows added classes"() {
        ClasspathEntrySnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb), "C": HashCode.fromInt(0xcc)], analysis)
        ClasspathEntrySnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa)], analysis)
        ClasspathEntrySnapshot s3 = snapshot([:], analysis)

        expect:
        s1.getChangedClassesSince(s2).added == ["B", "C"] as Set
        s2.getChangedClassesSince(s1).added == [] as Set
        s1.getChangedClassesSince(s3).added == ["A", "B", "C"] as Set
    }
}
