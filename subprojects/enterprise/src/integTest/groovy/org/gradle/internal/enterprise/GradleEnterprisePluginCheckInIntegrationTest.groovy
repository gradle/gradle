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

}
