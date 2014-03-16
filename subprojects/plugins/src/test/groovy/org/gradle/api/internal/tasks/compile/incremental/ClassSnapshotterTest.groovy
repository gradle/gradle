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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.hash.Hasher
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo
import spock.lang.Specification
import spock.lang.Subject

class ClassSnapshotterTest extends Specification {

    def hasher = Mock(Hasher)
    def analyzer = Mock(ClassDependenciesAnalyzer)
    def info = Mock(ClassDependencyInfo)
    @Subject snapshotter = new ClassSnapshotter(hasher, analyzer)

    def "creates snapshot for class that is dependent to all"() {
        when:
        def s = snapshotter.createSnapshot("Foo", new File("f"), info)

        then:
        1 * analyzer.getClassAnalysis("Foo", new File("f")) >> Mock(ClassAnalysis) {
            isDependentToAll() >> true
        }
        1 * hasher.hash(new File("f")) >> "f".bytes
        0 * _

        s.dependentClasses == null
        s.hash == "f".bytes
    }

    def "creates snapshot for a class"() {
        when:
        def s = snapshotter.createSnapshot("Foo", new File("f"), info)

        then:
        1 * analyzer.getClassAnalysis("Foo", new File("f")) >> Mock(ClassAnalysis) {
            isDependentToAll() >> false
        }
        1 * info.getActualDependents("Foo") >> ["X", "Y"]
        1 * hasher.hash(new File("f")) >> "f".bytes
        0 * _

        s.dependentClasses == ["X", "Y"] as Set
        s.hash == "f".bytes
    }
}
