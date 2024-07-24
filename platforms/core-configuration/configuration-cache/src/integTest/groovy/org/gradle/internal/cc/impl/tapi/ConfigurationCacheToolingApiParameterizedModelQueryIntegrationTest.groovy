/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.tapi

import org.gradle.internal.cc.impl.actions.FetchParameterizedCustomModelForEachProject

class ConfigurationCacheToolingApiParameterizedModelQueryIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """

        withParameterizedSomeToolingModelBuilderPluginInChildBuild("buildSrc")

        buildFile << """
            plugins.apply(my.MyPlugin)
            println("configuring root")
        """
    }

    def "can cache parameterized models"() {
        when:
        withConfigurationCacheForModels()
        def models = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        outputContains("creating model with parameter='fetch2' for root project 'root'")

        and:
        models.keySet() ==~ [":"]
        models.values().every { it.size() == 2 }

        models[":"][0].message == "fetch1 It works from project :"
        models[":"][1].message == "fetch2 It works from project :"


        when:
        withConfigurationCacheForModels()
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring root")
        outputDoesNotContain("creating model")
    }

    def "can cache parameterized models with different parameters"() {
        when:
        withConfigurationCacheForModels()
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        when:
        withConfigurationCacheForModels()
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch2", "fetch1"]))

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        when:
        withConfigurationCacheForModels()
        def model1 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring root")
        outputDoesNotContain("creating model")

        and:
        model1.keySet() ==~ [":"]
        model1.values().every { it.size() == 2 }
        model1[":"][0].message == "fetch1 It works from project :"
        model1[":"][1].message == "fetch2 It works from project :"

        when:
        withConfigurationCacheForModels()
        def model2 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch2", "fetch1"]))

        then:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring root")
        outputDoesNotContain("creating model")

        and:
        model2.keySet() ==~ [":"]
        model2.values().every { it.size() == 2 }
        model2[":"][0].message == "fetch2 It works from project :"
        model2[":"][1].message == "fetch1 It works from project :"
    }

}
