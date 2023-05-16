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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

class JavaClassChangeIncrementalCompilationIntegrationTest extends BaseJavaClassChangeIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        fails language.compileTaskName
        failure.assertHasErrorOutput(expectedFailure)
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

    @Issue("https://github.com/gradle/gradle/issues/14744")
    @Requires(UnitTestPreconditions.Jdk16OrLater)
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
    @Requires(UnitTestPreconditions.Jdk16OrLater)
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
    @Requires(UnitTestPreconditions.Jdk16OrLater)
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
    @Requires(UnitTestPreconditions.Jdk16OrLater)
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

    def "changing an unused non-private constant incurs partial rebuild"() {
        source "class A { int foo() { return 2; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B'
    }

    def "changing an used non-private constant incurs partial rebuild"() {
        source "class A { static final int x = 1; }", "class B { private void method() { int x = A.x; } }", "class C { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B'
    }

    def "changing an used non-private constant incurs partial rebuild for binary expression"() {
        source "class A { static final int x = 1; }", "class B { private void method() { int x = A.x + 1; } }", "class C { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B'
    }

    def "changing an used non-private constant incurs partial rebuild when used on annotation"() {
        source "class A { static final int x = 1; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }""",
            "class C { private void method() { @B(A.x) int x = 1; } }", "class D { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'C'
    }

    def "changing an used non-private constant incurs partial rebuild when used on annotation with binary expression"() {
        source "class A { static final int x = 1; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }""",
            "class C { private void method() { @B(A.x + 1) int x = 1; } }", "class D { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'C'
    }

    def "changing an used non-private constant incurs partial rebuild when constants are chained"() {
        source "class A { static final int x = 1; }",
            "class B { static final int x = A.x + 1; }",
            "class C { static final int x = B.x + 1; }",
            "class D { static final int x = C.x + 1; }",
            "class E { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B', 'C', 'D'
    }

    def "changing an constant from interface incurs partial rebuild"() {
        source "interface A { static final int x = 1; }",
            "class B { static final int x = A.x + 1; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface Annotation { int value(); }""",
            "@Annotation(A.x + 1) class C { private void method() { int x = A.x; } }",
            "@Annotation(A.x + 1) class D { }",
            "class E { }"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { static final int x = 2; /* change */ void bla() { /* avoid flakiness */ } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B', 'C', 'D'
    }

    def "partially recompiles when constant from inner class is changed"() {
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
                @MyAnnotation(TopLevel.Inner.CONST + 1)
                private void foo() { }
            }
        """
        file("src/main/${languageName}/MyClass2.${languageName}") << """
            public class MyClass2 {
                private void foo() {
                    int x = TopLevel.Inner.CONST + 1;
                }
            }
        """
        file("src/main/${languageName}/IrrelevantClass.${languageName}") << """
            public class IrrelevantClass {
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
        outputs.recompiledClasses('MyClass', 'MyClass2', 'TopLevel$Inner', 'TopLevel')
    }

    // Expected since any change inside same compile task will cause recompilation of any dependent class
    def "non-abi change to constant origin class causes partial recompilation"() {
        source "class A { final static int x = 1; int method() { return 1; } }",
            "class B { int method() { return A.x; }  }",
            "class C {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { final static int x = 1; int method() { return 2; } }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses('A', 'B')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as class type parameter"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source "interface Super <T, R> { T getResult(R r); }"
        source "interface Sub extends Super<" + t + ", " + r + "> { }"
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('symbol: class Deleted', 'Sub')

        where:
        t         | r
        "Deleted" | "String"
        "String"  | "Deleted"
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as method parameter type"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source "interface A { void doSomething(Deleted d); }"
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as method parameter type bound"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source "interface A { <T extends Deleted> void doSomething(T t); }"
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as method return type"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source "interface A { Deleted doSomething(); }"
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as method return type bound"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source "interface A { <T extends Deleted> T doSomething(); }"
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/21203")
    def "can detect deletion of class used as method return type parameter"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            import java.util.List;
            interface A {
                List<Deleted> doSomething();
            }"""
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/21203")
    def "can detect deletion of class used as method argument type parameter"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            import java.util.List;
            interface A {
                void doSomething(List<Deleted> arg0);
            }"""
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as field type parameter"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            import java.util.List;
            class A {
                private List<Deleted> list;
            }
            """
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as local variable type parameter"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            import java.util.List;
            import java.util.ArrayList;
            class A {
                public void method() {
                    List<Deleted> l = new ArrayList<Deleted>();
                }
            }
            """
        def deleted = source "interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as class annotation"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            @Deleted
            class A {}
            """
        def deleted = source "@interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as method annotation"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            class A {
                @Deleted
                public void method() {
                }
            }
            """
        def deleted = source "@interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }

    @Issue("https://github.com/gradle/gradle/issues/20478")
    def "can detect deletion of class used as field annotation"() {
        //executer.requireOwnGradleUserHomeDir()  //use when debugging to bypass cache

        source """
            class A {
                @Deleted
                String s;
            }
            """
        def deleted = source "@interface Deleted { }"
        outputs.snapshot { run language.compileTaskName }

        expect:
        deleted.delete()
        recompiledWithFailure('class Deleted', 'A')
    }
}
