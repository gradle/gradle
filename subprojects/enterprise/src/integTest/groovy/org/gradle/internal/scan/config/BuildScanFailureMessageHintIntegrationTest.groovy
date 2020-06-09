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

package org.gradle.internal.scan.config

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.BUILD_SCAN_ERROR_MESSAGE_HINT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryDefinition
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.LogLevelOption
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.StacktraceOption

@Issue("https://github.com/gradle/gradle/issues/3516")
@Requires(TestPrecondition.ONLINE)
class BuildScanFailureMessageHintIntegrationTest extends AbstractPluginIntegrationTest {

    private static final List<String> DUMMY_TASK_ONLY = [DUMMY_TASK_NAME]
    private static final List<String> DUMMY_TASK_AND_BUILD_SCAN = [DUMMY_TASK_NAME, "--$BuildScanOption.LONG_OPTION"]
    private static final String BUILD_SCAN_SUCCESSFUL_PUBLISHING = 'Publishing build scan'

    def "does not render hint for successful build without applied plugin"() {
        given:
        buildFile << """
            task $DUMMY_TASK_NAME
        """

        when:
        succeeds(DUMMY_TASK_NAME)

        then:
        result.assertNotOutput(BUILD_SCAN_ERROR_MESSAGE_HINT)
    }

    @Unroll
    def "renders hint for failing build without applied plugin and #description"() {
        given:
        buildFile << failingBuildFile()

        when:
        fails(DUMMY_TASK_ONLY + options as String[])

        then:
        failure.assertNotOutput(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)

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

    def "always renders hint for failing build if plugin was applied in plugins DSL and not requested for generation"() {
        given:
        settingsFile << appliedBuildScanPluginInPluginsDsl()
        buildFile << buildScanLicenseConfiguration()
        buildFile << failingBuildFile()

        when:
        fails(tasks as String[])

        then:
        output.contains(BUILD_SCAN_SUCCESSFUL_PUBLISHING) == buildScanPublished
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)

        where:
        tasks                     | buildScanPublished
        DUMMY_TASK_ONLY           | false
        DUMMY_TASK_AND_BUILD_SCAN | true
    }

    def "always renders hint for failing build if plugin was applied in buildscript and not requested for generation"() {
        given:
        settingsFile << appliedBuildScanPluginInBuildScript()
        buildFile << buildScanLicenseConfiguration()
        buildFile << failingBuildFile()

        when:
        fails(tasks as String[])

        then:
        output.contains(BUILD_SCAN_SUCCESSFUL_PUBLISHING) == buildScanPublished
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)

        where:
        tasks                     | buildScanPublished
        DUMMY_TASK_ONLY           | false
        DUMMY_TASK_AND_BUILD_SCAN | true
    }

    def "always renders hint for failing build if plugin was applied in initscript and not requested for generation"() {
        given:
        def initScriptFileName = 'init.gradle'
        file(initScriptFileName) << appliedBuildScanPluginInInitScript()
        buildFile << buildScanLicenseConfiguration()
        buildFile << failingBuildFile()


        when:
        fails(['-I', initScriptFileName] + tasks as String[])

        then:
        output.contains(BUILD_SCAN_SUCCESSFUL_PUBLISHING) == buildScanPublished
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)

        where:
        tasks                     | buildScanPublished
        DUMMY_TASK_ONLY           | false
        DUMMY_TASK_AND_BUILD_SCAN | true
    }

    def "always hint for failing build if plugin was applied in script plugin and not requested for generation"() {
        given:
        def scriptPluginFileName = 'scan.gradle'
        file(scriptPluginFileName) << appliedBuildScanPluginInScriptPlugin()
        settingsFile << """
            apply from: '$scriptPluginFileName'
        """
        buildFile << buildScanLicenseConfiguration()
        buildFile << failingBuildFile()

        when:
        fails(tasks as String[])

        then:
        output.contains(BUILD_SCAN_SUCCESSFUL_PUBLISHING) == buildScanPublished
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)

        where:
        tasks                     | buildScanPublished
        DUMMY_TASK_ONLY           | false
        DUMMY_TASK_AND_BUILD_SCAN | true
    }

    def "renders hint for failing build if plugin was applied in plugins DSL is configured to always publish"() {
        given:
        settingsFile << appliedBuildScanPluginInPluginsDsl()
        buildFile << buildScanLicenseConfiguration()
        buildFile << buildScanPublishAlwaysConfiguration()
        buildFile << failingBuildFile()

        when:
        fails(DUMMY_TASK_NAME)

        then:
        output.contains(BUILD_SCAN_SUCCESSFUL_PUBLISHING)
        failure.assertHasResolution(BUILD_SCAN_ERROR_MESSAGE_HINT)
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

    static String appliedBuildScanPluginInPluginsDsl() {
        """
            plugins {
                id 'com.gradle.enterprise' version '$AutoAppliedGradleEnterprisePlugin.VERSION'
            }
        """
    }

    static String appliedBuildScanPluginInBuildScript() {
        """
            buildscript {
                ${buildScanRepositoryAndDependency()}
            }

            apply plugin: "com.gradle.enterprise"
        """
    }

    static String appliedBuildScanPluginInInitScript() {
        """
            initscript {
                ${buildScanRepositoryAndDependency()}
            }

            beforeSettings {
                it.apply plugin: com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
            }
        """
    }

    static String appliedBuildScanPluginInScriptPlugin() {
        """
            buildscript {
                ${buildScanRepositoryAndDependency()}
            }

            apply plugin: com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
        """
    }

    private static String buildScanRepositoryAndDependency() {
        """
            repositories {
                ${gradlePluginRepositoryDefinition()}
            }

            dependencies {
                classpath "com.gradle:gradle-enterprise-gradle-plugin:$AutoAppliedGradleEnterprisePlugin.VERSION"
            }
        """
    }

    static String buildScanLicenseConfiguration() {
        """
            buildScan {
                termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                termsOfServiceAgree = 'yes'
            }
        """
    }

    static String buildScanPublishAlwaysConfiguration() {
        """
            buildScan {
                publishAlways()
            }
        """
    }
}
