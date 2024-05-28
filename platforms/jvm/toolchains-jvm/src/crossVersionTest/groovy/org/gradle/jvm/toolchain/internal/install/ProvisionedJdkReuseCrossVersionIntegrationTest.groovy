/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.jvm.toolchain.internal.install


import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JdkRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.singleUrlResolverCode

// We need to test specifically versions before the toolchain provisioning was fixed that support resolvers.
@IgnoreVersions({ !it.isSupportsCustomToolchainResolvers() || it.isNonFlakyToolchainProvisioning() })
@Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
class ProvisionedJdkReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    JdkRepository jdkRepository

    URI uri

    private static final String JAVA_HOME_PREFIX = "The application Java Home is "

    private static Path getPrintedJavaHome(ExecutionResult result) {
        Paths.get(result.getOutputLineThatContains(JAVA_HOME_PREFIX).takeAfter(JAVA_HOME_PREFIX))
    }

    private def userHome = file('user-home')
    private def jdkDir = userHome.toPath().resolve("jdks")

    def setup() {
        // Use a JVM that will force a provisioning
        Jvm differentVersion = AvailableJavaHomes.differentVersion

        jdkRepository = new JdkRepository(differentVersion, "not_current_jdk.zip")
        uri = jdkRepository.start()

        userHome.deleteDir()
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))
        buildFile << """
            plugins {
                id 'application'
                id 'java'
            }

            java.toolchain.languageVersion = JavaLanguageVersion.of(${differentVersion.javaVersion.majorVersion})
            application.mainClass = 'printer.JavaHomePrinter'
        """
        file('src/main/java/printer/JavaHomePrinter.java').text = """
            package printer;
            public class JavaHomePrinter {
                public static void main(String[] args) {
                    System.out.println("${JAVA_HOME_PREFIX}" + System.getProperty("java.home"));
                }
            }
        """
    }

    def cleanup() {
        jdkRepository.stop()
    }

    def "current version does not use jdk provisioned by previous version"() {
        given:
        jdkRepository.reset()

        when:
        def result = version previous withGradleUserHomeDir userHome withTasks 'run' withArguments '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def previousJavaHome = getPrintedJavaHome(result)
        previousJavaHome.startsWith(jdkDir)

        and:
        jdkRepository.expectHead()

        when:
        result = version current withGradleUserHomeDir userHome withTasks 'run' withArguments '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def currentJavaHome = getPrintedJavaHome(result)
        currentJavaHome.startsWith(jdkDir)
        currentJavaHome != previousJavaHome
    }

    def "current version's provisioned jdk is used by previous version"() {
        given:
        jdkRepository.reset()

        when:
        def result = version current withGradleUserHomeDir userHome withTasks 'run' withArguments '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def currentJavaHome = getPrintedJavaHome(result)
        currentJavaHome.startsWith(jdkDir)

        when:
        result = version previous withGradleUserHomeDir userHome withTasks 'run' withArguments '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        getPrintedJavaHome(result) == currentJavaHome
        try (def stream = Files.list(jdkDir)) {
            def entries = stream.collect(Collectors.toList())
            entries.size() == 1
            currentJavaHome.startsWith(entries.get(0).toAbsolutePath())
        }
    }
}
