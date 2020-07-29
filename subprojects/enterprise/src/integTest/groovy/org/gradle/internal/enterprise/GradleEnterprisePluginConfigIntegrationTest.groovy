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

import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.NONE
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.REQUESTED
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.SUPPRESSED

class GradleEnterprisePluginConfigIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            task t
        """
    }

    def "has none requestedness if no switch present"() {
        when:
        succeeds "t"

        then:
        plugin.assertBuildScanRequest(output, NONE)
    }

    def "is requested with --scan"() {
        when:
        succeeds "t", "--scan"

        then:
        plugin.assertBuildScanRequest(output, REQUESTED)
    }

    def "is suppressed with --no-scan"() {
        when:
        succeeds "t", "--no-scan"

        then:
        plugin.assertBuildScanRequest(output, SUPPRESSED)
    }

}
