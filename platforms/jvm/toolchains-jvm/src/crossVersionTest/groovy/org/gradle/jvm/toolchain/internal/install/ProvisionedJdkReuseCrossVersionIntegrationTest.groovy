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


import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.LeaksFileHandles

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

// We use foojay to resolve the toolchains, and we need to test specifically versions before the toolchain provisioning was fixed.
@IgnoreVersions({ !it.isSupportsToolchainsUsingFoojay() || it.isNonFlakyToolchainProvisioning() })
@LeaksFileHandles
class ProvisionedJdkReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    private static final String JAVA_HOME_PREFIX = "The application Java Home is "

    private static Path getPrintedJavaHome(ExecutionResult result) {
        Paths.get(result.getOutputLineThatContains(JAVA_HOME_PREFIX).takeAfter(JAVA_HOME_PREFIX))
    }

    private def userHome = file('user-home')
    private def jdkDir = userHome.toPath().resolve("jdks")

    def setup() {
        settingsFile << """
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
            }
        """
        buildFile << """
            plugins {
                id 'application'
                id 'java'
            }

            java.toolchain.languageVersion = JavaLanguageVersion.of(21)
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

    def "current version does not use jdk provisioned by previous version"() {
        given:

        when:
        def result = version previous withGradleUserHomeDir userHome withTasks 'run' withArguments '-i', '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def previousJavaHome = getPrintedJavaHome(result)
        previousJavaHome.startsWith(jdkDir)

        when:
        result = version current withGradleUserHomeDir userHome withTasks 'run' withArguments '-i', '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def currentJavaHome = getPrintedJavaHome(result)
        currentJavaHome.startsWith(jdkDir)
        currentJavaHome != previousJavaHome
    }

    def "current version's provisioned jdk is used by previous version"() {
        given:
        when:
        def result = version current withGradleUserHomeDir userHome withTasks 'run' withArguments '-i', '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        def currentJavaHome = getPrintedJavaHome(result)
        currentJavaHome.startsWith(jdkDir)

        when:
        result = version previous withGradleUserHomeDir userHome withTasks 'run' withArguments '-i', '-Porg.gradle.java.installations.auto-download=true' run()

        then:
        getPrintedJavaHome(result) == currentJavaHome
        try (def stream = Files.list(jdkDir)) {
            def entries = stream.collect(Collectors.toList())
            entries.size() == 1
            currentJavaHome.startsWith(entries.get(0).toAbsolutePath())
        }
    }
}
