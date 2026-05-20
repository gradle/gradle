/*
 * Copyright 2026 the original author or authors.
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

import static org.gradle.initialization.StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME

class IsolatedProjectsToolingApiEnablementIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    static final String TOOLING_MODELS_CLI = "-D${PROPERTY_NAME}=tooling-models"

    def setup() {
        settingsFile """
            rootProject.name = 'root'
        """
    }

    def "model building uses IP when set to tooling-models"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(TOOLING_MODELS_CLI, CONFIGURE_ON_DEMAND_FOR_TOOLING, CACHING_FOR_TOOLING)
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            modelsCreated(":")
        }
    }

    def "model building uses IP when set to true"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            modelsCreated(":")
        }
    }
}
