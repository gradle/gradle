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
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.language.fixtures.AnnotatedGeneratedClassProcessorFixture
import org.gradle.language.fixtures.AnnotationProcessorFixture
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.PackageInfoGeneratedClassProcessorFixture
import org.gradle.language.fixtures.ResourceGeneratingProcessorFixture
import org.gradle.language.fixtures.ServiceRegistryProcessorFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import javax.tools.StandardLocation

import static org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.AGGREGATING

class AggregatingIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {
    private static ServiceRegistryProcessorFixture writingResourcesTo(String location) {
        def serviceRegistryProcessor = new ServiceRegistryProcessorFixture()
        serviceRegistryProcessor.resourceLocation = location
        return serviceRegistryProcessor
    }

    @Override
    def setup() {
        withProcessor(writingResourcesTo(StandardLocation.CLASS_OUTPUT.toString()))
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
        outputs.recompiledFiles("A", "ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("A", "B")
    }

    def "incremental processing works on subsequent incremental compilations after failure"() {
        def a = java "@Service class A {}"
        java "@Service class B {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Service class A { public String foo() { return 0; } }"
        runAndFail "compileJava", "-d"

        then:
        outputs.noneRecompiled()
        outputContains("Deleting generated files: [${file("build/classes/java/main/ServiceRegistryResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java")}]")
        outputContains("Restoring stashed files: [" +
            "${file("build/classes/java/main/A.class")}, " +
            "${file("build/classes/java/main/ServiceRegistry.class")}, " +
            "${file("build/classes/java/main/ServiceRegistryResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java")}]"
        )

        when:
        a.text = "@Service class A { public int foo() { return 0; } }"
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("A", "B")
    }


    def "incremental processing works when new aggregating type is added on subsequent incremental compilations after failure"() {
        def a = java "@Service class A {}"
        java "@Service class B {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        def c = java "@Service class C { public String foo() { return 0; } }"
        runAndFail "compileJava", "-d"

        then:
        outputs.noneRecompiled()
        outputContains("Deleting generated files: [${file("build/classes/java/main/ServiceRegistryResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java")}]")
        outputContains("Restoring stashed files: [" +
            "${file("build/classes/java/main/ServiceRegistry.class")}, " +
            "${file("build/classes/java/main/ServiceRegistryResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java")}]"
        )

        when:
        c.text = "@Service class C { public int foo() { return 0; } }"
        run "compileJava"

        then:
        outputs.recompiledFiles("C", "ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("A", "B", "C")
    }

    def "unrelated files are not recompiled when annotated file changes"() {
        def a = java "@Service class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Service class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "ServiceRegistry", "ServiceRegistryResource.txt")
    }

    def "annotated files are reprocessed when an unrelated file changes"() {
        java "@Service class A {}"
        def unrelated = java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        unrelated.text = "class Unrelated { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("Unrelated", "ServiceRegistry", "ServiceRegistryResource.txt")
    }

    def "annotated files are reprocessed when a new file is added"() {
        java "@Service class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        java "@Service class B {}"
        run "compileJava"

        then:
        outputs.recompiledFiles("ServiceRegistry", "ServiceRegistryResource.txt", "B")
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
        outputs.recompiledFiles("ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("B")
        !serviceRegistryReferences("A")
    }

    @Issue("https://github.com/gradle/gradle/issues/13767")
    def "generated files with annotations are not reprocessed when a file is added (#label)"() {
        def fixture = new AnnotatedGeneratedClassProcessorFixture()
        if (generateClass) {
            fixture.generateClassFile = true
        }
        withProcessor(fixture)

        buildFile << """
            repositories {
                ${fixture.repositoriesBlock}
            }
        """

        makeGeneratedClassFilesAccessible()

        java "@Bean class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        java "@Bean class B {}"
        run "compileJava"

        then:
        outputs.recompiledFiles("B", "BHelper", "ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("AHelper", "BHelper")

        where:
        generateClass | label
        false         | "generate source files"
        true          | "generate class files"
    }

    @Issue("https://github.com/gradle/gradle/issues/17572")
    def "can have an aggregating annotation processor for package info"() {
        def fixture = new PackageInfoGeneratedClassProcessorFixture()
        withProcessor(fixture)
        buildFile << """
            repositories {
                ${fixture.repositoriesBlock}
            }
        """

        def processedPackage = 'com.foo'
        def a = javaInPackage processedPackage, "class A {}"
        javaInPackage processedPackage, "class B {}"
        annotatedPackageInfo(processedPackage, 'com.my.processor.Configuration')
        javaInPackage 'com.unprocessed', "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles('A', '$Configuration', 'package-info')
    }

    File annotatedPackageInfo(String packageName, String annotation) {
        file("src/main/java/${packageName.replace('.', '/')}/package-info.java") << """
            @${annotation}
            package ${packageName};
        """
    }

    private void makeGeneratedClassFilesAccessible() {
        // On Java 8 and earlier, generated classes are not automatically
        // available to the compile classpath so we need to expose them
        // manually
        if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9)) {
            buildFile << """
                tasks.withType(JavaCompile).configureEach {
                    classpath += files("\${buildDir}/classes/java/main")
                }
            """
        }
    }


    def "generated files with annotations are reprocessed when a file is deleted (#label)"() {
        def fixture = new AnnotatedGeneratedClassProcessorFixture()
        if (generateClass) {
            fixture.generateClassFile = true
        }
        withProcessor(fixture)

        buildFile << """
            repositories {
                ${fixture.repositoriesBlock}
            }
        """

        makeGeneratedClassFilesAccessible()

        def a = java "@Bean class A {}"
        java "@Bean class B {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.delete()
        run "compileJava"

        then:
        outputs.deletedClasses("A", "AHelper")
        outputs.recompiledFiles("ServiceRegistry", "ServiceRegistryResource.txt")
        serviceRegistryReferences("BHelper")
        !serviceRegistryReferences("AHelper")

        where:
        generateClass | label
        false         | "generate source files"
        true          | "generate class files"
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
        outputs.recompiledFiles("A", "ServiceRegistry", "ServiceRegistryResource.txt", "Dependent")
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
        outputs.deletedFiles("A", "ServiceRegistry", "ServiceRegistryResource.txt")
    }

    def "generated files are deleted when annotated file is deleted"() {
        given:
        withProcessor(writingResourcesTo(StandardLocation.SOURCE_OUTPUT.toString()))
        def a = java "@Service class A {}"
        java "class Unrelated {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistryResource.txt").exists()

        when:
        a.delete()
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistryResource.txt").exists()
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        withProcessor(writingResourcesTo(StandardLocation.SOURCE_OUTPUT.toString()))
        java "@Service class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()
        file("build/generated/sources/annotationProcessor/java/main/ServiceRegistryResource.txt").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistry.java").exists()
        !file("build/generated/sources/annotationProcessor/java/main/ServiceRegistryResource.txt").exists()

        and:
        outputs.deletedClasses("ServiceRegistry")
    }

    def "processors can generate identical resources in different locations"() {
        given:
        def locations = [StandardLocation.SOURCE_OUTPUT.toString(), StandardLocation.NATIVE_HEADER_OUTPUT.toString(), StandardLocation.CLASS_OUTPUT.toString()]
        withProcessor(new ResourceGeneratingProcessorFixture().withOutputLocations(locations).withDeclaredType(IncrementalAnnotationProcessorType.AGGREGATING))
        def a = java "@Thing class A {}"
        java "class Unrelated {}"

        when:
        outputs.snapshot { succeeds "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/A.txt").exists()
        file("build/generated/sources/headers/java/main/A.txt").exists()
        file("build/classes/java/main/A.txt").exists()

        when:
        a.delete()
        succeeds "compileJava"

        then: "they all get cleaned"
        outputs.deletedClasses("A")
        !file("build/generated/sources/annotationProcessor/java/main/A.txt").exists()
        !file("build/generated/sources/headers/java/main/A.txt").exists()
        !file("build/classes/java/main/A.txt").exists()
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
        with(operations[':compileJava'].result.annotationProcessorDetails as List<Object>) {
            size() == 1
            first().className == 'ServiceProcessor'
            first().type == AGGREGATING.name()
        }
    }

    def "updating an unrelated file doesn't delete generated resources built by an aggregating processor"() {
        def locations = [StandardLocation.SOURCE_OUTPUT.toString(), StandardLocation.NATIVE_HEADER_OUTPUT.toString(), StandardLocation.CLASS_OUTPUT.toString()]
        withProcessor(new ResourceGeneratingProcessorFixture().withOutputLocations(locations).withDeclaredType(IncrementalAnnotationProcessorType.AGGREGATING))
        java "@Thing class A {}"
        def unrelated = java "class Unrelated {}"

        when:
        outputs.snapshot { succeeds "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/A.txt").exists()
        file("build/generated/sources/headers/java/main/A.txt").exists()
        file("build/classes/java/main/A.txt").exists()

        when:
        unrelated.delete()
        succeeds "compileJava"

        then: "they all get cleaned"
        outputs.deletedClasses("Unrelated")
        file("build/generated/sources/annotationProcessor/java/main/A.txt").exists()
        file("build/generated/sources/headers/java/main/A.txt").exists()
        file("build/classes/java/main/A.txt").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/18840")
    def "a class can be aggregated with a type that it generated"() {
        given:
        withProcessor(new AnnotatedGeneratedClassProcessorFixture())
        java "@Bean @Service class A {}"
        succeeds "compileJava"

        when:
        java "class Unrelated {}"

        then:
        succeeds "compileJava"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6536")
    def "does not reprocess if nothing in the current sourceSet changed"() {
        given:
        withProcessor(new AnnotatedGeneratedClassProcessorFixture())
        javaTestSourceFile "@Bean class Test {}"
        outputs.snapshot { succeeds "compileTestJava" }

        when:
        java "class Unrelated {}"

        then:
        succeeds "compileTestJava"

        and:
        outputs.recompiledClasses("Unrelated")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "module-info.java does not cause exception in annotation processor handling"() {
        given:
        withProcessor(new AnnotationProcessorFixture("foo", "FooAnnotation", true).withDeclaredType(IncrementalAnnotationProcessorType.AGGREGATING))
        javaInPackage "foo", "class Unrelated {}"

        when:
        file("src/main/java/module-info.java") << 'module org.example.foo { exports foo; }'

        then:
        succeeds "compileJava"

        and:
        javaInPackage "foo", "class Unrelated2 {}"

        then:
        succeeds "compileJava"
    }

    private boolean serviceRegistryReferences(String... services) {
        def registry = file("build/classes/java/main/ServiceRegistryResource.txt").text
        services.every() {
            registry.contains("$it")
        }
    }
}
