/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.autoapply

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.BUILD_SCAN_ERROR_MESSAGE_HINT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.EOF
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.NO
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.writeToStdInAndClose

@Requires(TestPrecondition.ONLINE)
@LeaksFileHandles
class AutoAppliedPluginsFunctionalTest extends AbstractPluginIntegrationTest {

    private static final String BUILD_SCAN_LICENSE_QUESTION = 'Publishing a build scan to scans.gradle.com requires accepting the Gradle Terms of Service defined at https://gradle.com/terms-of-service. Do you accept these terms?'
    private static final String BUILD_SCAN_SUCCESSFUL_PUBLISHING = 'Publishing build scan'
    private static final String BUILD_SCAN_PLUGIN_CONFIG_PROBLEM = 'The build scan was not published due to a configuration problem.'
    private static final String BUILD_SCAN_LICENSE_NOTE = 'The Gradle Terms of Service have not been agreed to.'
    private static final String BUILD_SCAN_LICENSE_ACCEPTANCE = 'Gradle Terms of Service accepted.'
    private static final String BUILD_SCAN_LICENSE_DECLINATION = 'Gradle Terms of Service not accepted.'
    private static final String BUILD_SCAN_WARNING = 'WARNING: The build scan plugin was applied after other plugins.'

    def "can auto-apply build scan plugin but does not ask for license acceptance in non-interactive console"() {
        given:
        buildFile << dummyBuildFile()

        when:
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        def result = gradleHandle.waitForFinish()
        result.assertNotOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertHasPostBuildOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_NOTE)
        result.assertNotOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
    }

    def "can auto-apply build scan plugin and accept license in interactive console"() {
        given:
        withInteractiveConsole()
        buildFile << dummyBuildFile()

        when:
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        writeToStdInAndClose(gradleHandle, YES.bytes)
        def result = gradleHandle.waitForFinish()
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_ACCEPTANCE)
        result.assertHasPostBuildOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
        result.assertNotOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertNotOutput(BUILD_SCAN_LICENSE_NOTE)
    }

    def "can auto-apply build scan plugin and decline license in interactive console"() {
        given:
        withInteractiveConsole()
        buildFile << dummyBuildFile()

        when:
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        writeToStdInAndClose(gradleHandle, NO.bytes)
        def result = gradleHandle.waitForFinish()
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_DECLINATION)
        result.assertHasPostBuildOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertNotOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
        result.assertNotOutput(BUILD_SCAN_LICENSE_NOTE)
        result.assertHasPostBuildOutput("The buildScan extension 'termsOfServiceAgree' value must be exactly the string 'yes' (without quotes).")
        result.assertHasPostBuildOutput("The value given was 'no'.")
    }

    def "can auto-apply build scan plugin and cancel license acceptance with ctrl-d in interactive console"() {
        given:
        withInteractiveConsole()
        buildFile << dummyBuildFile()

        when:
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        def result = gradleHandle.waitForFinish()
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertNotOutput(BUILD_SCAN_LICENSE_ACCEPTANCE)
        result.assertNotOutput(BUILD_SCAN_LICENSE_DECLINATION)
        result.assertHasPostBuildOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_NOTE)
        result.assertNotOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
    }

    @Issue("https://github.com/gradle/gradle/issues/3341")
    def "does not render warning message if other plugins are applied via plugin DSL"() {
        given:
        withInteractiveConsole()
        buildFile << """
            plugins {
                id 'org.gradle.hello-world' version '0.2'
            }
        """
        buildFile << dummyBuildFile()

        when:
        executer.withArgument("--debug")
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        def result = gradleHandle.waitForFinish()
        result.assertNotOutput(BUILD_SCAN_WARNING)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertNotOutput(BUILD_SCAN_LICENSE_ACCEPTANCE)
        result.assertNotOutput(BUILD_SCAN_LICENSE_DECLINATION)
        result.assertHasPostBuildOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_NOTE)
        result.assertNotOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
    }

    @Issue("https://github.com/gradle/gradle/issues/3516")
    def "renders build scan hint in build failure output if command line option was requested"() {
        given:
        withInteractiveConsole()
        buildFile << failingBuildFile()

        when:
        def gradleHandle = startBuildWithBuildScanCommandLineOption()

        then:
        writeToStdInAndClose(gradleHandle, YES.bytes)
        def result = gradleHandle.waitForFailure()
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_QUESTION)
        result.assertHasPostBuildOutput(BUILD_SCAN_LICENSE_ACCEPTANCE)
        result.assertHasPostBuildOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
        result.assertNotOutput(BUILD_SCAN_PLUGIN_CONFIG_PROBLEM)
        result.assertNotOutput(BUILD_SCAN_LICENSE_NOTE)
        result.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)
    }

    private void withInteractiveConsole() {
        executer.withTestConsoleAttached()
                .withConsole(ConsoleOutput.Plain)
                .withStdinPipe()
                .withForceInteractive(true)
    }

    private GradleHandle startBuildWithBuildScanCommandLineOption() {
        return executer.withTasks(DUMMY_TASK_NAME).withArgument('--scan').start()
    }

    static String dummyBuildFile() {
        "task $DUMMY_TASK_NAME"
    }

    static String failingBuildFile() {
        """
            task $DUMMY_TASK_NAME {
                doLast {
                    throw new GradleException('something went wrong')
                }
            }
        """
    }
}
