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

class GradleEnterprisePluginExecutionPhaseStartedCallbackIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            tasks.register("t")

            tasks.register("success")

            tasks.register("failure") {
                doLast {
                    throw new GradleException("Expected failure")
                }
            }
        """
    }

    def "receives execution phase started callback if build succeeds"() {
        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)

        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)
    }


    def "receives execution phase started callback if build fails"() {
        when:
        fails "failure"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)

        when:
        fails "failure"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)
    }

    def "does not receive execution phase started callback if configuration fails"() {
        given:
        buildFile << """
            throw new GradleException("Expected configuration failure")
        """
        when:
        fails "t"

        then:
        plugin.didNotInvokeExecutionPhaseStartedCallback(output)
    }
}
