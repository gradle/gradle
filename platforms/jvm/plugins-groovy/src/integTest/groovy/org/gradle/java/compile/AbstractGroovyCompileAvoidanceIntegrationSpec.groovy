/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java.compile

import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.integtests.fixtures.CompiledLanguage

abstract class AbstractGroovyCompileAvoidanceIntegrationSpec extends AbstractJavaGroovyCompileAvoidanceIntegrationSpec {
    CompiledLanguage language = CompiledLanguage.GROOVY

    @Override
    String expectedJavaCompilationFailureMessage() {
        // Groovy problem reporting is not yet implemented
        return CompilationFailedException.COMPILATION_FAILED_DETAILS_ABOVE
    }

    private String goodAstTransformation() {
        """
            import org.codehaus.groovy.transform.*;
            import org.codehaus.groovy.ast.*;
            import org.codehaus.groovy.control.*;
            @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
            public class MyASTTransformation extends AbstractASTTransformation {
                @Override
                public void visit(ASTNode[] nodes, SourceUnit source) {
                    System.out.println("Hello from AST transformation!");
                }
            }
        """
    }

    private String goodAstTransformationWithABIChange() {
        """
            import org.codehaus.groovy.transform.*;
            import org.codehaus.groovy.ast.*;
            import org.codehaus.groovy.control.*;
            @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
            public class MyASTTransformation extends AbstractASTTransformation {
                @Override
                public void visit(ASTNode[] nodes, SourceUnit source) {
                    System.out.println("Hello from AST transformation!");
                }
                public void foo() {}
            }
        """
    }

    private String badAstTransformationNonABIChange() {
        """
            import org.codehaus.groovy.transform.*;
            import org.codehaus.groovy.ast.*;
            import org.codehaus.groovy.control.*;
            @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
            public class MyASTTransformation extends AbstractASTTransformation {
                @Override
                public void visit(ASTNode[] nodes, SourceUnit source) {
                    assert false: "Bad AST transformation!"
                }
                public void foo() {}
            }
        """
    }

    private String astTransformationDeclaration() {
        """
            project(':b') {
                configurations { astTransformation }
                dependencies {
                    astTransformation project(':a')
                }

                tasks.withType(GroovyCompile) {
                    astTransformationClasspath.from(configurations.astTransformation)
                }
            }
        """
    }

    private String astTransformationAnnotation() {
        """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE])
            @org.codehaus.groovy.transform.GroovyASTTransformationClass("MyASTTransformation")
            public @interface MyAnnotation {}
        """
    }

    def 'always recompile if compilation avoidance is not enabled'() {
        given:
        settingsFile.text = settingsFile.text.readLines().findAll { !it.contains("enableFeaturePreview") }.join('\n')
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/groovy/ToolImpl.groovy")
        sourceFile << """
            public class ToolImpl {
                public String thing() { return null; }
            }
        """
        file("b/src/main/groovy/Main.groovy") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:compileGroovy"

        then:
        outputDoesNotContain('Groovy compilation avoidance is an incubating feature')
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"

        when:
        sourceFile.text = """
            public class ToolImpl {
                public String thing() { return ""; }
            }
        """

        then:
        succeeds ":b:compileGroovy"
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"
    }

    def "recompile with change of local ast transformation"() {
        given:
        executer.beforeExecute {
            executer.withArgument('--info')
        }
        buildFile << astTransformationDeclaration()
        file("a/src/main/groovy/MyAnnotation.groovy") << astTransformationAnnotation()
        def astTransformationSourceFile = file("a/src/main/groovy/MyASTTransformation.groovy")
        file("b/src/main/groovy/Main.groovy") << """
            @MyAnnotation
            public class Main {}
        """

        when:
        astTransformationSourceFile << goodAstTransformation()

        then:
        succeeds ":b:compileGroovy"
        outputContains('Hello from AST transformation!')
        outputContains('Groovy compilation avoidance is an incubating feature')
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"

        when:
        astTransformationSourceFile.text = goodAstTransformationWithABIChange()

        then:
        succeeds ":b:compileGroovy"
        outputContains('Hello from AST transformation!')
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"

        when:
        astTransformationSourceFile.text = badAstTransformationNonABIChange()

        then:
        fails ":b:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"
        failure.assertHasCause('Bad AST transformation!')
    }

    def "recompile with change of global ast transformation"() {
        given:
        executer.beforeExecute {
            executer.withArgument('--info')
        }
        buildFile << astTransformationDeclaration()
        file("a/src/main/resources/META-INF/services/org.codehaus.groovy.transform.ASTTransformation") << "MyASTTransformation"
        def astTransformationSourceFile = file("a/src/main/groovy/MyASTTransformation.groovy")
        file("b/src/main/groovy/Main.groovy") << """
            public class Main {}
        """

        when:
        astTransformationSourceFile << goodAstTransformation()

        then:
        succeeds ":b:compileGroovy"
        outputContains('Hello from AST transformation!')
        outputContains('Groovy compilation avoidance is an incubating feature')
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"

        when:
        astTransformationSourceFile.text = goodAstTransformationWithABIChange()

        then:
        succeeds ":b:compileGroovy"
        outputContains('Hello from AST transformation!')
        executedAndNotSkipped ":a:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"

        when:
        astTransformationSourceFile.text = badAstTransformationNonABIChange()

        then:
        fails ":b:compileGroovy"
        executedAndNotSkipped ":b:compileGroovy"
        failure.assertHasCause('Bad AST transformation!')
    }
}
