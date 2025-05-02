/*
 * Copyright 2021 the original author or authors.
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

class IsolatedProjectsToolingApiBuildActionIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "caches execution of BuildAction that queries custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """
        // Intentionally don't apply to project b. Should split this case (some projects don't have the model available) out into a separate test

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "this is the root project"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertModelUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "this is the root project"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()

        when:
        file("a/build.gradle") << """
            myExtension.message = 'this is project a'
        """

        withIsolatedProjects()
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "this is the root project"
        model5[1].message == "this is project a"

        and:
        fixture.assertModelUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":buildSrc")
        }
        outputContains("creating model for project ':a'")
    }

    def "invalidates all cached models when build scoped input changes"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()

        when:
        settingsFile << """
            println("some new stuff")
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertModelRecreated {
            fileChanged("settings.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "It works from project :"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()
    }

    def "invalidates cached model when model builder input changes"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc("""
            project.providers.gradleProperty("shared-input").getOrNull()
            project.providers.systemProperty("\${project.name}-input").getOrNull()
        """)
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects("-Pshared-input=12", "-Da-input=14")
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :a"
        model[1].message == "It works from project :b"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":c")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        withIsolatedProjects("-Pshared-input=12", "-Da-input=14")
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :a"
        model2[1].message == "It works from project :b"

        and:
        fixture.assertModelLoaded()

        when:
        withIsolatedProjects("-Pshared-input=2", "-Da-input=14")
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :a"
        model3[1].message == "It works from project :b"

        and:
        // TODO - should not invalidate all cached state
        fixture.assertModelRecreated {
            gradlePropertyChanged()
            buildModelQueries = 1 // TODO:configuration-cache ???
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":a", ":b", ":c")
            modelsCreated(":a", ":b")
        }

        when:
        withIsolatedProjects("-Pshared-input=2", "-Da-input=14")
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "It works from project :a"
        model4[1].message == "It works from project :b"

        and:
        fixture.assertModelLoaded()

        when:
        withIsolatedProjects("-Pshared-input=2", "-Da-input=2")
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "It works from project :a"
        model5[1].message == "It works from project :b"

        and:
        fixture.assertModelUpdated {
            systemPropertyChanged("a-input")
            projectConfigured(":buildSrc")
            projectsConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }

        when:
        withIsolatedProjects("-Pshared-input=2", "-Da-input=2")
        def model6 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model6.size() == 2
        model6[0].message == "It works from project :a"
        model6[1].message == "It works from project :b"

        and:
        fixture.assertModelLoaded()

        when:
        withIsolatedProjects("-Pshared-input=2", "-Da-input=2", "-Db-input=new")
        def model7 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model7.size() == 2
        model7[0].message == "It works from project :a"
        model7[1].message == "It works from project :b"

        and:
        fixture.assertModelUpdated {
            systemPropertyChanged("b-input")
            projectConfigured(":buildSrc")
            projectsConfigured(":")
            modelsCreated(":b")
            modelsReused(":", ":a", ":c", ":buildSrc")
        }
    }

    def "caches execution of BuildAction that queries each model multiple times"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model.size() == 4
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model2.size() == 4
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model3.size() == 4
        model3[0].message == "this is the root project"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertModelUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model4.size() == 4
        model4[0].message == "this is the root project"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()
    }

    def "caches execution of BuildAction that queries nullable custom tooling model"() {
        given:
        withSomeNullableToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.empty

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.empty

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            println("changed")
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.empty

        and:
        fixture.assertModelUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.empty

        and:
        fixture.assertModelLoaded()
    }

    def "caches execution of different BuildAction types"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":")
        }
        outputContains("creating model for root project 'root'")

        and:
        model.size() == 1
        model[0].message == "It works from project :"


        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":")
        }
        outputContains("creating model for root project 'root'")

        and:
        model2.size() == 2
        model2[0].message == "It works from project :"


        when:
        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelLoaded()

        and:
        model3.size() == 1
        model3[0].message == "It works from project :"


        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        fixture.assertModelLoaded()

        and:
        model4.size() == 2
        model4[0].message == "It works from project :"
    }

    def "caches execution of BuildAction of same type with different state"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForTargetProject(":"))

        then:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":")
            buildModelCreated()
            modelsCreated(":", SomeToolingModel)
        }
        outputContains("creating model for root project 'root'")

        and:
        model.message == "It works from project :"


        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForTargetProject(":a"))

        then:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":", ":a")
            buildModelCreated()
            modelsCreated(":a", SomeToolingModel)
        }
        outputContains("creating model for project ':a'")

        and:
        model2.message == "It works from project :a"


        when:
        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForTargetProject(":"))

        then:
        fixture.assertModelLoaded()

        and:
        model3.message == "It works from project :"


        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForTargetProject(":a"))

        then:
        fixture.assertModelLoaded()

        and:
        model4.message == "It works from project :a"
    }

    def "does not cache execution of BuildAction when it fails"() {
        when:
        withIsolatedProjects()
        runBuildActionFails(new FailingBuildAction())

        then:
        fixture.assertNoConfigurationCache()
        failureDescriptionContains("Build action expectedly failed")

        // TODO:isolated should not contain this output https://github.com/gradle/gradle/issues/27476
        failure.assertOutputContains("Configuration cache entry stored.")
    }

}
