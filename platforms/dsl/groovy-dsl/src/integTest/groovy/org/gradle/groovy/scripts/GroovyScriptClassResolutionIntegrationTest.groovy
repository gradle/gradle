/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.scripts

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.containsString

/**
 * Build scripts should resolve classes the same way plain Groovy does, on top of Gradle default imports.
 */
class GroovyScriptClassResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "resolves Gradle and JDK default imports"() {
        buildFile("""
            JavaVersion version = JavaVersion.VERSION_17
            LocalDate date = LocalDate.of(2026, 1, 2)
            BigDecimal decimal = BigDecimal.ONE
            Class<?> factory = ObjectFactory
            println "result=\$version \$date \$decimal \${factory.name}"
        """)

        expect:
        succeeds("help")
        outputContains("result=17 2026-01-02 1 org.gradle.api.model.ObjectFactory")
    }

    def "resolves nested types of a class extending Script declared in a script"() {
        // Script on its own is a Gradle default import of org.gradle.api.Script
        buildFile("""
            class MyScript extends groovy.lang.Script {
                static class Helper {}
                def run() { new Helper() }
            }
            println "result=\${new MyScript().run().class.simpleName}"
        """)

        expect:
        succeeds("help")
        outputContains("result=Helper")
    }

    def "resolves nested types through qualified name in #expression"() {
        writeNestedTypes()
        buildFile("""
            println "result=\${${expression}}"
        """)

        expect:
        succeeds("help")
        outputContains("result=$binaryName")

        where:
        expression                                        | binaryName
        "com.example.Outer.Mid.Inner.name"                | "com.example.Outer\$Mid\$Inner"
        "com.example.Outer.nested.Inner.name"             | "com.example.Outer\$nested\$Inner"
        "com.example.lower.Inner.name"                    | "com.example.lower\$Inner"
        "com.example.lower.Mid.Inner.name"                | "com.example.lower\$Mid\$Inner"
        "new com.example.lower.nested.Inner().class.name" | "com.example.lower\$nested\$Inner"
    }

    def "does not resolve lower case nested classes of lower case classes in property chains"() {
        // Groovy resolves this, but Gradle stops at lower case class names to avoid class loader lookups for every
        // property chain like "rootProject.ext.libs.VERSION". Declared types like "new com.example.lower.nested.Inner()" still resolve.
        writeNestedTypes()
        buildFile("""
            println "result=\${com.example.lower.nested.Inner.name}"
        """)

        expect:
        fails("help")
        failure.assertThatCause(containsString("Could not get unknown property 'com' for root project"))
    }

    def "resolves imports of nested types of lower case classes"() {
        writeNestedTypes()
        buildFile("""
            import com.example.lower.Mid.Inner
            import com.example.lower.nested.Inner as NestedInner
            import static com.example.lower.Mid.Inner.X

            println "result=\${Inner.name} \${NestedInner.name} \$X"
        """)

        expect:
        succeeds("help")
        outputContains("result=com.example.lower\$Mid\$Inner com.example.lower\$nested\$Inner x")
    }

    private void writeNestedTypes() {
        // Nested class names differ in more than case, so their class files don't clash on case-insensitive file systems
        file("buildSrc/src/main/java/com/example/Outer.java") << """
            package com.example;
            public class Outer {
                public static class Mid { public static class Inner {} }
                public static class nested { public static class Inner {} }
            }
        """
        file("buildSrc/src/main/java/com/example/lower.java") << """
            package com.example;
            public class lower {
                public static class Inner {}
                public static class Mid { public static class Inner { public static final String X = "x"; } }
                public static class nested { public static class Inner {} }
            }
        """
    }

    def "resolves #description (#issue)"() {
        buildFile(script)

        expect:
        succeeds("help")
        outputContains("result=$expected")

        where:
        issue         | description                                            | expected                                      | script
        "GROOVY-5103" | "nested types through a star import of a class"       | "foo=bar"                                     | """
            import java.util.Map.*
            Entry entry = [foo: 'bar'].entrySet().first()
            println "result=\$entry"
        """
        "GROOVY-8559" | "repeated @Repeatable annotations"                     | "[a:1, b:2]"                                  | """
            import groovy.transform.NamedParam
            class C { static foo(@NamedParam('a') @NamedParam('b') Map args) { args } }
            println "result=\${C.foo(a: 1, b: 2)}"
        """
        "GROOVY-8531" | "nested types of a precompiled superclass"             | "NEW"                                         | """
            class MyThread extends Thread { State initial() { State.NEW } }
            println "result=\${new MyThread().initial()}"
        """
        "GROOVY-9281" | "nested type of a precompiled superclass as a value"   | "class java.util.AbstractMap\$SimpleImmutableEntry" | """
            class Foo extends AbstractMap {
                Set entrySet() { [] as Set }
                def cls() { SimpleImmutableEntry }
            }
            println "result=\${new Foo().cls()}"
        """
        "GROOVY-7916" | "nested type of a superclass through a subclass"       | "Hello"                                       | """
            class Base { static class Inner { static final String CONST = 'Hello' } }
            class Derived extends Base {}
            println "result=\${Derived.Inner.CONST}"
        """
        "GROOVY-8715" | "arrays of doubly nested types"                        | "0"                                           | """
            class A { static class B { static class C {} } }
            def test(A.B.C[] cs) { cs.length }
            println "result=\${test(new A.B.C[0])}"
        """
        "GROOVY-6742" | "method type parameters in an anonymous class"         | "0"                                           | """
            class Foo {
                static <T> Comparator<T> make() {
                    new Comparator<T>() { int compare(T a, T b) { 0 } }
                }
            }
            println "result=\${Foo.make().compare('a', 'b')}"
        """
        "GROOVY-6167" | "type parameters referenced before declaration"       | "[x]"                                         | """
            class Pair<A extends List<B>, B> { A a; B b }
            println "result=\${new Pair<List<String>, String>(a: ['x'], b: 'y').a}"
        """
        "GROOVY-10153"| "implicit bounds of wildcard type arguments"           | "ok"                                          | """
            class A {}
            class C extends A {}
            class Foo<T extends A> {}
            Foo<? super C> foo = new Foo<A>()
            println "result=ok"
        """
        "GROOVY-9632" | "type annotations in type arguments"                   | "[a]"                                         | """
            import java.lang.annotation.*
            @Target([ElementType.TYPE_USE]) @Retention(RetentionPolicy.RUNTIME) @interface TA {}
            def m() { List<@TA String> list = ['a']; list }
            println "result=\${m()}"
        """
        "GROOVY-11206"| "constant expressions in annotations of static classes" | "/bars/{barId}"                             | """
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @interface Foo { String value() }
            @groovy.transform.CompileStatic
            @Foo(BarController.SOME_URL)
            class BarController {
                public static final String BASE_URL = '/bars'
                public static final String SOME_URL = BASE_URL + '/{barId}'
            }
            println "result=\${BarController.getAnnotation(Foo).value()}"
        """
        "GROOVY-11711"| "outer type parameters after a static nested class"   | "null"                                        | """
            class Foo<T> {
                static class Bar {}
                class Baz implements java.util.concurrent.Callable<T> { T call() { null } }
            }
            println "result=\${new Foo.Baz(new Foo<Short>()).call()}"
        """
    }
}
