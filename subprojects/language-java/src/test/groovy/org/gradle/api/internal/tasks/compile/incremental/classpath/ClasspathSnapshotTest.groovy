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

class ClasspathSnapshotTest extends Specification {

    def analysis = new ClassSetAnalysisData()

    private ClasspathSnapshot snapshot(Map<String, HashCode> hashes, ClassSetAnalysisData a) {
        new ClasspathSnapshot([new ClasspathEntrySnapshotData(HashCode.fromInt(0x1234), hashes, a)])
    }

    def "knows when there are no affected classes since some other snapshot"() {
        ClasspathSnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)], analysis)
        ClasspathSnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb)], analysis)

        expect:
        s1.getChangesSince(s2).dependents.isEmpty()
    }

    def "knows when there are changed classes since other snapshot"() {
        ClasspathSnapshot s1 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbb), "C": HashCode.fromInt(0xcc)], analysis)
        ClasspathSnapshot s2 = snapshot(["A": HashCode.fromInt(0xaa), "B": HashCode.fromInt(0xbbbb)], analysis)

        expect:
        s1.getChangesSince(s2).dependents.allDependentClasses == ["B", "C"] as Set
        s2.getChangesSince(s1).dependents.allDependentClasses == ["B", "C"] as Set
    }
}
