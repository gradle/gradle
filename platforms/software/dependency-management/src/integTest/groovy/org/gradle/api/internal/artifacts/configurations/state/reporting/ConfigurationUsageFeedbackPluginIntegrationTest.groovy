/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state.reporting

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConfigurationUsageFeedbackPluginIntegrationTest extends AbstractIntegrationSpec {
    def "can apply the plugin"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        when:
        succeeds("tasks")

        then:
        output.contains("No configurations were created in this build.")
    }

    def "reports on java-library"() {
        given:
        settingsFile << """
            plugins {
                id 'org.gradle.configuration-usage-reporting'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }
        """

        when:
        succeeds("build", "-S")

        then:
        output.contains("Configuration Usage Feedback Plugin")
        output.contains("Configuration Usage Feedback for Java Library")
    }
}
