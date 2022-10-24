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

package org.gradle.api.tasks.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class JavadocToolchainIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/main/java/Lib.java") << """
            public class Lib {
               /**
                * Some API documentation.
                */
               public void foo() {
               }
            }
        """
    }

    def "changing toolchain invalidates task"() {
        def jdk1 = Jvm.current()
        def jdk2 = AvailableJavaHomes.getDifferentVersion()

        buildFile << """
            plugins {
                id 'java'
            }

            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    def version = providers.gradleProperty('test.javadoc.version').getOrElse('${jdk1.javaVersion.majorVersion}')
                    languageVersion = JavaLanguageVersion.of(version)
                }
            }
        """

        when:
        withInstallations(jdk1, jdk2).run(":javadoc")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        withInstallations(jdk1, jdk2).run(":javadoc")
        then:
        skipped(":javadoc")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdk2.javaVersion.majorVersion}")
        withInstallations(jdk1, jdk2).run(":javadoc")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdk2.javaVersion.majorVersion}")
        withInstallations(jdk1, jdk2).run(":javadoc")
        then:
        skipped(":javadoc")
    }

    def "fails on toolchain and executable mismatch"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        configureProjectWithJavaPlugin()

        configureJavadocTool(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":javadoc")

        then:
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javadocTool` property")
    }

    def "fails on toolchain and executable mismatch (without java-base plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        configureProjectWithoutJavaBasePlugin()

        configureJavadocTool(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":javadoc")

        then:
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javadocTool` property")
    }

    def "uses #what toolchain #when"() {
        def jdkCurrent = Jvm.current()
        def jdk1 = AvailableJavaHomes.differentVersion
        def jdk2 = AvailableJavaHomes.getDifferentVersion(jdk1.javaVersion)

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        def targetJdk = jdkCurrent
        def useJdk = {
            if (targetJdk === jdkCurrent) {
                targetJdk = jdk1
                return jdk1
            } else {
                return jdk2
            }
        }

        configureProjectWithJavaPlugin()

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureJavadocTool(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }
        if (withJavaExtension) {
            configureJavaExtension(useJdk())
        }

        when:
        withInstallations(jdkCurrent, jdk1, jdk2).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion.toString())

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
        def jdkCurrent = Jvm.current()
        def jdk1 = AvailableJavaHomes.differentVersion
        def jdk2 = AvailableJavaHomes.getDifferentVersion(jdk1.javaVersion)

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        def targetJdk = jdkCurrent
        def useJdk = {
            if (targetJdk === jdkCurrent) {
                targetJdk = jdk1
                return jdk1
            } else {
                return jdk2
            }
        }

        configureProjectWithoutJavaBasePlugin()

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureJavadocTool(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }

        when:
        withInstallations(jdkCurrent, jdk1, jdk2).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion.toString())

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withExecutable
        "current JVM"   | "when toolchains are not configured" | false    | false
        "executable"    | "when configured"                    | false    | true
        "assigned tool" | "when configured"                    | true     | false
    }

    private TestFile configureProjectWithJavaPlugin() {
        buildFile << """
            plugins {
                id 'java'
            }

            javadoc {
                options.jFlags("-version")
            }
        """
    }

    private TestFile configureProjectWithoutJavaBasePlugin() {
        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task javadoc(type: Javadoc) {
                source = project.layout.files("src/main/java")
                destinationDir = project.layout.buildDirectory.dir("docs/javadoc").get().getAsFile()
            }

            javadoc {
                options.jFlags("-version")
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
            javadoc {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javadocExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureJavadocTool(Jvm jdk) {
        buildFile << """
            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }

    private withInstallations(Jvm... jvm) {
        def installationPaths = jvm.collect { it.javaHome.absolutePath }.join(",")
        executer.withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }
}
