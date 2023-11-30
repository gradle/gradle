/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.isolated

import org.gradle.util.internal.ToBeImplemented

class IsolatedProjectsToolingApiParametrizedModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    @ToBeImplemented
    def "can query and cache parametrized models in the same build action"() {
        withParameterizedSomeToolingModelBuilderPluginInChildBuild("buildSrc")
        buildFile << """
            plugins.apply(my.MyPlugin)
            println("configuring build")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateStored {
            buildModelCreated()
            projectConfigured(":buildSrc")
            modelsCreated(":")
        }
        outputContains("configuring build")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        // TODO: this line should be present when the functionality is fixed
//        outputContains("creating model with parameter='fetch2' for root project 'root'")

        and:
        models.keySet() ==~ [":"]
        models.values().every { it.size() == 2 }

        models[":"][0].message == "fetch1 It works from project :"
        // TODO: this should be prefixed with `fetch2` when the functionality is fixed
        models[":"][1].message == "fetch1 It works from project :"

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")
    }

}
