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

package org.gradle.java.compile.incremental


import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Paths

class JavaSourceIncrementalCompilationIntegrationTest extends BaseJavaSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        fails language.compileTaskName
        failure.assertHasErrorOutput(expectedFailure)
    }

    @Unroll
    def "change to #retention retention annotation class recompiles #desc"() {
        def annotationClass = file("src/main/${language.name}/SomeAnnotation.${language.name}") << """
            import java.lang.annotation.*;

            @Retention(RetentionPolicy.$retention)
            public @interface SomeAnnotation {}
        """
        source "@SomeAnnotation class A {}", "class B {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        annotationClass.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses(expected as String[])

        where:
        desc              | retention | expected
        'all'             | 'SOURCE'  | ['A', 'B', 'SomeAnnotation']
        'annotated types' | 'CLASS'   | ['SomeAnnotation', 'A']
        'annotated types' | 'RUNTIME' | ['SomeAnnotation', 'A']
    }

    def "deletes headers when source file is deleted"() {
        given:
        def sourceFile = file("src/main/java/my/org/Foo.java")
        sourceFile.text = """
            package my.org;

            public class Foo {
                public native void foo();

                public static class Inner {
                    public native void anotherNative();
                }
            }
        """
        def generatedHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo.h")
        def generatedInnerClassHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo_Inner.h")

        source("""class Bar {
            public native void bar();
        }""")

        succeeds language.compileTaskName
        generatedHeaderFile.assertExists()
        generatedInnerClassHeaderFile.assertExists()

        when:
        sourceFile.delete()
        succeeds language.compileTaskName

        then:
        generatedHeaderFile.assertDoesNotExist()
        generatedInnerClassHeaderFile.assertDoesNotExist()
        file("build/generated/sources/headers/java/main/Bar.h").assertExists()
    }

    def "changed class with used non-private constant incurs full rebuild"() {
        source "class A { int foo() { return 1; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B', 'A'
    }

    //  Can re-enable with compiler plugins. See gradle/gradle#1474
    def "changing an unused non-private constant incurs partial rebuild"() {
        source "class A { int foo() { return 2; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B'
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when module info changes"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module foo {
                requires java.logging;
            }
        """

        succeeds language.compileTaskName

        when:
        moduleInfo.text = """
            module foo {
            }
        """

        then:
        fails language.compileTaskName
        result.assertHasErrorOutput("package java.util.logging is not visible")
    }

    @Issue("https://github.com/gradle/gradle/issues/7363")
    def "can recompile classes which depend on a top-level class with a different name than the file"() {
        file("src/main/java/foo/Strings.java") << """
            package foo;
            public class Strings {

            }

            class StringUtils {
                static void foo() {}
            }

        """

        file("src/main/java/foo/Consumer.java") << """
            package foo;
            public class Consumer {
                void consume() { StringUtils.foo(); }
            }
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/java/foo/Strings.java").text = """
            package foo;
            public class Strings {

            }

            class StringUtils {
                static void foo() {}
                static void bar() {}
            }

        """
        run language.compileTaskName

        then:
        outputs.recompiledFqn("foo.StringUtils", "foo.Strings", "foo.Consumer")
    }

    @Issue("https://github.com/gradle/gradle/issues/7363")
    def "can recompile classes which depend on a top-level class with a different name than the file (scenario 2)"() {
        file("src/main/java/foo/Strings.java") << """
            package foo;
            public class Strings {

            }

            class StringUtils {
                static void foo() { Constants.getConstant(); }
            }

        """

        file("src/main/java/foo/Constants.java") << """
            package foo;
            class Constants {
                static String getConstant() { return " "; }
            }

        """

        file("src/main/java/foo/Main.java") << """
            package foo;
            public class Main {
                void consume() { StringUtils.foo(); }
            }
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/java/foo/Constants.java").text = """
            package foo;
            class Constants {
                static String getConstant() { return "two spaces"; }
            }

        """
        run language.compileTaskName

        then:
        outputs.recompiledFqn("foo.StringUtils", "foo.Strings", "foo.Constants")
    }

    @Issue("https://github.com/gradle/gradle/issues/10340")
    def "recompiles class when constant from inner class is changed"() {
        given:
        file("src/main/${languageName}/MyAnnotation.${languageName}") << """
            public @interface MyAnnotation { int value(); }
        """
        file("src/main/${languageName}/TopLevel.${languageName}") << """
            public class TopLevel {
               static class Inner {
                  public static final int CONST = 9999;
               }
            }
        """
        file("src/main/${languageName}/MyClass.${languageName}") << """
            public class MyClass {
                @MyAnnotation(TopLevel.Inner.CONST)
                private void foo() { }
            }
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/TopLevel.${languageName}").text = """
            public class TopLevel {
               static class Inner {
                  public static final int CONST = 1223;
               }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyClass', 'MyAnnotation', 'TopLevel$Inner', 'TopLevel')
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Issue("https://github.com/gradle/gradle/issues/9202")
    def "source mapping file works with symlinks"() {
        given:
        buildFile << """
            sourceSets {
                main {
                    ${languageName} {
                        srcDirs = ['src/main/${languageName}/build', 'src/main/${languageName}/linkparent']
                    }
                }
            }
        """
        file("other/foo/a/MyClass.${languageName}") << """package foo.a;
            public class MyClass {
                public void foo() { }
            }
        """
        file("src/main/${languageName}/build/foo/b/Other.${languageName}") << """package foo.b;
            import foo.a.MyClass;

            public class Other {
                public void hello(MyClass my) { my.foo(); }
            }
        """
        Files.createSymbolicLink(Paths.get(file("src/main/${languageName}/linkparent").toURI()), Paths.get(file("other").toURI()))
        outputs.snapshot { run language.compileTaskName }

        when:
        file("other/foo/a/MyClass.${languageName}").text = """package foo.a;
            public class MyClass {
                public void foo() { }
                public void bar() { }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyClass', 'Other')
    }
}
