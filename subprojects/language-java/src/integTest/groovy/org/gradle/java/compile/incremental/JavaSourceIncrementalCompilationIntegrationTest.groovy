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

import java.nio.file.Files
import java.nio.file.Paths

class JavaSourceIncrementalCompilationIntegrationTest extends BaseJavaSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        fails language.compileTaskName
        failure.assertHasErrorOutput(expectedFailure)
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

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when module info is added"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)

        succeeds language.compileTaskName

        when:
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
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

    @Issue("https://github.com/gradle/gradle/issues/14744")
    @Requires(TestPrecondition.JDK16_OR_LATER)
    def "recompiles only affected classes when Java records are used"() {
        given:
        file("src/main/${languageName}/Person.${languageName}") << """
            public record Person(String name, int age) {}
        """
        file("src/main/${languageName}/Library.${languageName}") << """
            public class Library {
                public boolean foo() {
                    return true;
                }
            }
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/Library.${languageName}").text = """
            public class Library {
                public boolean foo() {
                    return false;
                }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('Library')
    }

    @Issue("https://github.com/gradle/gradle/issues/14744")
    @Requires(TestPrecondition.JDK16_OR_LATER)
    def "recompiles only annotation of record when changed"() {
        given:
        file("src/main/${languageName}/MyRecordAnnotation.${languageName}") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.RECORD_COMPONENT)
            public @interface MyRecordAnnotation {}
        """
        file("src/main/${languageName}/Person.${languageName}") << """
            public record Person(@MyRecordAnnotation String name, int age) {}
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/MyRecordAnnotation.${languageName}").text = """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.RECORD_COMPONENT)
            public @interface MyRecordAnnotation {
                int value() default 42;
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyRecordAnnotation')
    }

    @Issue("https://github.com/gradle/gradle/issues/14744")
    @Requires(TestPrecondition.JDK16_OR_LATER)
    def "recompiles record when changed"() {
        given:
        file("src/main/${languageName}/Library.${languageName}") << """
            public class Library {
                public boolean foo() {
                    return true;
                }
            }
        """
        file("src/main/${languageName}/Person.${languageName}") << """
            public record Person(String name, int age) {}
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/Person.${languageName}").text = """
            public record Person(String firstName, String lastName, int age) {}
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('Person')
    }

    @Issue("https://github.com/gradle/gradle/issues/14744")
    @Requires(TestPrecondition.JDK16_OR_LATER)
    def "recompiles record consumer and record when record is changed"() {
        given:
        file("src/main/${languageName}/Library.${languageName}") << """
            public class Library {
                public boolean foo() {
                    return false;
                }
            }
        """
        file("src/main/${languageName}/Consumer.${languageName}") << """
            public class Consumer {
                public int useRecord(Person p) {
                    return p.age();
                }
            }
        """
        file("src/main/${languageName}/Person.${languageName}") << """
            public record Person(String name, int age) {}
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/Person.${languageName}").text = """
            public record Person(String firstName, String lastName, int age) {}
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('Person', 'Consumer')
    }
}
