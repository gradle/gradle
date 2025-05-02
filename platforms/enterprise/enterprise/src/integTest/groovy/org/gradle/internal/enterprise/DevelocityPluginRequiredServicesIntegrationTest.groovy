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

import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DevelocityPluginRequiredServicesIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
    }

    def "required services are correct"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            task check {
                doLast {
                    def service = serviceRef.get()
                    def requiredServices = service._requiredServices

                    assert requiredServices.userInputHandler.is(services.get(${UserInputHandler.name}))
                    assert requiredServices.styledTextOutputFactory.is(services.get(${StyledTextOutputFactory.name}))
                    assert requiredServices.backgroundJobExecutors.is(services.get(${GradleEnterprisePluginBackgroundJobExecutors.name}))
                }
            }
        """

        when:
        succeeds("check")

        then:
        executed(":check")

        when:
        succeeds("check")

        then:
        executed(":check")
    }

}
