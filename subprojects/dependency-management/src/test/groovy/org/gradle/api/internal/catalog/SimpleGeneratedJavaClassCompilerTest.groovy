/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.catalog

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class SimpleGeneratedJavaClassCompilerTest extends Specification {
    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))

    private final TestFile srcDir = tmpDir.createDir("sources")
    private final TestFile dstDir = tmpDir.createDir("classes")

    private ClassPath classPath = DefaultClassPath.EMPTY

    def "compiles a simple dependency-less Java class"() {
        when:
        compile(source('Test', 'public class Test {}'))

        then:
        assertCompiled 'Test'
    }

    def "can compile several classes at once"() {
        when:
        compile(source('A', 'public class A {}'), source('B', 'public class B {}'))

        then:
        assertCompiled 'A', 'B'
    }

    def "reports compilation errors"() {
        when:
        compile(source('A', 'public class Broken'))

        then:
        GeneratedClassCompilationException ex = thrown()
        normaliseLineSeparators(ex.message) == """Unable to compile generated sources:
  - File A.java, line: 2, reached end of file while parsing"""
    }

    def "compiler is isolated from the Gradle API"() {
        when:
        compile(source("A", """
            import org.gradle.api.internal.catalog.DefaultVersionCatalog;

            class A {
                private final DefaultVersionCatalog model;

                public A(DefaultVersionCatalog model) {
                    this.model = model;
                }
            }
        """))

        then:
        GeneratedClassCompilationException ex = thrown()
        normaliseLineSeparators(ex.message) == """Unable to compile generated sources:
  - File A.java, line: 3, package org.gradle.api.internal.catalog does not exist
  - File A.java, line: 6, cannot find symbol
      symbol:   class DefaultVersionCatalog
      location: class org.test.A
  - File A.java, line: 8, cannot find symbol
      symbol:   class DefaultVersionCatalog
      location: class org.test.A"""
    }

    def "can compile a class with a dependency on the Gradle API"() {
        classPath = classPathRegistry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER")
        when:
        compile(source("A", """
            import org.gradle.api.internal.catalog.DefaultVersionCatalog;

            class A {
                private final DefaultVersionCatalog model;

                public A(DefaultVersionCatalog model) {
                    this.model = model;
                }
            }
        """))

        then:
        assertCompiled 'A'
    }

    void assertCompiled(String... classNames) {
        classNames.each { className ->
            def source = new File(srcDir, "org/test/${className}.java")
            assert source.exists()
            def clazz = new File(dstDir, "org/test/${className}.class")
            assert clazz.exists()
            assertCompatibleWithJava8(clazz)
        }
    }

    private static void assertCompatibleWithJava8(File clazz) {
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM7) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces)
                assert version == 52
            }
        }
        ClassReader reader = new ClassReader(clazz.bytes)
        reader.accept(cv, 0)
    }

    ClassSource source(String className, String classSource) {
        new ClassSource() {
            @Override
            String getPackageName() {
                'org.test'
            }

            @Override
            String getSimpleClassName() {
                className
            }

            @Override
            String getSource() {
                """package org.test;
$classSource
"""
            }
        }
    }

    void compile(ClassSource... sources) {
        SimpleGeneratedJavaClassCompiler.compile(srcDir, dstDir, sources as List<ClassSource>, classPath)
    }
}
