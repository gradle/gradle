/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile

class JavaToolchainUpToDateIntegrationTest extends AbstractIntegrationSpec {

    def "compile and test reacting to toolchains are up-to-date without changes"() {
        def someJdk = AvailableJavaHomes.differentJdk
        buildscriptWithToolchain(someJdk, true)

        file("src/main/java/Foo.java") << "public class Foo {}"
        file("src/test/java/FooTest.java") << testClass("FooTest")

        when:
        runWithToolchainConfigured(someJdk)
        runWithToolchainConfigured(someJdk)

        then:
        outputContains("Task :compileJava UP-TO-DATE")
        outputContains("Task :compileTestJava UP-TO-DATE")
        outputContains("Task :test UP-TO-DATE")
    }

    def "compile and test not up-to-date once toolchain changed"() {
        def someJdk = AvailableJavaHomes.differentVersion
        buildscriptWithToolchain(someJdk)
        file("src/main/java/Foo.java") << """
            /** foo */
            public class Foo {
            }
        """

        file("src/test/java/FooTest.java") << testClass("FooTest")

        when:
        runWithToolchainConfigured(someJdk)
        runWithToolchainConfigured(someJdk)

        then:
        outputContains("Task :compileJava UP-TO-DATE")
        outputContains("Task :compileTestJava UP-TO-DATE")
        outputContains("Task :test UP-TO-DATE")
        outputContains("Task :javadoc UP-TO-DATE")

        when:
        buildscriptWithToolchain(Jvm.current())
        runWithToolchainConfigured(Jvm.current())


        then:
        outputDoesNotContain("Task :compileJava UP-TO-DATE")
        outputDoesNotContain("Task :compileTestJava UP-TO-DATE")
        outputDoesNotContain("Task :test UP-TO-DATE")
        outputDoesNotContain("Task :javadoc UP-TO-DATE")
        outputDoesNotContain("UnsupportedClassVersionError")
    }

    private TestFile buildscriptWithToolchain(Jvm someJdk, Boolean enableCompilerDaemonDebugging = false) {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }

            ${compilerDaemonDebugging(enableCompilerDaemonDebugging)}
        """
    }

    def compilerDaemonDebugging(Boolean enableDebug) {
        if (enableDebug) {
            return """
                tasks {
                    compileJava {
                        options.forkOptions.jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5006,suspend=y")
                    }
                }
            """
        } else {
            return ""
        }
    }

    def runWithToolchainConfigured(Jvm jvm) {
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jvm.javaHome.absolutePath)
            .withTasks("check", "javadoc")
            .run()
    }

    private static String testClass(String className) {
        return """
            import org.junit.*;

            public class $className {
               @Test
               public void test() {
                  Assert.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }

}
