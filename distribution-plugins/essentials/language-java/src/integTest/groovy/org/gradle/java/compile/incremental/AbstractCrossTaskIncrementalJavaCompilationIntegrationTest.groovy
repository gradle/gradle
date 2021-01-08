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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild if same constant is used (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A { $constantType foo() { return $constantValue; }}", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        // impl.recompiledClasses('ImplA') //  Can re-enable with compiler plugins. See gradle/gradle#1474
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

    // This test describes the current behavior, not the intended one, expressed by the test just above
    def "changing an unused non-private constant causes full rebuild"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA', 'ImplB')
    }

    @NotYetImplemented
    //  Can re-enable with compiler plugins. See gradle/gradle#1474
    def "changing an unused non-private constant doesn't cause full rebuild"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
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
