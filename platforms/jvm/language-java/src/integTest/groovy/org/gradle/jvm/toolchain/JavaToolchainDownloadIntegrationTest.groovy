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

package org.gradle.jvm.toolchain

import net.rubygrapefruit.platform.internal.DefaultSystemInfo
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.os.OperatingSystem
import org.gradle.platform.internal.CurrentBuildPlatform

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.noUrlResolverCode

class JavaToolchainDownloadIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "fails for missing combination"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())}
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                    implementation = JvmImplementation.J9
                    vendor = JvmVendorSpec.ADOPTIUM
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=14, vendor=Eclipse Temurin, implementation=J9}. " +
                    "No matching toolchain could be found in the configured toolchain download repositories.")
               .assertHasResolutions(
                   DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                   DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain repositories at https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories."),
                   STACKTRACE_MESSAGE,
                   INFO_DEBUG,
                   SCAN,
                   GET_HELP)
    }

    def 'toolchain selection that requires downloading fails when it is disabled'() {
        settingsFile << """${applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())}"""

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                }
            }
        """

        propertiesFile << """
            org.gradle.java.installations.auto-download=false
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=14, vendor=any vendor, implementation=vendor-specific}. " +
                   "Toolchain auto-provisioning is not enabled.")
               .assertHasResolutions(
                   DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                   STACKTRACE_MESSAGE,
                   INFO_DEBUG,
                   SCAN,
                   GET_HELP)
    }

    def 'toolchain download on http fails'() {
        settingsFile << """${applyToolchainResolverPlugin("CustomToolchainResolver", unsecuredToolchainResolverCode())}"""

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=99, vendor=any vendor, implementation=vendor-specific}. " +
                   "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                   "({languageVersion=99, vendor=any vendor, implementation=vendor-specific}) from 'http://exoticJavaToolchain.com/java-99', " +
                   "due to: Attempting to download java toolchain from an insecure URI http://exoticJavaToolchain.com/java-99. This is not supported, use a secure URI instead.).")
    }

    private static String unsecuredToolchainResolverCode() {
        """
            @Override
            public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                URI uri = URI.create("http://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get());
                return Optional.of(JavaToolchainDownload.fromUri(uri));
            }
        """
    }

    private def getFailureMessageBuildPlatform() {
        def buildPlatform = new CurrentBuildPlatform(new DefaultSystemInfo(), OperatingSystem.current())
        return "for ${buildPlatform.operatingSystem} on ${buildPlatform.architecture.toString().toLowerCase()}"
    }

}
