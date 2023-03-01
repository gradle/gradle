/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class AnnotationProcessorDiscoveringCompilerTest extends Specification {
    JavaCompileSpec spec = new DefaultJavaCompileSpec().with {
        compileOptions = TestUtil.newInstance(CompileOptions, TestUtil.objectFactory())
        it
    }
    AnnotationProcessorDetector detector = Stub(AnnotationProcessorDetector)
    Compiler<JavaCompileSpec> delegate = Stub(Compiler)

    AnnotationProcessorDiscoveringCompiler compiler = new AnnotationProcessorDiscoveringCompiler(delegate, detector)

    def "when neither processor path nor processor option are given, no processors are used"() {
        when:
        compiler.execute(spec)
        then:
        spec.effectiveAnnotationProcessors == [] as Set
    }

    def "when only processor path is given, all processors on the path are used"() {
        given:
        def proc1 = Stub(AnnotationProcessorDeclaration)
        def proc2 = Stub(AnnotationProcessorDeclaration)
        detector.detectProcessors(_) >> [
            "Foo": proc1,
            "Bar": proc2
        ]

        when:
        compiler.execute(spec)

        then:
        spec.effectiveAnnotationProcessors == [proc1, proc2] as Set
    }

    def "when processor option is given, only those processors are used"() {
        given:
        def proc1 = Stub(AnnotationProcessorDeclaration)
        def proc2 = Stub(AnnotationProcessorDeclaration)
        detector.detectProcessors(_) >> [
            "Foo": proc1,
            "Bar": proc2
        ]
        spec.compileOptions.compilerArgs = ["-processor", "Foo"]

        when:
        compiler.execute(spec)

        then:
        spec.effectiveAnnotationProcessors == [proc1] as Set
    }

    def "when both path and processor option are given, the order of the processor option is used"() {
        given:
        def proc1 = Stub(AnnotationProcessorDeclaration)
        def proc2 = Stub(AnnotationProcessorDeclaration)
        detector.detectProcessors(_) >> [
            "Foo": proc1,
            "Bar": proc2
        ]
        spec.compileOptions.compilerArgs = ["-processor", "Bar,Foo"]

        when:
        compiler.execute(spec)

        then:
        spec.effectiveAnnotationProcessors.toList() == [proc2, proc1]
    }

    def "when processor option is given and no info is available on the path, assume it is non-incremental"() {
        given:
        spec.compileOptions.compilerArgs = ["-processor", "Foo"]

        when:
        compiler.execute(spec)

        then:
        spec.effectiveAnnotationProcessors == [new AnnotationProcessorDeclaration("Foo", IncrementalAnnotationProcessorType.UNKNOWN)] as Set
    }

    @Issue("gradle/gradle#1471")
    def "fails when -processor is the last compiler arg"() {
        given:
        spec.compileOptions.compilerArgs = ["-Xthing", "-processor"]

        when:
        compiler.execute(spec)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == 'No processor specified for compiler argument -processor in requested compiler args: -Xthing -processor'
    }
}
