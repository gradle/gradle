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

package org.gradle.internal.cc.impl.isolated

import org.gradle.internal.cc.impl.fixtures.SomeToolingModel

class IsolatedProjectsToolingApiParameterizedModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

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

    def "parameterized models are reused in the same build action"() {
        when:
        withIsolatedProjects()
        def models = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2", "fetch1", "fetch2"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel, SomeToolingModel) // only 2 models are created for 2 unique parameters of 4 requests
        }
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        outputContains("creating model with parameter='fetch2' for root project 'root'")

        and:
        models.keySet() ==~ [":"]
        models.values().every { it.size() == 4 }

        models[":"][0].message == "fetch1 It works from project :"
        models[":"][1].message == "fetch2 It works from project :"
        models[":"][2].message == "fetch1 It works from project :"
        models[":"][3].message == "fetch2 It works from project :"


        when:
        withIsolatedProjects()
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2", "fetch1", "fetch2"]))

        then:
        fixture.assertModelLoaded()
        outputDoesNotContain("configuring root")
        outputDoesNotContain("creating model")
    }

    def "parameterized models not reused if build action changes"() {
        settingsFile << """
            include("a")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            println("configuring \$project")
        """

        when:
        withIsolatedProjects()
        def model1 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1", "fetch2"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel, SomeToolingModel)
            modelsCreated(":a", SomeToolingModel, SomeToolingModel)
        }
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        outputContains("creating model with parameter='fetch2' for root project 'root'")
        outputContains("configuring project ':a'")
        outputContains("creating model with parameter='fetch1' for project ':a'")
        outputContains("creating model with parameter='fetch2' for project ':a'")

        and:
        model1.keySet() ==~ [":", ":a"]
        model1.values().every { it.size() == 2 }

        model1[":"][0].message == "fetch1 It works from project :"
        model1[":"][1].message == "fetch2 It works from project :"
        model1[":a"][0].message == "fetch1 It works from project :a"
        model1[":a"][1].message == "fetch2 It works from project :a"


        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch2", "fetch1"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel, SomeToolingModel)
            modelsCreated(":a", SomeToolingModel, SomeToolingModel)
        }
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        outputContains("creating model with parameter='fetch2' for root project 'root'")
        outputContains("configuring project ':a'")
        outputContains("creating model with parameter='fetch1' for project ':a'")
        outputContains("creating model with parameter='fetch2' for project ':a'")

        and:
        model2.keySet() ==~ [":", ":a"]
        model2.values().every { it.size() == 2 }

        model2[":"][0].message == "fetch2 It works from project :"
        model2[":"][1].message == "fetch1 It works from project :"
        model2[":a"][0].message == "fetch2 It works from project :a"
        model2[":a"][1].message == "fetch1 It works from project :a"
    }

    def "no parameterized models are reused when settings change"() {
        when:
        withIsolatedProjects()
        def models = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel)
        }
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")

        and:
        models.keySet() ==~ [":"]
        models.values().every { it.size() == 1 }
        models[":"][0].message == "fetch1 It works from project :"

        when:
        settingsFile << """
            println("configuring changed settings")
        """

        withIsolatedProjects()
        runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel)
        }
        outputContains("configuring changed settings")
        outputContains("configuring root")
        outputContains("creating model with parameter='fetch1' for root project 'root'")

        and:
        models.keySet() ==~ [":"]
        models.values().every { it.size() == 1 }
        models[":"][0].message == "fetch1 It works from project :"
    }

    def "parameterized models are reused for other projects when one project is reconfigured"() {
        ["a", "b"].forEach {
            settingsFile << """
                include("$it")
            """
            file("$it/build.gradle") << """
                plugins.apply(my.MyPlugin)
                println("configuring \$project")
            """
        }

        when:
        withIsolatedProjects()
        def model1 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1"]))

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(SomeToolingModel, ":", ":a", ":b")
        }
        outputContains("configuring root")
        outputContains("configuring project ':a'")
        outputContains("configuring project ':b'")
        outputContains("creating model with parameter='fetch1' for root project 'root'")
        outputContains("creating model with parameter='fetch1' for project ':a'")
        outputContains("creating model with parameter='fetch1' for project ':b'")

        and:
        model1.keySet() ==~ [":", ":a", ":b"]
        model1.values().every { it.size() == 1 }

        model1[":"][0].message == "fetch1 It works from project :"
        model1[":a"][0].message == "fetch1 It works from project :a"
        model1[":b"][0].message == "fetch1 It works from project :b"

        when:
        file("a/build.gradle") << """
            println("configuring changed \$project")
        """

        withIsolatedProjects()
        def model2 = runBuildAction(new FetchParameterizedCustomModelForEachProject(["fetch1"]))

        then:
        fixture.assertModelUpdated {
            fileChanged("a/build.gradle")
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":a", SomeToolingModel)
            modelsReused(":buildSrc", ":", ":b")
        }

        outputContains("configuring root")
        outputContains("configuring project ':a'")
        outputContains("creating model with parameter='fetch1' for project ':a'")

        outputDoesNotContain("creating model with parameter='fetch1' for root project 'root'")
        outputDoesNotContain("configuring project ':b'")
        outputDoesNotContain("creating model with parameter='fetch1' for project ':b'")

        and:
        model2.keySet() ==~ [":", ":a", ":b"]
        model2.values().every { it.size() == 1 }

        model2[":"][0].message == "fetch1 It works from project :"
        model2[":a"][0].message == "fetch1 It works from project :a"
        model2[":b"][0].message == "fetch1 It works from project :b"
    }

}
