/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.intellij.lang.annotations.Language
import org.junit.Rule
import spock.lang.Specification

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider

class ConfigurationCacheInstrumentationProcessorTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    final TestFile srcDir = temporaryFolder.createDir('src')
    final TestFile outDir = temporaryFolder.createDir('out')
    final TestFile classesDir = temporaryFolder.createDir('classes')

    def "code generation is deterministic regardless of type processing order"() {
        given:
        withAnnotatedType 'Foo'
        withAnnotatedType 'Bar'
        def annotatedTypes = ['my.Foo', 'my.Bar']

        when:
        DiagnosticCollector<JavaFileObject> diagnostics = processAnnotationsOf(annotatedTypes)

        then:
        errorsFrom(diagnostics).isEmpty()

        and:
        def outputFile = outDir.file('my/Interceptors.java')
        outputFile.file

        when:
        def originalOutput = outputFile.text

        and:
        processAnnotationsOf(annotatedTypes.reverse())

        then:
        outputFile.text == originalOutput
    }

    def "code generation is deterministic regardless of method declaration order"() {
        given:
        withMethodDeclarationOrder 'foo', 'bar'

        when:
        DiagnosticCollector<JavaFileObject> diagnostics = processAnnotationsOf(['my.Test'])

        then:
        errorsFrom(diagnostics).isEmpty()

        and:
        def outputFile = outDir.file('my/Interceptors.java')
        outputFile.file

        when:
        def originalOutput = outputFile.text
        withMethodDeclarationOrder 'bar', 'foo'

        and:
        processAnnotationsOf(['my.Test'])

        then:
        outputFile.text == originalOutput
    }

    private withAnnotatedType(String typeName) {
        sourceFile "my/${typeName}.java", """
            package my;

            import org.gradle.internal.instrumentation.api.annotations.*;

            @SpecificJvmCallInterceptors(generatedClassName = "my.Interceptors")
            class $typeName {
                @InterceptCalls
                @CallableKind.StaticMethod(ofClass = my.${typeName}.class)
                @CallableDefinition.Name("stubTarget")
                public static void stub() {}
            }
        """
    }

    private void withMethodDeclarationOrder(String first, String second) {
        sourceFile 'my/Test.java', """
            package my;

            import org.gradle.internal.instrumentation.api.annotations.*;

            @SpecificJvmCallInterceptors(generatedClassName = "my.Interceptors")
            class Test {
                @InterceptCalls
                @CallableKind.StaticMethod(ofClass = my.Test.class)
                @CallableDefinition.Name("${first}Target")
                public static void ${first}() {}

                @InterceptCalls
                @CallableKind.StaticMethod(ofClass = my.Test.class)
                @CallableDefinition.Name("${second}Target")
                public static void ${second}() {}
            }
        """
    }

    private void sourceFile(String name, @Language('java') String source) {
        srcDir.file(name).text = source.stripIndent()
    }

    private DiagnosticCollector<JavaFileObject> processAnnotationsOf(Iterable<String> classes) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        String targetVersion = '8'
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.tap {
                setLocation(StandardLocation.SOURCE_PATH, [srcDir])
                setLocation(StandardLocation.SOURCE_OUTPUT, [outDir])
                setLocation(StandardLocation.CLASS_OUTPUT, [classesDir])
            }
            Iterable<? extends JavaFileObject> compilationUnits = classes.collect {
                fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, it, JavaFileObject.Kind.SOURCE)
            }
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                ['-processor', ConfigurationCacheInstrumentationProcessor.name,
                 '-source', targetVersion,
                 '-target', targetVersion],
                null, compilationUnits
            )
            assert task.call()
        }
        return diagnostics
    }

    private List<Boolean> errorsFrom(DiagnosticCollector<JavaFileObject> diagnostics) {
        diagnostics
            .getDiagnostics()
            .findAll { it.kind == Diagnostic.Kind.ERROR }
    }
}
