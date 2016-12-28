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

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer
import spock.lang.Specification
import spock.lang.Subject

class ClassFilesAnalyzerTest extends Specification {

    def classAnalyzer = Mock(ClassDependenciesAnalyzer)
    def accumulator = Mock(ClassDependentsAccumulator)
    @Subject analyzer = new ClassFilesAnalyzer(classAnalyzer, "org.foo", accumulator)

    def "does not visit dirs"() {
        when: analyzer.visitDir(null)
        then: 0 * _
    }

    def "does not visit non .class files"() {
        def details = Stub(FileVisitDetails) { getName() >> "foo.xml"}
        when: analyzer.visitFile(details)
        then: 0 * _
    }

    def "is sensitive to package prefix"() {
        def details = Stub(FileVisitDetails) { getPath() >> "com/foo/Foo.class"}
        when: analyzer.visitFile(details)
        then: 0 * _
    }

    def "accumulates dependencies"() {
        def details = Stub(FileVisitDetails) {
            getPath() >> "org/foo/Foo.class"
            getFile() >> new File("Foo.class")
        }
        def classNames = ["A"] as Set
        def constants = [1] as Set
        def literals = [2] as Set

        when:
        analyzer.visitFile(details)

        then:
        1 * classAnalyzer.getClassAnalysis("org.foo.Foo", new File("Foo.class")) >> new ClassAnalysis(classNames, true, constants, literals)
        1 * accumulator.addClass("org.foo.Foo", true, classNames, constants, literals)
        0 * _
    }
}
