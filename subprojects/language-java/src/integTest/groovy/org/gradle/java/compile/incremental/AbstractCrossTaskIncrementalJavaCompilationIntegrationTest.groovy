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
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A {}", "class ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}", "--info"

        then:
        outputContains("Full recompilation is required because an inlineable constant in 'B' has changed.")
        impl.recompiledClasses('ImplA', 'ImplB')

        where:
        constantType | constantValue
        'boolean'    | 'false'
        'byte'       | '(byte) 125'
        'short'      | '(short) 666'
        'int'        | '55542'
        'long'       | '5L'
        'float'      | '6f'
        'double'     | '7d'
        'String'     | '"foo"'
        'String'     | '"foo" + "bar"'
    }

    // This behavior is kept for backward compatibility - may be removed in the future
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when upstream module-info changes with manual module path"() {
        file("api/src/main/${language.name}/a/A.${language.name}").text = "package a; public class A {}"
        file("impl/src/main/${language.name}/b/B.${language.name}").text = "package b; import a.A; class B extends A {}"
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

        file("api/src/main/${language.name}/a/A.${language.name}").text = "package a; public class A {}"
        file("api/src/main/${language.name}/module-info.${language.name}") << """
            module api {
                exports a;
            }
        """
        file("otherApi/src/main/${language.name}/a2/A.${language.name}").text = "package a2; public class A {}"
        file("otherApi/src/main/${language.name}/module-info.${language.name}") << """
            module otherApi {
                exports a2;
            }
        """
        file("impl/src/main/${language.name}/b/B.${language.name}").text = "package b; class B extends a.A{}"
        file("impl/src/main/${language.name}/b/B2.${language.name}").text = "package b; class B2 extends a2.A{}"
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
                requires otherApi;
            }
        """
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

    def "recompiles in case of conflicting changing constant values"() {
        source api: ["class A { final static int x = 3; }", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return 3; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        source api: ["class A { final static int x = 2 ; final static int y = -2;  void blah() { /*  change irrelevant to constant value y */ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y')
    }
}
