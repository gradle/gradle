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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.tooling.model.gradle.GradleBuild

import javax.inject.Inject

class IsolatedProjectsToolingApiGracefulDegradationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "CC graceful degradation requests aren't honored if models were requested"() {
        given:
        settingsFile << ""
        buildFile """
            abstract class DegradingTask extends DefaultTask {
                @${Inject.name}
                abstract ${ConfigurationCacheDegradationController.name} getDegradationController()
            }
            tasks.register("foo", DegradingTask) { task ->
               getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
            }
        """

        when:
        withIsolatedProjects()
        fetchModel(GradleBuild, "foo")

        then:
        fixture.assertModelStored {
            runsTasks = true
            projectConfigured(":")
            buildModelCreated()
        }
    }
}
