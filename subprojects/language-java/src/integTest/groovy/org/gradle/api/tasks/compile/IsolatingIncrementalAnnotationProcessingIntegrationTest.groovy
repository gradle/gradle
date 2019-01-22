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
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.language.fixtures.AnnotationProcessorFixture
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.NonIncrementalProcessorFixture
import org.gradle.language.fixtures.ServiceRegistryProcessorFixture
import org.gradle.util.TextUtil
import spock.lang.Issue

import static org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails.Type.ISOLATING

class IsolatingIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {
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
        outputs.recompiledClasses("A", "AHelper", "Dependent")
    }

    def "source file is recompiled when dependency of generated file changes"() {
        given:
        withProcessor(new OpaqueDependencyProcessor())
        java "@Thingy class A {}"
        def dependency = java "class Dependency {}"

        outputs.snapshot { run "compileJava" }

        when:
        dependency.text = "class Dependency { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AThingy", "Dependency")
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
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()

        when:
        a.delete()
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        def a = java "@Helper class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava"

        then:
        !file("build/generated/sources/annotationProcessor/java/main/AHelper.java").exists()

        and:
        outputs.deletedClasses("AHelper")
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
        file("build/generated/sources/annotationProcessor/java/main/AHelper.java").delete()
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

    def "writing resources triggers a full recompilation"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().writingResources().withDeclaredType(IncrementalAnnotationProcessorType.ISOLATING))
        def a = java "@Thing class A {}"
        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Thing class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because an annotation processor generated a resource.")
    }

    def "processors cannot provide multiple originating elements"() {
        given:
        withProcessor(new ServiceRegistryProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.ISOLATING))
        def a = java "@Service class A {}"
        java "@Service class B {}"

        outputs.snapshot { succeeds "compileJava" }

        when:
        a.text = "@Service class A { void foo() {} }"

        then:
        succeeds "compileJava", "--info"

        and:
        outputContains("Full recompilation is required because the generated type 'ServiceRegistry' must have exactly one originating element, but had 2.")
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
        with(operations[':compileJava'].result.annotationProcessorDetails as List<CompileJavaBuildOperationType.Result.AnnotationProcessorDetails>) {
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
