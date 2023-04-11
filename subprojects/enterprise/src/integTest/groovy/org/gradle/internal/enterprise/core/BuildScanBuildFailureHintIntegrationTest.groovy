/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.core

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.LogLevelOption
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.StacktraceOption

@Issue("https://github.com/gradle/gradle/issues/3516")
@Requires(TestPrecondition.ONLINE)
class BuildScanBuildFailureHintIntegrationTest extends AbstractIntegrationSpec {

    private static final List<String> DUMMY_TASK_ONLY = [DUMMY_TASK_NAME]
    private static final List<String> DUMMY_TASK_AND_BUILD_SCAN = [DUMMY_TASK_NAME, "--$BuildScanOption.LONG_OPTION"]

    def fixture = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << fixture.pluginManagement()
        fixture.publishDummyPlugin(executer)
    }

    def "does not render hint for successful build without applied plugin"() {
        given:
        buildFile << """
            task $DUMMY_TASK_NAME
        """

        when:
        succeeds(DUMMY_TASK_NAME)

        then:
        result.assertNotOutput(SCAN)
    }

    def "renders hint for failing build without applied plugin and #description"() {
        given:
        buildFile << failingBuildFile()

        when:
        fails(DUMMY_TASK_ONLY + options as String[])

        then:
        fixture.notApplied(output)
        failure.assertHasResolution(SCAN)

        where:
        options                                             | description
        []                                                  | 'no additional command line options'
        ["-$StacktraceOption.STACKTRACE_SHORT_OPTION"]      | 'stacktrace'
        ["-$StacktraceOption.FULL_STACKTRACE_SHORT_OPTION"] | 'full stacktrace'
        ["-$LogLevelOption.INFO_SHORT_OPTION"]              | 'info'
        ["-$LogLevelOption.DEBUG_SHORT_OPTION"]             | 'debug'
        ["-$LogLevelOption.WARN_SHORT_OPTION"]              | 'warn'
        ["-$LogLevelOption.QUIET_SHORT_OPTION"]             | 'quiet'
    }

    def "never renders hint for failing build if plugin was applied via command line argument and not requested for generation"() {
        given:
        buildFile << failingBuildFile()

        when:
        fails(DUMMY_TASK_AND_BUILD_SCAN as String[])

        then:
        fixture.appliedOnce(output)
        failure.assertNotOutput(SCAN)
    }

    def "never renders hint for failing build if plugin was applied in plugins DSL and not requested for generation"() {
        given:
        settingsFile << fixture.plugins()
        buildFile << failingBuildFile()

        when:
        fails(tasks as String[])

        then:
        fixture.appliedOnce(output)
        failure.assertNotOutput(SCAN)

        where:
        tasks << [ DUMMY_TASK_ONLY, DUMMY_TASK_AND_BUILD_SCAN ]
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
