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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.NonIncrementalProcessorFixture
import org.gradle.language.fixtures.ServiceRegistryProcessorFixture
import org.gradle.util.TextUtil

class SingleOriginIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {
    private HelperProcessorFixture helperProcessor

    @Override
    def setup() {
        helperProcessor = new HelperProcessorFixture()
        withProcessor(helperProcessor)
    }

    def "generated files are recompiled when annotated file changes"() {
        given:
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AHelper")
    }

    def "annotated files are not recompiled on unrelated changes"() {
        given:
        java "@Helper class A {}"
        def unrelated = java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        unrelated.text = "class Unrelated { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("Unrelated")
    }

    def "classes files of generated sources are deleted when annotated file is deleted"() {
        given:
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.delete()
        run "compileJava"

        then:
        outputs.deletedClasses("A", "AHelper")
    }

    def "generated files are deleted when annotated file is deleted"() {
        given:
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/classes/java/main/AHelper.java").exists()

        when:
        a.delete()
        run "compileJava"

        then:
        !file("build/classes/java/main/AHelper.java").exists()
    }

    def "generated files are deleted when processor is removed"() {
        given:
        def a = java "@Helper class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/classes/java/main/AHelper.java").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava"

        then:
        !file("build/classes/java/main/AHelper.java").exists()
    }

    def "all files are recompiled when processor changes"() {
        given:
        def a = java "@Helper class A {}"
        outputs.snapshot { run "compileJava" }

        when:
        helperProcessor.suffix = "world"
        withProcessor(helperProcessor)
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AHelper")
    }

    def "all files are recompiled if compiler does not support incremental annotation processing"() {
        given:
        buildFile << "compileJava.options.forkOptions.executable = '${TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javacExecutable)}'"
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        outputs.recompiledClasses("A", "AHelper", "Unrelated")

        and:
        outputContains("the chosen compiler did not support incremental annotation processing")
    }

    def "all files are recompiled if a generated source file is deleted"() {
        given:
        java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("build/classes/java/main/AHelper.java").delete()
        run "compileJava"

        then:
        outputs.recompiledClasses('A', "AHelper", "Unrelated")
    }

    def "all files are recompiled if a generated class is deleted"() {
        given:
        java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("build/classes/java/main/AHelper.class").delete()
        run "compileJava"

        then:
        outputs.recompiledClasses('A', "AHelper", "Unrelated")
    }

    def "processors must provide an originating element for each source element"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Thing class A {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Generated type 'AThing' must have exactly one originating element, but had 0.")
    }

    def "processors can't access resources"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Thing class A {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Incremental annotation processors are not allowed to read resources.")
        errorOutput.contains("Incremental annotation processors are not allowed to create resources.")
    }

    def "processors cannot provide multiple originating elements"() {
        given:
        withProcessor(new ServiceRegistryProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Service class A {}"
        java "@Service class B {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Generated type 'ServiceRegistry' must have exactly one originating element, but had 2.")
    }
}
