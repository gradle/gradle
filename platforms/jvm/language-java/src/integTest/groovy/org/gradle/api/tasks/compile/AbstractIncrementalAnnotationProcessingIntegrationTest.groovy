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
import org.gradle.language.fixtures.CompileJavaBuildOperationsFixture
import org.gradle.test.fixtures.file.TestFile
import org.intellij.lang.annotations.Language

abstract class AbstractIncrementalAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    protected CompileJavaBuildOperationsFixture operations
    protected CompilationOutputsFixture outputs

    protected TestFile annotationProjectDir
    protected TestFile libraryProjectDir
    protected TestFile processorProjectDir

    def setup() {
        executer.requireOwnGradleUserHomeDir()

        operations = new CompileJavaBuildOperationsFixture(executer, testDirectoryProvider)
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

                testCompileOnly project(":annotation")
                testAnnotationProcessor project(":processor")
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

    protected final File java(@Language("java") String... classBodies) {
        javaInPackage('', classBodies)
    }

    protected final File javaInPackage(String packageName, @Language("java") String... classBodies) {
        File out
        def packageStatement = packageName.empty ? "" : "package ${packageName};\n"
        def packagePathPrefix = packageName.empty ? '' : "${packageName.replace('.', '/')}/"
        for (String body : classBodies) {
            def className = (body =~ /(?s).*?class (\w+) .*/)[0][1]
            assert className: "unable to find class name"
            def f = file("src/main/java/${packagePathPrefix}${className}.java")
            f.createFile()
            f.text = packageStatement
            f << body
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
        failure.assertHasCause("Annotation processor 'unknown.Processor' not found")
    }

    def "recompiles when a resource changes"() {
        given:
        buildFile << """
            compileJava.inputs.dir 'src/main/resources'
        """
        java("class A {}")
        java("class B {}")
        def resource = file("src/main/resources/foo.txt")
        resource.text = 'foo'

        outputs.snapshot { succeeds 'compileJava' }

        when:
        resource.text = 'bar'

        then:
        succeeds 'compileJava'
        outputs.recompiledClasses("A", "B")
    }

    def "recompiles when a resource is removed"() {
        given:
        buildFile << """
            compileJava.inputs.dir 'src/main/resources'
        """
        java("class A {}")
        java("class B {}")
        def resource = file("src/main/resources/foo.txt")
        resource.text = 'foo'

        outputs.snapshot { succeeds 'compileJava' }

        when:
        resource.delete()

        then:
        succeeds 'compileJava'
        outputs.recompiledClasses("A", "B")
    }

    def "compilation is incremental when an empty directory is added"() {
        given:
        def a = java("class A {}")
        java("class B {}")

        outputs.snapshot { succeeds 'compileJava' }

        when:
        a.text = "class A { /*change*/ }"
        file('src/main/java/different').createDir()

        then:
        succeeds 'compileJava'
        outputs.recompiledClasses("A")
    }
}
