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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture

class JavaCompileIncrementalOverrideIntegrationTest extends AbstractIntegrationSpec {

    def "system property disables incremental compilation for compileJava"() {
        given:
        javaProjectWithIncrementalCompilation()

        def outputs = new CompilationOutputsFixture(file("build/classes/java/main"))
        outputs.snapshot { succeeds("compileJava") }

        when:
        file("src/main/java/A.java").text = """
            public class A {
                public int value() {
                    return 2;
                }
            }
        """
        executer.withArguments("--info", "-Dorg.gradle.java.disable-incremental-compile=true")
        succeeds("compileJava")

        then:
        outputs.recompiledClasses("A", "B", "C")
    }

    def "gradle property disables incremental compilation for compileJava"() {
        given:
        javaProjectWithIncrementalCompilation()
        file("gradle.properties") << "org.gradle.java.disable-incremental-compile=true\n"

        def outputs = new CompilationOutputsFixture(file("build/classes/java/main"))
        outputs.snapshot { succeeds("compileJava") }

        when:
        file("src/main/java/A.java").text = """
            public class A {
                public int value() {
                    return 2;
                }
            }
        """
        executer.withArgument("--info")
        succeeds("compileJava")

        then:
        outputs.recompiledClasses("A", "B", "C")
    }

    def "environment variable disables incremental compilation for compileJava"() {
        given:
        javaProjectWithIncrementalCompilation()

        def outputs = new CompilationOutputsFixture(file("build/classes/java/main"))
        outputs.snapshot { succeeds("compileJava") }

        when:
        file("src/main/java/A.java").text = """
            public class A {
                public int value() {
                    return 2;
                }
            }
        """
        executer.withArgument("--info")
        executer.withEnvironmentVars(ORG_GRADLE_JAVA_DISABLE_INCREMENTAL_COMPILE: "true")
        succeeds("compileJava")

        then:
        outputs.recompiledClasses("A", "B", "C")
    }

    private void javaProjectWithIncrementalCompilation() {
        buildFile << """
            plugins {
                id("java")
            }

            tasks.withType(JavaCompile).configureEach {
                options.incremental = true
            }
        """

        file("src/main/java/A.java") << """
            public class A {
                public int value() {
                    return 1;
                }
            }
        """
        file("src/main/java/B.java") << """
            public class B extends A {
            }
        """
        file("src/main/java/C.java") << """
            public class C {
            }
        """
    }
}
