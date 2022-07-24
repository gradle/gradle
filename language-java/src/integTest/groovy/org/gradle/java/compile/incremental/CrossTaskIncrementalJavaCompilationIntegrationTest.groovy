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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

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
    @Requires(TestPrecondition.JDK9_OR_LATER)
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

    @Requires(TestPrecondition.JDK9_OR_LATER)
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
