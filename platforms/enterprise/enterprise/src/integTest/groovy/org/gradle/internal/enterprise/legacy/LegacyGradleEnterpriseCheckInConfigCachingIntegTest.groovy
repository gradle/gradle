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

package org.gradle.internal.enterprise.legacy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotConfigCached)
class LegacyGradleEnterpriseCheckInConfigCachingIntegTest extends AbstractIntegrationSpec {

    def scanPlugin = new GradleEnterprisePluginLegacyContactPointFixture(testDirectory, mavenRepo, createExecuter())

    def "configuration caching is unsupported"() {
        given:
        settingsFile << scanPlugin.pluginManagement()

        scanPlugin.with {
            logConfig = true
            logApplied = true
            runtimeVersion = "3.3.4"
            publishDummyPlugin(executer)
        }

        buildFile << """
            task t
        """

        when:
        succeeds "t", "--configuration-cache"

        then:
        scanPlugin.assertUnsupportedMessage(output, "Build scans have been disabled due to incompatibility between your Develocity plugin version (3.3.4) and configuration caching. Please use Develocity plugin version 3.4 or later for compatibility with configuration caching.")
    }

}
