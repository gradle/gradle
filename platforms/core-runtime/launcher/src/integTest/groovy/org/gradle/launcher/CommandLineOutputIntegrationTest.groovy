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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

/**
 * Assertions over the output of certain command line invocations.
 */
class CommandLineOutputIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    private final TestNameTestDirectoryProvider alternativeTemporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor.class, reason = "No --version support in embedded executor")
    def "displays version message appropriately for Build JVM: #source"() {
        given:
        List<String> options = optionSetup(testDirectory)

        when:
        succeeds(options + ["--version"])

        then:
        outputContains(expectedOutput)

        where:
        source                   | optionSetup                                                                      | expectedOutput
        "Launcher JVM"           | { [] }                                                                           | "Requested Daemon JVM:  ${Jvm.current().javaHome.absolutePath} (no JDK specified, using Launcher JVM)"
        "Daemon JVM Criteria"    | { TestFile it -> setupDaemonJvmCriteria(it) }                                    | "Requested Daemon JVM:  Compatible with Java 17 (Daemon JVM criteria from gradle/gradle-daemon-jvm.properties)"
        "-Dorg.gradle.java.home" | { ["-Dorg.gradle.java.home=${Jvm.current().javaHome.absolutePath}".toString()] } | "Requested Daemon JVM:  '${Jvm.current().javaHome.absolutePath}' (from -Dorg.gradle.java.home)"
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor.class, reason = "No --version support in embedded executor")
    def "in subproject, displays version message appropriately for Build JVM: #source"() {
        given:
        def subProject = createDir("subproject")
        List<String> options = optionSetup(testDirectory)
        settingsFile("""
            include 'subproject'
        """)
        executer.inDirectory(subProject)

        when:
        succeeds(options + ["--version"])

        then:
        outputContains(expectedOutput)

        where:
        source                   | optionSetup                                                                      | expectedOutput
        "Launcher JVM"           | { [] }                                                                           | "Requested Daemon JVM:  ${Jvm.current().javaHome.absolutePath} (no JDK specified, using Launcher JVM)"
        "Daemon JVM Criteria"    | { TestFile it -> setupDaemonJvmCriteria(it) }                                    | "Requested Daemon JVM:  Compatible with Java 17 (Daemon JVM criteria from gradle/gradle-daemon-jvm.properties)"
        "-Dorg.gradle.java.home" | { ["-Dorg.gradle.java.home=${Jvm.current().javaHome.absolutePath}".toString()] } | "Requested Daemon JVM:  '${Jvm.current().javaHome.absolutePath}' (from -Dorg.gradle.java.home)"
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor.class, reason = "No --version support in embedded executor")
    def "using --project-dir, displays version message appropriately for Build JVM: #source"() {
        given:
        def unrelatedBuildDir = alternativeTemporaryFolder.testDirectory
        projectDir(unrelatedBuildDir)
        unrelatedBuildDir.file("settings.gradle") << "rootProject.name = 'unrelatedBuildDir'"
        List<String> options = optionSetup(unrelatedBuildDir)

        when:
        succeeds(options + ["--version"])

        then:
        outputContains(expectedOutput)

        where:
        source                   | optionSetup                                                                      | expectedOutput
        "Launcher JVM"           | { [] }                                                                           | "Requested Daemon JVM:  ${Jvm.current().javaHome.absolutePath} (no JDK specified, using Launcher JVM)"
        "Daemon JVM Criteria"    | { TestFile it -> setupDaemonJvmCriteria(it) }                                    | "Requested Daemon JVM:  Compatible with Java 17 (Daemon JVM criteria from gradle/gradle-daemon-jvm.properties)"
        "-Dorg.gradle.java.home" | { ["-Dorg.gradle.java.home=${Jvm.current().javaHome.absolutePath}".toString()] } | "Requested Daemon JVM:  '${Jvm.current().javaHome.absolutePath}' (from -Dorg.gradle.java.home)"
    }

    private static List<String> setupDaemonJvmCriteria(TestFile testDirectory) {
        testDirectory.file(DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE) << "${DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY}=17"
        return []
    }
}
