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

import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.NonIncrementalProcessorFixture
import org.gradle.language.fixtures.ServiceRegistryProcessorFixture

import static org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.AGGREGATING

class AggregatingIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {

    @Override
    def setup() {
        withProcessor(new ServiceRegistryProcessorFixture())
    }

    def "generated files are recompiled when any annotated file changes"() {
        def a = java "@Service class A {}"
        java "@Service class B {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Service class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "ServiceRegistry")
        serviceRegistryReferences("A", "B")
    }

    def "unrelated files are not recompiled when annotated file changes"() {
        def a = java "@Service class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Service class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "ServiceRegistry")
    }

    def "annotated files are reprocessed when an unrelated file changes"() {
        java "@Service class A {}"
        def unrelated = java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        unrelated.text = "class Unrelated { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("Unrelated", "ServiceRegistry")
    }

    def "annotated files are reprocessed when a new file is added"() {
        java "@Service class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        java "@Service class B {}"
        run "compileJava"

        then:
        outputs.recompiledClasses("ServiceRegistry", "B")
        serviceRegistryReferences("A", "B")
    }

    def "annotated files are reprocessed when a file is deleted"() {
        def a = java "@Service class A {}"
        java "@Service class B {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.delete()
        run "compileJava"

        then:
        outputs.deletedClasses("A")
        outputs.recompiledClasses("ServiceRegistry")
        serviceRegistryReferences("B")
        !serviceRegistryReferences("A")
    }

    def "classes depending on generated file are recompiled when source file changes"() {
        given:
        def a = java "@Service class A {}"
        java """class Dependent {
            private ServiceRegistry registry = new ServiceRegistry();
        }"""


        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Service class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "ServiceRegistry", "Dependent")
    }

    def "classes files of generated sources are deleted when annotated file is deleted"() {
        given:
        def a = java "@Service class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.delete()
        run "compileJava"

        then:
        outputs.deletedClasses("A", "ServiceRegistry")
    }

    def "generated files are deleted when annotated file is deleted"() {
        given:
        def a = java "@Service class A {}"
        java "class Unrelated {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()

        when:
        a.delete()
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        def a = java "@Service class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()

        and:
        outputs.deletedClasses("ServiceRegistry")
    }

    def "writing resources triggers a full recompilation"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().writingResources().withDeclaredType(IncrementalAnnotationProcessorType.AGGREGATING))
        def a = java "@Thing class A {}"
        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Thing class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because an annotation processor generated a resource.")
    }

    def "an isolating processor is also a valid aggregating processor"() {
        given:
        withProcessor(new HelperProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.AGGREGATING))
        java "@Helper class A {}"

        expect:
        succeeds "compileJava"
    }

    def "processors can provide multiple originating elements"() {
        given:
        java "@Service class A {}"
        java "@Service class B {}"

        expect:
        succeeds "compileJava"
    }

    def "aggregating processor do not work with source retention"() {
        given:
        annotationProjectDir.file("src/main/java/Service.java").text = """
            import java.lang.annotation.*;
            
            @Retention(RetentionPolicy.SOURCE)
            public @interface Service {
            }
"""

        def a = java "@Service class A {}"
        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Service class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because '@Service' has source retention. Aggregating annotation processors require class or runtime retention.")
    }

    def "reports aggregating processor in build operation"() {
        java "class Irrelevant {}"

        when:
        succeeds "compileJava"

        then:
        with(operations[':compileJava'].result.annotationProcessorDetails as List<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails>) {
            size() == 1
            first().className == 'ServiceProcessor'
            first().type == AGGREGATING.name()
        }
    }

    private boolean serviceRegistryReferences(String... services) {
        def registry = file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").text
        services.every() {
            registry.contains("get$it")
        }
    }
}
