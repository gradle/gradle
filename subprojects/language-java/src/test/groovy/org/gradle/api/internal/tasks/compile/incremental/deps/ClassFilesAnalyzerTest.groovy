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

import com.google.common.hash.HashCode
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.hash.FileHasher
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer
import spock.lang.Specification
import spock.lang.Subject

class ClassFilesAnalyzerTest extends Specification {

    def classAnalyzer = Mock(ClassDependenciesAnalyzer)
    def accumulator = Mock(ClassDependentsAccumulator)
    def fileHasher = Mock(FileHasher)
    @Subject analyzer = new ClassFilesAnalyzer(classAnalyzer, fileHasher, accumulator)

    def "does not visit dirs"() {
        when: analyzer.visitDir(null)
        then: 0 * _
    }

    def "does not visit non .class files"() {
        def details = Stub(FileVisitDetails) { getName() >> "foo.xml"}
        when: analyzer.visitFile(details)
        then: 0 * _
    }

    def "accumulates dependencies"() {
        def hash = HashCode.fromInt(123)
        def file = new File("org/foo/Foo.class")
        def details = Stub(FileVisitDetails) {
            getFile() >> file
            getName() >> "Foo.class"
        }
        def classNames = ["A"] as Set
        def constants = [1] as Set
        def literals = [2] as Set
        def superTypes = ['B', 'C'] as Set
        def analysis = new ClassAnalysis("org.foo.Foo", classNames, true, constants, literals, superTypes)

        when:
        analyzer.visitFile(details)

        then:
        1 * fileHasher.hash(details) >> hash
        1 * classAnalyzer.getClassAnalysis(hash, details) >> analysis
        1 * accumulator.addClass(file, analysis)
        0 * _
    }
}
