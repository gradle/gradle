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
}

class CrossTaskIncrementalJavaCompilationUsingClassDirectoryIntegrationTest extends CrossTaskIncrementalJavaCompilationIntegrationTest {
    boolean useJar = false
}

class CrossTaskIncrementalJavaCompilationUsingJarIntegrationTest extends CrossTaskIncrementalJavaCompilationIntegrationTest {
    boolean useJar = true
}
