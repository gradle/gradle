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
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE_MESSAGE
import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.MINIMUM_SUPPORTED_PLUGIN_VERSION

class DevelocityPluginCheckInIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            task t
            task f { doLast { throw new RuntimeException("failed") } }
        """
    }

    void applyPlugin() {
        settingsFile << plugin.plugins()
    }

    def "detects that the build scan plugin has been [applied=#applied]"() {
        given:
        if (applied) {
            applyPlugin()
        }

        settingsFile << """
            println "present: " + services.get($GradleEnterprisePluginManager.name).present
        """

        when:
        succeeds "t"

        then:
        output.contains("present: ${applied}")

        where:
        applied << [true, false]
    }

    def "can convey unsupported to plugin that supports it"() {
        given:
        applyPlugin()

        when:
        succeeds "t", "-D${UNSUPPORTED_TOGGLE}=true"

        then:
        plugin.assertUnsupportedMessage(output, UNSUPPORTED_TOGGLE_MESSAGE)
    }

    def "checkin happens once for build with buildSrc"() {
        given:
        applyPlugin()
        file("buildSrc/src/main/groovy/Thing.groovy") << "class Thing {}"

        when:
        succeeds "t"

        then:
        plugin.serviceCreatedOnce(output)

        when:
        succeeds "t"

        then:
        plugin.serviceCreatedOnce(output)
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "Isolated projects implies config cache")
    def "shows warning message when Develocity plugin version is used with isolated projects enabled"() {
        given:
        plugin.runtimeVersion = pluginVersion
        plugin.artifactVersion = pluginVersion
        applyPlugin()
        settingsFile << """
            println "present: " + services.get($GradleEnterprisePluginManager.name).present
        """

        when:
        succeeds("t", "-Dorg.gradle.unsafe.isolated-projects=true")

        then:
        output.contains("present: ${supported}")

        and:
        if (supported) {
            assert output.contains("develocityPlugin.checkIn.supported")
        } else {
            assert output.contains("develocityPlugin.checkIn.unsupported.reasonMessage = Gradle Enterprise plugin 3.13.1 has been disabled as it is incompatible with Isolated Projects. Upgrade to Gradle Enterprise plugin 3.15 or newer to restore functionality.")
        }

        where:
        pluginVersion                    | supported
        MINIMUM_SUPPORTED_PLUGIN_VERSION | false
        '3.15'                           | true
    }
}
