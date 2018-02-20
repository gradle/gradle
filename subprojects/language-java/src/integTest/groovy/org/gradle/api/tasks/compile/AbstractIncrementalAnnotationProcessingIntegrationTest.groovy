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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.language.fixtures.AnnotationProcessorFixture
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractIncrementalAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    protected CompilationOutputsFixture outputs

    private TestFile annotationProjectDir
    private TestFile libraryProjectDir
    private TestFile processorProjectDir

    def setup() {
        executer.requireOwnGradleUserHomeDir()

        outputs = new CompilationOutputsFixture(file("build/classes"))

        annotationProjectDir = testDirectory.file("annotation").createDir()
        libraryProjectDir = testDirectory.file("library").createDir()
        processorProjectDir = testDirectory.file("processor").createDir()

        settingsFile << """
            include "annotation", "library", "processor"
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
            
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            
            compileJava {
                compileJava.options.incremental = true
                options.fork = true
            }
        """

        processorProjectDir.file("build.gradle") << """
            dependencies {
                implementation project(":annotation")
                implementation project(":library")
            }
        """
    }

    protected void withProcessor(AnnotationProcessorFixture processor) {
        processor.writeSupportLibraryTo(libraryProjectDir)
        processor.writeApiTo(annotationProjectDir)
        processor.writeAnnotationProcessorTo(processorProjectDir)
    }

    protected final File java(String... classBodies) {
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

    def "explicit -processor option overrides automatic detection"() {
        buildFile << """
            compileJava.options.compilerArgs << "-processor" << "unknown.Processor"
        """
        java "class A {}"

        expect:
        fails("compileJava")
        errorOutput.contains("java.lang.ClassNotFoundException: unknown.Processor")
    }
}
