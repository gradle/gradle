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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.api.problems.ReportingScript.getProblemReportingScript

class DevelocityPluginEndOfBuildCallbackIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())
    def failingTaskName = "reportProblem"
    def succeedingTaskName = "succeedingTask"

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)

        buildFile """
            ${getProblemReportingScript """
                problems.forNamespace('org.example.plugin').throwing {
                    it.id('type', 'label')
                    .withException(new RuntimeException('failed'))
            }"""}

            task $succeedingTaskName
        """
    }

    def "end of build listener is notified on success"() {
        when:
        succeeds succeedingTaskName

        then:
        plugin.assertEndOfBuildWithFailure(output, null)

        when:
        succeeds succeedingTaskName

        then:
        plugin.assertEndOfBuildWithFailure(output, null)
    }

    def "end of build listener is notified on failure"() {
        when:
        fails failingTaskName

        then:
        plugin.assertEndOfBuildWithFailure(output, "org.gradle.internal.exceptions.LocationAwareException: Build file")

        when:
        fails failingTaskName

        then:
        // Note: we test less of the exception here because it's different in a build where configuration came from cache
        // In the non cache case, the exception points to the build file. In the from cache case it does not.
        plugin.assertEndOfBuildWithFailure(output, "org.gradle.internal.exceptions.LocationAwareException")
    }

    def "end of build listener may fail with an exception"() {
        when:
        fails succeedingTaskName, "-Dbuild-listener-failure"

        then:
        plugin.assertEndOfBuildWithFailure(output, null)
        failure.assertHasDescription("broken")

        when:
        fails succeedingTaskName, "-Dbuild-listener-failure"

        then:
        plugin.assertEndOfBuildWithFailure(output, null)
        failure.assertHasDescription("broken")
    }

}
