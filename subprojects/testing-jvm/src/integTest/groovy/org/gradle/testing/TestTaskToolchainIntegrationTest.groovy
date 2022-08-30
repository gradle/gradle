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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import spock.lang.IgnoreIf

class TestTaskToolchainIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        file('src/test/java/ToolchainTest.java') << testClass("ToolchainTest")
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "can manually set java launcher via  #type toolchain on java test task #jdk"() {
        buildFile << """
            tasks.withType(JavaCompile).configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
            test {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """

        when:
        runWithToolchainConfigured(jdk)

        then:
        outputContains("Tests running with ${jdk.javaHome.absolutePath}")
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.differentJdk
        'current'      | Jvm.current()
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "Test task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.getDifferentVersion()
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }
        """

        when:
        runWithToolchainConfigured(someJdk)

        then:
        outputContains("Tests running with ${someJdk.javaHome.absolutePath}")
        noExceptionThrown()
    }

    def "uses toolchain launcher when nothing is explicitly configured"() {
        def jdk = Jvm.current()

        when:
        runWithToolchainConfigured(jdk)

        then:
        outputContains("Tests running with ${jdk.javaHome.absolutePath}")
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8) != null && AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) != null })
    def "uses toolchain launcher when setting executable path on fork options"() {
        def jdk8 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            tasks.withType(JavaCompile).configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk8.javaVersion.majorVersion})
                }
            }

            test {
                executable = "${pathString(jdk11.javaHome, "bin/javac")}"
            }
        """

        when:
        runWithToolchainConfigured(jdk8, jdk11)

        then:
        outputContains("Tests running with ${jdk11.javaHome.absolutePath}")
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8) != null && AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) != null })
    def "cannot configure both toolchain and executable path on fork options"() {
        def jdk8 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk8.javaVersion.majorVersion})
                }
            }

            test {
                executable = "${pathString(jdk11.javaHome, "bin/javac")}"
            }
        """

        when:
        executer.withArgument("-Porg.gradle.java.installations.paths=" + jdk8.javaHome.absolutePath + "," + jdk11.javaHome.absolutePath)
        fails("test")

        then:
        failureHasCause("Must not use `executable` property on `Test` together with `javaLauncher` property")
    }

    private static String testClass(String className) {
        return """
            import org.junit.*;

            public class $className {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }

    def runWithToolchainConfigured(Jvm... jvms) {
        def installationPaths = jvms.collect { it.javaHome.absolutePath }.join(",")
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
            .withArgument("--info")
            .withTasks("test")
            .run()
    }
}
