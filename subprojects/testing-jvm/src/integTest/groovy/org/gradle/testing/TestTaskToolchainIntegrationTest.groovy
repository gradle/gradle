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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class TestTaskToolchainIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/test/java/ToolchainTest.java") << """
            import org.junit.*;

            public class ToolchainTest {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1,1);
               }
            }
        """
    }

    def "fails on toolchain and executable mismatch"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        def compileWithVersion = [jdkCurrent, jdkOther].collect { it.javaVersion }.min()

        configureProjectWithJavaPlugin(compileWithVersion)

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":test")

        then:
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "fails on toolchain and executable mismatch (without java-base plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        def compileWithVersion = [jdkCurrent, jdkOther].collect { it.javaVersion }.min()

        configureProjectWithoutJavaBasePlugin(compileWithVersion)

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":test")

        then:
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "uses #what toolchain #when"() {
        Jvm jdkCurrent = Jvm.current()
        Jvm jdk1 = AvailableJavaHomes.differentVersion
        Jvm jdk2 = AvailableJavaHomes.getDifferentVersion(jdk1.javaVersion)

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        Jvm targetJdk = jdkCurrent
        def useJdk = {
            if (targetJdk === jdkCurrent) {
                targetJdk = jdk1
                return jdk1
            } else {
                return jdk2
            }
        }

        def compileWithVersion = [jdkCurrent, jdk1, jdk2].collect { it.javaVersion }.min()

        configureProjectWithJavaPlugin(compileWithVersion)

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureLauncher(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }
        if (withJavaExtension) {
            configureJavaExtension(useJdk())
        }

        when:
        withInstallations(jdkCurrent, jdk1, jdk2).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${targetJdk.javaHome.absolutePath}")

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what             | when                                 | withTool | withExecutable | withJavaExtension
        "current JVM"    | "when toolchains are not configured" | false    | false          | false
        "java extension" | "when configured"                    | false    | false          | true
        "executable"     | "when configured"                    | false    | true           | false
        "assigned tool"  | "when configured"                    | true     | false          | false
        "executable"     | "over java extension"                | false    | true           | true
        "assigned tool"  | "over everything else"               | true     | false          | true
    }

    def "uses #what toolchain #when (without java-base plugin)"() {
        Jvm jdkCurrent = Jvm.current()
        Jvm jdk1 = AvailableJavaHomes.differentVersion
        Jvm jdk2 = AvailableJavaHomes.getDifferentVersion(jdk1.javaVersion)

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        Jvm targetJdk = jdkCurrent
        def useJdk = {
            if (targetJdk === jdkCurrent) {
                targetJdk = jdk1
                return jdk1
            } else {
                return jdk2
            }
        }

        def compileWithVersion = [jdkCurrent, jdk1, jdk2].collect { it.javaVersion }.min()

        configureProjectWithoutJavaBasePlugin(compileWithVersion)

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureLauncher(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }

        when:
        withInstallations(jdkCurrent, jdk1, jdk2).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Command: ${targetJdk.javaHome.absolutePath}")

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withExecutable
        "current JVM"   | "when toolchains are not configured" | false    | false
        "executable"    | "when configured"                    | false    | true
        "assigned tool" | "when configured"                    | true     | false
    }

    private TestFile configureProjectWithJavaPlugin(JavaVersion compileJavaVersion) {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.withType(JavaCompile).configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${compileJavaVersion.majorVersion})
                }
            }
        """
    }

    private TestFile configureProjectWithoutJavaBasePlugin(JavaVersion compileJavaVersion) {
        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            ${mavenCentralRepository()}

            configurations {
                testImplementation
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            def buildDir = project.layout.buildDirectory

            task compileJava(type: JavaCompile) {
                classpath = configurations.testImplementation
                source = project.layout.files("src/test/java")
                destinationDirectory = buildDir.dir("classes/java/test")
                sourceCompatibility = "${compileJavaVersion}"
                targetCompatibility = "${compileJavaVersion}"
            }

            task test(type: Test) {
                testClassesDirs = buildDir.files("classes/java/test")
                classpath = files(configurations.testImplementation, buildDir.dir("classes/java/test"))
                binaryResultsDirectory.set(buildDir.dir("test-results"))
                reports.junitXml.required.set(false)
                reports.html.outputLocation.set(buildDir.dir("test-results"))
            }

            test.dependsOn compileJava
        """
    }

    private TestFile configureJavaExtension(Jvm jdk) {
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }

    private TestFile configureExecutable(Jvm jdk) {
        buildFile << """
            test {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureLauncher(Jvm jdk) {
        buildFile << """
            test {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }

    private withInstallations(Jvm... jvm) {
        def installationPaths = jvm.collect { it.javaHome.absolutePath }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }

}
