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
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.junit.Assume.assumeNotNull

class JavadocToolchainIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

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

    def "fails on toolchain and executable mismatch (with java plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        configureProjectWithJavaPlugin()

        configureJavadocTool(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":javadoc")

        then:
        failureDescriptionStartsWith("Execution failed for task ':javadoc'.")
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
        failureDescriptionStartsWith("Execution failed for task ':javadoc'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javadocTool` property")
    }

    def "uses #what toolchain #when (with java plugin)"() {
        Jvm currentJdk = Jvm.current()
        Jvm otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        configureProjectWithJavaPlugin()

        if (withTool != null) {
            configureJavadocTool(selectJdk(withTool))
        }
        if (withExecutable != null) {
            configureExecutable(selectJdk(withExecutable))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion.toString())

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what             | when                                 | withTool | withExecutable | withJavaExtension | target
        "current JVM"    | "when toolchains are not configured" | null     | null           | null              | "current"
        "java extension" | "when configured"                    | null     | null           | "other"           | "other"
        "executable"     | "when configured"                    | null     | "other"        | null              | "other"
        "assigned tool"  | "when configured"                    | "other"  | null           | null              | "other"
        "executable"     | "over java extension"                | null     | "other"        | "current"         | "other"
        "assigned tool"  | "over everything else"               | "other"  | null           | "current"         | "other"
    }

    def "uses #what toolchain #when (without java-base plugin)"() {
        Jvm currentJdk = Jvm.current()
        Jvm otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        configureProjectWithoutJavaBasePlugin()

        if (withTool != null) {
            configureJavadocTool(selectJdk(withTool))
        }
        if (withExecutable != null) {
            configureExecutable(selectJdk(withExecutable))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion.toString())

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withExecutable | target
        "current JVM"   | "when toolchains are not configured" | null     | null           | "current"
        "executable"    | "when configured"                    | null     | "other"        | "other"
        "assigned tool" | "when configured"                    | "other"  | null           | "other"
    }

    def "fails if no toolchain has a javadoc tool"() {
        def jre = AvailableJavaHomes.differentVersionJreOnly
        assumeNotNull(jre)
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jre.javaVersionMajor})
                }
            }
        """

        when:
        withInstallations(jre).fails("javadoc")

        then:
        failure.assertHasCause("No locally installed toolchains match and toolchain auto-provisioning is not enabled.")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
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
}
