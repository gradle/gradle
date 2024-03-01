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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.CompiledLanguage

abstract class CrossTaskClassChangesIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {

    def "detects changed class in an upstream project"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("ImplA")
    }

    def "detects change to transitive superclass in an upstream project"() {
        createDirs("app")
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B extends A {}"]
        source app: ["class Unrelated {}", "class C extends B {}", "class D extends C {}"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("C", "D")
    }

    def "detects change to transitive dependency in an upstream project"() {
        createDirs("app")
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}"]
        source app: ["class Unrelated {}", "class C { public B b; }"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("C")
    }

    def "distinguishes between api and implementation changes"() {
        createDirs("app")
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}", "class C { private B b;}"]
        source app: ["class D { public B b; }", "class E { public C c; }"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("D")
    }

    def "detects deletions of transitive dependency in an upstream project"() {
        createDirs("app")
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}"]
        source app: ["class Unrelated {}", "class C { public B b; }"]
        app.snapshot {
            impl.snapshot {
                run language.compileTaskName
            }
        }

        when:
        file("api/src/main/${language.name}/A.${language.name}").delete()
        run "app:${language.compileTaskName}", "-x", "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
        app.recompiledClasses("C")
    }

    def "deletion of jar without dependents does not recompile any classes"() {
        source api: ["class A {}"], impl: ["class SomeImpl {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        clearImplProjectDependencies()

        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "deletion of jar with dependents causes compilation failure"() {
        source api: ["class A {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        clearImplProjectDependencies()
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "detects deleted class in an upstream project and fails compilation"() {
        def b = source(api: ["class A {}", "class B {}"])
        source impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        assert b.delete()
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "recompilation not necessary when upstream does not change any of the actual dependencies"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { String change; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "addition of unused class in upstream project does not rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class C { }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "removal of unused class in upstream project does not rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        def c = source api: ["class C { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        c.delete()
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "change to class referenced by an annotation recompiles annotated types"() {
        source api: [
            """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface B {
                    Class<?> value();
                }
            """,
            "class A {}"
        ], impl: [
            "class NoAnnotationClass {}",
            "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: [
            """
                class A { public void foo() {} }
            """
        ]
        run language.compileTaskName

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to class referenced by an array value in an annotation recompiles annotated types"() {
        source api: [
            """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface B {
                    Class<?>[] value();
                }
            """,
            "class A {}"
        ], impl: [
            "class X {}",
            "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }",
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: [
            """
                class A { public void foo() {} }
            """
        ]
        run language.compileTaskName

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField")
    }


    def "does not recompile on non-abi change across projects"() {
        source api: ["class A { }"],
            impl: ["class B { A a; }", "class C { B b; }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { \n}"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    // This test checks the current behavior, not necessarily the desired one.
    // If all classes are compiled by the same compile task, we do not know if a
    // change is an abi change or not. Hence, an abi change is always assumed.
    def "does recompile on non-abi changes inside one project"() {
        source impl: ["class A { }", "class B { A a; }", "class C { B b; }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source impl: ["class A { \n}"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'A', 'B', 'C'
    }

    def "recompiles downstream dependents of classes whose package-info changed"() {
        given:
        source api: ["""
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {}
        """]
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        packageFile.text = """@Deprecated package foo;"""
        source(
            api: ["package foo; public class A {}", "package bar; public class B {}"],
            impl: ["package baz; import foo.A; class C extends A {}", "package baz; import bar.B; class D extends B {}"]
        )

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.text = """@Deprecated @annotations.Anno package foo;"""
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }

    def "recompiles downstream dependents of classes whose package-info was added"() {
        given:
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        source(
            api: ["package foo; public class A {}", "package bar; public class B {}"],
            impl: ["package baz; import foo.A; class C extends A {}", "package baz; import bar.B; class D extends B {}"]
        )

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.text = """@Deprecated package foo;"""
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }

    def "recompiles downstream dependents of classes whose package-info was removed"() {
        given:
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        packageFile.text = """@Deprecated package foo;"""
        source(
            api: ["package foo; public class A {}", "package bar; public class B {}"],
            impl: ["package baz; import foo.A; class C extends A {}", "package baz; import bar.B; class D extends B {}"]
        )

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.delete()
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }
}

class CrossTaskClassChangesIncrementalJavaCompilationUsingClassDirectoryIntegrationTest extends CrossTaskClassChangesIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA
    boolean useJar = false
}

class CrossTaskClassChangesIncrementalJavaCompilationUsingJarIntegrationTest extends CrossTaskClassChangesIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA
    boolean useJar = true
}

class CrossTaskClassChangesIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest extends CrossTaskClassChangesIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.GROOVY
    boolean useJar = false
}

class CrossTaskClassChangesIncrementalGroovyCompilationUsingJarIntegrationTest extends CrossTaskClassChangesIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.GROOVY
    boolean useJar = true
}
