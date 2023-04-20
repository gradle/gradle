/*
 * Copyright 2014 the original author or authors.
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

abstract class CrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    def "compilation fails for private dependents on incompatible change"() {
        source api: ["class A { int method() { return 1; } }"],
            impl: ["class X { private int foo() { return new A().method(); }}", "class Y { private int foo() { return new A().method(); }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { int method1() { return 1; } }"]
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    // This behavior is kept for backward compatibility - may be removed in the future
    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles when upstream module-info changes with manual module path"() {
        source api: ["package a; public class A {}"], impl: ["package b; import a.A; class B extends A {}"]
        def moduleInfo = file("api/src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module api {
                exports a;
            }
        """
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
            }
        """
        file("impl/build.gradle") << """
            def layout = project.layout
            compileJava.doFirst {
                options.compilerArgs << "--module-path" << classpath.join(File.pathSeparator)
                classpath = layout.files()
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        moduleInfo.text = """
            module api {
            }
        """

        then:
        fails "impl:${language.compileTaskName}"
        result.hasErrorOutput("package a is not visible")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    @Issue("https://github.com/gradle/gradle/issues/23067")
    def "incremental compilation works with modules #description"() {
        file("impl/build.gradle") << """
            def layout = project.layout
            tasks.compileJava {
                modularity.inferModulePath = $inferModulePath
                options.compilerArgs.addAll($compileArgs)
                doFirst {
                    $doFirst
                }
            }
        """
        source api: ["package a; public class A {}"]
        file("api/src/main/${language.name}/module-info.${language.name}").text = """
            module api {
                exports a;
            }
        """
        source impl: [
            "package b; import a.A; import c.C; public class B extends A {}",
            "package c; public class C {}",
            "package c.d; public class D {}"
        ]
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
                exports b;
                exports c;
                exports c.d;
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        impl.snapshot { source api: "package a; public class A { void m1() {} }" }

        then:
        succeeds "impl:${language.compileTaskName}", "--info"
        impl.recompiledClasses("B", "module-info")

        where:
        description                 | inferModulePath | compileArgs                                                  | doFirst
        "with inferred module-path" | "true"          | "[]"                                                         | ""
        "with manual module-path"   | "false"         | "[\"--module-path=\${classpath.join(File.pathSeparator)}\"]" | "classpath = layout.files()"
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "incremental compilation detects if some exported package for compiled module was deleted #description"() {
        file("impl/build.gradle") << """
            def layout = project.layout
            tasks.compileJava {
                modularity.inferModulePath = $inferModulePath
                options.compilerArgs.addAll($compileArgs)
                doFirst {
                    $doFirst
                }
            }
        """
        source impl: ["package a; public class A {}", "package b; public class B {}"]
        file("impl/src/main/$languageName/module-info.$languageName").text = """
            module impl {
                exports a;
                exports b;
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        impl.snapshot { file("impl/src/main/$languageName/b/B.$languageName").delete() }

        then:
        runAndFail "impl:${language.compileTaskName}", "--info"
        impl.noneRecompiled()
        result.hasErrorOutput("module-info.java:4: error: package is empty or does not exist: b")

        where:
        description                 | inferModulePath | compileArgs                                                  | doFirst
        "with inferred module-path" | "true"          | "[]"                                                         | ""
        "with manual module-path"   | "false"         | "[\"--module-path=\${classpath.join(File.pathSeparator)}\"]" | "classpath = layout.files()"
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "incremental compilation works for multi-module project with manual module paths"() {
        file("impl/build.gradle") << """
            def layout = project.layout
            tasks.compileJava {
                modularity.inferModulePath = false
                options.compilerArgs << "--module-path=\${classpath.join(File.pathSeparator)}" \
                    << "--module-source-path" << file("src/main/$languageName")
                doFirst {
                    classpath = layout.files()
                }
            }
        """
        source api: "package a; public class A {}"
        def moduleInfo = file("api/src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module api {
                exports a;
            }
        """
        file("impl/src/main/${language.name}/my.module.first/b/B.java").text = "package b; import a.A; public class B extends A {}"
        file("impl/src/main/${language.name}/my.module.first/module-info.${language.name}").text = """
            module my.module.first {
                requires api;
                exports b;
            }
        """
        file("impl/src/main/${language.name}/my.module.second/c/C.java").text = "package c; import b.B; class C extends B {}"
        file("impl/src/main/${language.name}/my.module.second/module-info.${language.name}").text = """
            module my.module.second {
                requires my.module.first;
            }
        """
        file("impl/src/main/${language.name}/my.module.unrelated/unrelated/Unrelated.java").text = "package unrelated; class Unrelated {}"
        file("impl/src/main/${language.name}/my.module.unrelated/module-info.${language.name}").text = """
            module my.module.unrelated {
                exports unrelated;
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        impl.snapshot { source api: "package a; public class A { public void m1() {} }" }

        then:
        succeeds "impl:${language.compileTaskName}"
        // We recompile all module-info.java also for unrelated modules, but we don't recompile unrelated classes
        impl.recompiledFqn("my.module.first.b.B", "my.module.second.c.C", "my.module.first.module-info", "my.module.second.module-info", "my.module.unrelated.module-info")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles when upstream module-info changes"() {
        given:
        settingsFile << "include 'otherApi'"
        file("impl/build.gradle") << "dependencies { implementation(project(':otherApi')) }"

        file("api/src/main/${language.name}/module-info.${language.name}") << """
            module api {
                exports a;
            }
        """
        file("otherApi/src/main/${language.name}/module-info.${language.name}") << """
            module otherApi {
                exports a2;
            }
        """
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
                requires otherApi;
            }
        """
        source(
            api: ["package a; public class A {}"],
            otherApi: ["package a2; public class A {}"],
            impl: ["package b; class B extends a.A{}", "package b; class B2 extends a2.A{}"]
        )
        succeeds "impl:${language.compileTaskName}"

        when:
        file("$module/src/main/${language.name}/module-info.${language.name}").text = """
            module $module {
            }
        """

        then:
        fails "impl:${language.compileTaskName}"
        result.hasErrorOutput("package $pkg is not visible")

        where:
        module | pkg
        "api"  | "a"
        "otherApi" | "a2"
    }
}

class CrossTaskIncrementalJavaCompilationUsingClassDirectoryIntegrationTest extends CrossTaskIncrementalJavaCompilationIntegrationTest {
    boolean useJar = false
}

class CrossTaskIncrementalJavaCompilationUsingJarIntegrationTest extends CrossTaskIncrementalJavaCompilationIntegrationTest {
    boolean useJar = true
}
