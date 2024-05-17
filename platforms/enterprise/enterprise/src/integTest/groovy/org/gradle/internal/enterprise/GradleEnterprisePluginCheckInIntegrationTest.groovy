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
import org.gradle.util.internal.VersionNumber

import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_CONFIGURATION_CACHING
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_ISOLATED_PROJECTS
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.MINIMUM_SUPPORTED_PLUGIN_VERSION_SINCE_GRADLE_9
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_PLUGIN_DUE_TO_CONFIGURATION_CACHING_MESSAGE
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_PLUGIN_DUE_TO_ISOLATED_PROJECTS_MESSAGE
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE
import static org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE_MESSAGE

class GradleEnterprisePluginCheckInIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

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

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "shows warning message when unsupported Develocity plugin version is used with configuration caching enabled"() {
        given:
        plugin.runtimeVersion = pluginVersion
        plugin.artifactVersion = pluginVersion
        applyPlugin()
        settingsFile << """
            println "present: " + services.get($GradleEnterprisePluginManager.name).present
        """

        when:
        if (applied && VersionNumber.parse(pluginVersion) < MINIMUM_SUPPORTED_PLUGIN_VERSION_SINCE_GRADLE_9) {
            executer.expectDocumentedDeprecationWarning("Develocity plugin $pluginVersion has been deprecated. Starting with Gradle 9.0, only Develocity plugin 3.13.1 or newer is supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#unsupported_ge_plugin_3.13")
        }
        succeeds("t", "--configuration-cache")

        then:
        output.contains("present: ${applied}")

        and:
        output.contains("gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = $UNSUPPORTED_PLUGIN_DUE_TO_CONFIGURATION_CACHING_MESSAGE") != applied

        where:
        pluginVersion                               | applied
        '3.11.4'                                    | false
        minimumPluginVersionForConfigurationCaching | true
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
        output.contains("present: ${applied}")

        and:
        output.contains("gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = $UNSUPPORTED_PLUGIN_DUE_TO_ISOLATED_PROJECTS_MESSAGE") != applied

        where:
        pluginVersion                           | applied
        '3.11.4'                                | false
        minimumPluginVersionForIsolatedProjects | true
    }

    private static String getMinimumPluginVersionForConfigurationCaching() {
        "${MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_CONFIGURATION_CACHING.getMajor()}.${MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_CONFIGURATION_CACHING.getMinor()}"
    }

    private static String getMinimumPluginVersionForIsolatedProjects() {
        "${MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_ISOLATED_PROJECTS.getMajor()}.${MINIMUM_SUPPORTED_PLUGIN_VERSION_FOR_ISOLATED_PROJECTS.getMinor()}"
    }

}
