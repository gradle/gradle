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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.language.fixtures.AnnotationProcessorFixture
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.NonIncrementalProcessorFixture
import org.gradle.language.fixtures.ResourceGeneratingProcessorFixture
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import javax.tools.StandardLocation

import static org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.ISOLATING

class IsolatingIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {
    private static HelperProcessorFixture writingResourcesTo(String location) {
        def helperProcessorFixture = new HelperProcessorFixture()
        helperProcessorFixture.writeResources = true
        helperProcessorFixture.resourceLocation = location
        return helperProcessorFixture
    }

    private HelperProcessorFixture helperProcessor

    @Override
    def setup() {
        helperProcessor = writingResourcesTo(StandardLocation.CLASS_OUTPUT.toString())
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
        outputs.recompiledFiles("A", "AHelper", "AHelperResource.txt")
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6536")
    def "remembers generated files across multiple compilations"() {
        given:
        def a = java "@Helper class A {}"
        def b = java "@Helper class B {}"
        java "class Unrelated {}"
        run "compileJava"
        a.text = "@Helper class A { public void foo() {} }"
        outputs.snapshot { run "compileJava" }

        when:
        b.text = " class B { }"
        run "compileJava"

        then:
        outputs.deletedFiles("BHelper", "BHelperResource")
        outputs.recompiledFiles("B")
    }

    def "generated files are recompiled when annotated file is affected by a change"() {
        given:
        def util = java "class Util {}"
        java """
            @Helper class A {
                private Util util = new Util();
            }
        """

        outputs.snapshot { run "compileJava" }

        when:
        util.text = "class Util { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("Util", "A", "AHelper", "AHelperResource.txt")
    }

    @Issue("https://github.com/gradle/gradle/issues/21203")
    def "generated files are recompiled when annotated file is affected by a change through method return type parameter"() {
        given:
        def util = java "class Util {}"
        java """import java.util.List;
            @Helper class A {
                public List<Util> foo() {
                    return null;
                }
            }
        """
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        util.text = "class Util { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("Util", "A", "AHelper", "AHelperResource.txt")
    }

    def "classes depending on generated files are recompiled when annotated file's ABI is affected by a change"() {
        given:
        def util = java "class Util {}"
        java """
            @Helper class A {
                public Util util = new Util();
            }
        """
        java """
            class Dependent {
                private AHelper helper = new AHelper();
            }
       """

        outputs.snapshot { run "compileJava" }

        when:
        util.text = "class Util { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("Util", "A", "AHelper", "AHelperResource.txt", "Dependent")
    }

    def "classes depending on generated files are not recompiled when annotated file's implementation is affected by a change"() {
        given:
        def util = java "class Util {}"
        java """
            @Helper class A {
                private Util util = new Util();
            }
        """
        java """
            class Dependent {
                private AHelper helper = new AHelper();
            }
       """

        outputs.snapshot { run "compileJava" }

        when:
        util.text = "class Util { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("Util", "A", "AHelper", "AHelperResource.txt")
    }

    def "incremental processing works on subsequent incremental compilations"() {
        given:
        def a = java "@Helper class A {}"
        java "class Unrelated {}"
        run "compileJava"
        a.text = "@Helper class A { public void foo() {} }"
        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void bar() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "AHelper", "AHelperResource.txt")
    }

    def "incremental processing works on subsequent incremental compilations after failure"() {
        given:
        def a = java "@Helper class A {}"
        java "@Helper class B {}"
        java "class Unrelated {}"
        run "compileJava"
        a.text = "@Helper class A { public void foo() {} }"
        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public String bar() { return 0; } }"
        runAndFail "compileJava", "-d"

        then:
        outputs.noneRecompiled()
        outputContains("Deleting generated files: [${file("build/classes/java/main/AHelperResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/AHelper.java")}]"
        )
        outputContains("Restoring stashed files: [" +
            "${file("build/classes/java/main/A.class")}, " +
            "${file("build/classes/java/main/AHelper.class")}, " +
            "${file("build/classes/java/main/AHelperResource.txt")}, " +
            "${file("build/generated/sources/annotationProcessor/java/main/AHelper.java")}]"
        )

        when:
        a.text = "@Helper class A { public void bar() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "AHelper", "AHelperResource.txt")
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

    def "annotated files are not recompiled on unrelated changes even after failure"() {
        given:
        java "@Helper class A {}"
        def unrelated = java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        unrelated.text = "class Unrelated { public Unrelated foo() { return 0; } }"
        runAndFail "compileJava", "-d"

        then:
        outputs.noneRecompiled()
        outputContains("Deleting generated files: []")
        outputContains("Restoring stashed files: [${file("build/classes/java/main/Unrelated.class")}]")

        when:
        unrelated.text = "class Unrelated { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("Unrelated")
    }

    def "classes depending on generated file are recompiled when source file changes"() {
        given:
        def a = java "@Helper class A {}"
        java """class Dependent {
            private AHelper helper = new AHelper();
        }"""


        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "AHelper", "AHelperResource.txt", "Dependent")
    }

    def "source file is reprocessed when dependency of generated file changes"() {
        given:
        withProcessor(new OpaqueDependencyProcessor())
        java "@Thingy class A {}"
        java "class Unrelated {}"
        def dependency = java "class Dependency {}"

        outputs.snapshot { run "compileJava" }

        when:
        dependency.text = "class Dependency { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("AThingy", "Dependency")
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
        outputs.deletedFiles("A", "AHelper", "AHelperResource.txt")
    }

    def "generated files are deleted when annotated file is deleted"() {
        given:
        withProcessor(writingResourcesTo(StandardLocation.SOURCE_OUTPUT.toString()))
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()
        file("build/generated/sources/annotationProcessor/java/main/AHelperResource.txt").exists()

        when:
        a.delete()
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()
        !file("build/generated/sources/annotationProcessor/java/main/AHelperResource.txt").exists()
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        withProcessor(writingResourcesTo(StandardLocation.SOURCE_OUTPUT.toString()))
        java "@Helper class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()
        file("build/generated/sources/annotationProcessor/java/main/AHelperResource.txt").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()
        !file("build/generated/sources/annotationProcessor/java/main/AHelperResource.txt").exists()

        and:
        outputs.deletedFiles("AHelper")
    }

    def "all files are recompiled when processor changes"() {
        given:
        java "@Helper class A {}"
        outputs.snapshot { run "compileJava" }

        when:
        helperProcessor.suffix = "world"
        withProcessor(helperProcessor)
        run "compileJava"

        then:
        outputs.recompiledFiles("A", "AHelper", "AHelperResource.txt")
    }

    def "all files are recompiled if compiler does not support incremental annotation processing"() {
        given:
        buildFile << """
            compileJava {
                options.fork = true
                options.forkOptions.executable = '${TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javacExecutable)}'
            }
        """
        def a = java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        outputs.recompiledFiles("A", "AHelper", "Unrelated", "AHelperResource.txt")

        and:
        outputContains("the chosen compiler did not support incremental annotation processing")
    }

    def "all files are recompiled if a generated source file is deleted"() {
        given:
        java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").delete()
        run "compileJava"

        then:
        outputs.recompiledFiles('A', "AHelper", "AHelperResource.txt", "Unrelated")
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
        outputs.recompiledFiles('A', "AHelper", "AHelperResource.txt", "Unrelated")
    }

    def "all files are recompiled if a generated resource is deleted"() {
        given:
        java "@Helper class A {}"
        java "class Unrelated {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("build/classes/java/main/AHelperResource.txt").delete()
        run "compileJava"

        then:
        outputs.recompiledFiles('A', "AHelper", "AHelperResource.txt", "Unrelated")
    }

    def "processors must provide an originating element for each source element"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().providingNoOriginatingElements().withDeclaredType(IncrementalAnnotationProcessorType.ISOLATING))
        def a = java "@Thing class A {}"
        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Thing class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because the generated type 'AThing' must have exactly one originating element, but had 0.")
    }

    def "processors must provide an originating element for each resource"() {
        given:
        withProcessor(new ResourceGeneratingProcessorFixture().providingNoOriginatingElements().withDeclaredType(IncrementalAnnotationProcessorType.ISOLATING))
        def a = java "@Thing class A {}"
        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Thing class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because the generated resource 'A.txt in SOURCE_OUTPUT' must have exactly one originating element, but had 0.")
    }

    def "processors cannot provide multiple originating elements for types"() {
        given:
        helperProcessor.withMultipleOriginatingElements = true
        helperProcessor.writeResources = false
        withProcessor(helperProcessor)
        def a = java "@Helper class A {}"
        java "@Helper class B {}"

        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Helper class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because the generated type")
        outputContains(" must have exactly one originating element, but had 2.")
    }

    def "processors cannot provide multiple originating elements for resources"() {
        given:
        helperProcessor.withMultipleOriginatingElements = true
        withProcessor(helperProcessor)
        def a = java "@Helper class A {}"
        java "@Helper class B {}"

        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Helper class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because the generated resource")
        outputContains(" must have exactly one originating element, but had 2.")
    }

    def "processors can generate identical resources in different locations"() {
        given:
        def locations = [StandardLocation.SOURCE_OUTPUT.toString(), StandardLocation.NATIVE_HEADER_OUTPUT.toString(), StandardLocation.CLASS_OUTPUT.toString()]
        withProcessor(new ResourceGeneratingProcessorFixture().withOutputLocations(locations).withDeclaredType(IncrementalAnnotationProcessorType.ISOLATING))
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

    @Issue(["https://github.com/gradle/gradle/issues/8128", "https://bugs.openjdk.java.net/browse/JDK-8162455"])
    def "incremental processing doesn't trigger unmatched processor option warning"() {
        buildFile << """
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            compileJava.options.compilerArgs += [ "-Werror", "-Amessage=fromOptions" ]
        """
        java "@Helper class A {}"

        outputs.snapshot { succeeds "compileJava" }

        when:
        java "class B {}"

        then:
        succeeds "compileJava"
        outputs.recompiledClasses("B")
    }

    def "reports isolating processor in build operation"() {
        java "class Irrelevant {}"

        when:
        succeeds "compileJava"

        then:
        with(operations[':compileJava'].result.annotationProcessorDetails as List<Object>) {
            size() == 1
            first().className == 'HelperProcessor'
            first().type == ISOLATING.name()
        }
    }

    /**
     * An annotation processor whose generated code depends on some library class that the annotated
     * file does not depend on. A real-world example would be a processor that generated database
     * access classes and depends on the concrete database driver, which might change.
     */
    private static class OpaqueDependencyProcessor extends AnnotationProcessorFixture {
        OpaqueDependencyProcessor() {
            super("Thingy")
            declaredType = IncrementalAnnotationProcessorType.ISOLATING
        }

        @Override
        protected String getGeneratorCode() {
            """
                for (Element element : elements) {
                    TypeElement typeElement = (TypeElement) element;
                    String className = typeElement.getSimpleName().toString() + "Thingy";
                    try {
                        JavaFileObject sourceFile = filer.createSourceFile(className, element);
                        Writer writer = sourceFile.openWriter();
                        try {
                            writer.write("class " + className + " {");
                            writer.write("    Dependency getValue() { return new Dependency();}");
                            writer.write("}");
                        } finally {
                            writer.close();
                        }
                    } catch (IOException e) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className, element);
                    }
                }
            """
        }
    }

}
