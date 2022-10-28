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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class JavaExecToolchainIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/main/java/App.java") << """
            public class App {
               public static void main(String[] args) {
                 System.out.println("App running with " + System.getProperty("java.home"));
               }
            }
        """
    }

    def "can set java launcher via #type toolchain on manually created java exec task to #jdk with #plugin"() {
        buildFile << """
            plugins {
                id '${plugin}'
            }

            tasks.register("run", JavaExec) {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
                setJvmArgs(['-version'])
                mainClass = 'None'
            }
        """

        when:
        withInstallations(jdk).run(":run", "--info")

        then:
        executedAndNotSkipped(":run")
        outputContains("Command: ${jdk.javaHome.absolutePath}")

        where:
        type           | jdk                             | plugin
        'differentJdk' | AvailableJavaHomes.differentJdk | 'java-base'
        'current'      | Jvm.current()                   | 'java-base'
        'differentJdk' | AvailableJavaHomes.differentJdk | 'jvm-toolchains'
        'current'      | Jvm.current()                   | 'jvm-toolchains'
    }

    def "fails on toolchain and executable mismatch (with application plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        def compileWithVersion = [jdkCurrent, jdkOther].collect { it.javaVersion }.min()

        configureProjectWithApplicationPlugin(compileWithVersion)

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":run")

        then:
        failureDescriptionStartsWith("Execution failed for task ':run'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "fails on toolchain and executable mismatch (without application plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        configureProjectWithoutApplicationPlugin()

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":run")

        then:
        failureDescriptionStartsWith("Execution failed for task ':run'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "uses #what toolchain #when (with application plugin)"() {
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

        // Compile with the minimum version to make sure the runtime can execute the compiled class
        def compileWithVersion = [jdkCurrent, jdk1, jdk2].collect { it.javaVersion }.min()
        configureProjectWithApplicationPlugin(compileWithVersion)

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
        withInstallations(jdkCurrent, jdk1, jdk2).run(":run")

        then:
        executedAndNotSkipped(":run")
        outputContains("App running with ${targetJdk.javaHome.absolutePath}")

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

    def "uses #what toolchain #when (without application plugin)"() {
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

        configureProjectWithoutApplicationPlugin()

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureLauncher(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }

        when:
        withInstallations(jdkCurrent, jdk1, jdk2).run(":run", "--info")

        then:
        executedAndNotSkipped(":run")
        outputContains("Command: ${targetJdk.javaHome.absolutePath}")

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withExecutable
        "current JVM"   | "when toolchains are not configured" | false    | false
        "executable"    | "when configured"                    | false    | true
        "assigned tool" | "when configured"                    | true     | false
    }

    private TestFile configureProjectWithApplicationPlugin(JavaVersion compileWithVersion) {
        buildFile << """
            apply plugin: "application"

            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${compileWithVersion.majorVersion})
                }
            }

            application {
                mainClass = "App"
            }
        """
    }

    private TestFile configureProjectWithoutApplicationPlugin() {
        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            // Just outputting the JVM version that runs, instead of configuring a custom JavaCompile task
            task run(type: JavaExec) {
                setJvmArgs(['-version'])
                mainClass = 'None'
            }
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
            run {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureLauncher(Jvm jdk) {
        buildFile << """
            run {
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
