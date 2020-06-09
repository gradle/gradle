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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.internal.enterprise.core.GradleEnterprisePluginPresence
import org.gradle.internal.enterprise.impl.GradleEnterprisePluginManager
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.NONE
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.REQUESTED
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.SUPPRESSED

@Unroll
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

    @ToBeFixedForInstantExecution
    def "enabled and disabled are false with no flags"() {
        given:
        applyPlugin()

        when:
        succeeds "t"

        then:
        plugin.assertBuildScanRequest(output, NONE)
    }

    @ToBeFixedForInstantExecution
    def "enabled with --scan"() {
        given:
        applyPlugin()

        when:
        succeeds "t", "--scan"

        then:
        plugin.assertBuildScanRequest(output, REQUESTED)
    }

    @ToBeFixedForInstantExecution
    def "disabled with --no-scan"() {
        given:
        applyPlugin()

        when:
        succeeds "t", "--no-scan"

        then:
        plugin.assertBuildScanRequest(output, SUPPRESSED)
    }

    @ToBeFixedForInstantExecution
    def "warns if scan requested but no scan plugin applied"() {
        given:
        applyPlugin()
        plugin.doCheckIn = false

        when:
        succeeds "t", "--scan"

        then:
        plugin.issuedNoPluginWarning(output)
    }

    @ToBeFixedForInstantExecution
    def "does not warn if no scan requested but no scan plugin applied"() {
        given:
        applyPlugin()
        plugin.doCheckIn = false

        when:
        succeeds "t", "--no-scan"

        then:
        plugin.didNotIssuedNoPluginWarning(output)
    }

    @ToBeFixedForInstantExecution
    def "does not warn for each nested build if --scan used"() {
        given:
        applyPlugin()
        plugin.doCheckIn = false

        file("buildSrc/build.gradle") << ""
        file("a/buildSrc/build.gradle") << ""
        file("a/build.gradle") << ""
        file("a/settings.gradle") << ""
        file("b/buildSrc/build.gradle") << ""
        file("b/build.gradle") << ""
        file("b/settings.gradle") << ""
        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """
        buildFile.text = """
            task t
        """

        when:
        succeeds "t", "--scan"

        then:
        plugin.issuedNoPluginWarningCount(output, 1)
    }

    @ToBeFixedForInstantExecution
    def "detects that the build scan plugin has been [applied=#applied]"() {
        given:
        if (applied) {
            applyPlugin()
        }

        settingsFile << """
            println "present: " + services.get($GradleEnterprisePluginPresence.name).present
        """

        when:
        succeeds "t"

        then:
        output.contains("present: ${applied}")

        where:
        applied << [true, false]
    }

    @ToBeFixedForInstantExecution
    def "can convey unsupported to plugin that supports it"() {
        given:
        applyPlugin()

        when:
        succeeds "t", "-D${GradleEnterprisePluginManager.UNSUPPORTED_TOGGLE}=true"

        then:
        plugin.assertUnsupportedMessage(output, GradleEnterprisePluginManager.UNSUPPORTED_TOGGLE_MESSAGE)
    }

    @ToBeFixedForInstantExecution
    def "end of build listener is notified on success"() {
        given:
        applyPlugin()

        when:
        succeeds "t"

        then:
        plugin.assertEndOfBuildWithFailure(output, null)
    }

    @ToBeFixedForInstantExecution
    def "end of build listener is notified on failure"() {
        given:
        applyPlugin()

        when:
        fails "f"

        then:
        plugin.assertEndOfBuildWithFailure(output, "org.gradle.internal.exceptions.LocationAwareException: Build file")
    }

    private TestFile applyPlugin() {
        settingsFile << """
            plugins { id "$plugin.id" version "$plugin.artifactVersion" }
        """
    }

}
