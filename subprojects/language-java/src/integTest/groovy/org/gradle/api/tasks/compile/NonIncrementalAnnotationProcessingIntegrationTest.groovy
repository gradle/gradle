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

package org.gradle.api.tasks.compile;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.language.fixtures.AnnotationProcessorFixture

class NonIncrementalAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def setup() {
        executer.requireOwnGradleUserHomeDir()

        outputs = new CompilationOutputsFixture(file("build/classes"))

        def annotationProcessorProjectDir = testDirectory.file("annotation-processor").createDir()

        settingsFile << """
            include "annotation-processor"
        """
        buildFile << """
            apply plugin: 'java'
            
            dependencies {
                compileOnly project(":annotation-processor")
                annotationProcessor project(":annotation-processor")
            }
            
            compileJava {
                compileJava.options.incremental = true
                options.fork = true
            }
        """

        annotationProcessorProjectDir.file("build.gradle") << """
            apply plugin: "java"
        """

        def fixture = new AnnotationProcessorFixture()
        fixture.writeSupportLibraryTo(annotationProcessorProjectDir)
        fixture.writeApiTo(annotationProcessorProjectDir)
        fixture.writeAnnotationProcessorTo(annotationProcessorProjectDir)
    }

    private File java(String... classBodies) {
        File out
        for (String body : classBodies) {
            def className = (body =~ /(?s).*?class (\w+) .*/)[0][1]
            assert className: "unable to find class name"
            def f = file("src/main/java/${className}.java")
            f.createFile()
            f.text = body
            out = f
        }
        out
    }

    def "all sources are recompiled when any class changes"() {
        def a = java "@Helper class A {}"
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AHelper", "B")
    }

    def "the user is informed about non-incremental processors"() {
        def a = java "@Helper class A {}"

        when:
        run "compileJava"
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        output.contains("The following annotation processors were detected:")
        output.contains("Processor")
    }
}
