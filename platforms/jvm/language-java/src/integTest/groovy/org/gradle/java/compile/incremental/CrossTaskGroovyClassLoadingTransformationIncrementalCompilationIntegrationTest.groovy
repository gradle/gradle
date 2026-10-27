/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects
import spock.lang.Issue

/**
 * Incremental Groovy compilation when an AST transformation on the compile classpath loads classes it meets, as Grails' GORM
 * transformations do for domain classes. A class loaded from the previous output has to find every class it refers to, so
 * none of them may be missing because it was moved aside for recompilation.
 */
class CrossTaskGroovyClassLoadingTransformationIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {
    CompiledLanguage language = CompiledLanguage.GROOVY
    // the transformation is found through META-INF/services, which a class directory classpath does not carry
    boolean useJar = true

    def setup() {
        file("api/src/main/groovy/InspectFieldTypes.groovy") << """
            import org.codehaus.groovy.ast.ASTNode
            import org.codehaus.groovy.ast.ClassNode
            import org.codehaus.groovy.ast.FieldNode
            import org.codehaus.groovy.control.CompilePhase
            import org.codehaus.groovy.control.SourceUnit
            import org.codehaus.groovy.transform.ASTTransformation
            import org.codehaus.groovy.transform.GroovyASTTransformation

            @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
            class InspectFieldTypes implements ASTTransformation {
                void visit(ASTNode[] nodes, SourceUnit source) {
                    for (ClassNode classNode : source.AST.classes) {
                        for (FieldNode field : classNode.fields) {
                            ClassNode type = field.type
                            // a class of the project under compilation that is not being recompiled, so it comes from the previous output
                            if (!type.isPrimaryClassNode() && type.isResolved() && !type.name.contains('.')) {
                                // loads it and resolves the types of its methods, like java.beans.Introspector
                                type.typeClass.methods
                            }
                        }
                    }
                }
            }
        """
        file("api/src/main/resources/META-INF/services/org.codehaus.groovy.transform.ASTTransformation") << "InspectFieldTypes"

        // a local transformation that crashes: run for each class, its exception is reported as a compiler failure, not a source error
        file("api/src/main/groovy/CrashCompilation.groovy") << """
            import java.lang.annotation.ElementType
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            import java.lang.annotation.Target
            import org.codehaus.groovy.transform.GroovyASTTransformationClass

            @Retention(RetentionPolicy.SOURCE)
            @Target(ElementType.TYPE)
            @GroovyASTTransformationClass("CrashingTransformation")
            @interface CrashCompilation {}
        """
        file("api/src/main/groovy/CrashingTransformation.groovy") << """
            import org.codehaus.groovy.ast.ASTNode
            import org.codehaus.groovy.control.CompilePhase
            import org.codehaus.groovy.control.SourceUnit
            import org.codehaus.groovy.transform.ASTTransformation
            import org.codehaus.groovy.transform.GroovyASTTransformation

            @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
            class CrashingTransformation implements ASTTransformation {
                void visit(ASTNode[] nodes, SourceUnit source) {
                    throw new IllegalStateException('transformation crashed')
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/22519")
    @ToBeFixedForIsolatedProjects(because = "cross-project incremental compilation")
    def "recompiles accessible dependents of a class that is recompiled for a private dependency when a transformation loads classes"() {
        given:
        // Base uses Util only in a method body, so a change to Util recompiles Base as a private dependent.
        // Holder is loaded by the transformation while Base is compiled; it refers to Child, whose superclass is Base.
        File util = sourceWithFileSuffixForProject("groovy", "impl", "class Util { static String zone(String id) { id?.trim() } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class Base { Holder holder; String zone() { Util.zone('UTC') } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class Child extends Base {}")
        sourceWithFileSuffixForProject("groovy", "impl", "class Holder { Child child }")
        run ":impl:compileGroovy"

        when:
        impl.snapshot {
            util.text = "class Util { static String zone(String id) { id?.trim()?.toUpperCase() } }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses("Util", "Base", "Child", "Holder")
    }

    @ToBeFixedForIsolatedProjects(because = "cross-project incremental compilation")
    def "recompiles only the changed class when no class is recompiled for a private dependency"() {
        given:
        File util = sourceWithFileSuffixForProject("groovy", "impl", "class Util { static String zone(String id) { id?.trim() } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class Base { Holder holder }")
        sourceWithFileSuffixForProject("groovy", "impl", "class Child extends Base {}")
        sourceWithFileSuffixForProject("groovy", "impl", "class Holder { Child child }")
        run ":impl:compileGroovy"

        when:
        impl.snapshot {
            util.text = "class Util { static String zone(String id) { id?.trim()?.toUpperCase() } }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses("Util")
    }

    @ToBeFixedForIsolatedProjects(because = "cross-project incremental compilation")
    def "does full recompilation on fatal failure"() {
        given:
        File a = sourceWithFileSuffixForProject("groovy", "impl", "class A { }")
        sourceWithFileSuffixForProject("groovy", "impl", "class B { }")
        run ":impl:compileGroovy"

        when:
        a.text = "@CrashCompilation class A { }"

        then:
        executer.withStackTraceChecksDisabled()
        def execution = runAndFail ":impl:compileGroovy"
        execution.assertHasCause("Unrecoverable compilation error")

        when:
        a.text = "class A { int i }"
        executer.withStacktraceEnabled()
        run ":impl:compileGroovy", "--info"

        then:
        impl.recompiledClasses("A", "B")
        outputContains("Full recompilation is required")
    }
}
