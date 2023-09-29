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

package org.gradle.configurationcache.isolated

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
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "this is the root project"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "this is the root project"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            myExtension.message = 'this is project a'
        """

        executer.withArguments(ENABLE_CLI)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "this is the root project"
        model5[1].message == "this is project a"

        and:
        fixture.assertStateUpdated {
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
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()

        when:
        settingsFile << """
            println("some new stuff")
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateRecreated {
            fileChanged("settings.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "It works from project :"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
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
        executer.withArguments(ENABLE_CLI, "-Pshared-input=12", "-Da-input=14")
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :a"
        model[1].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":c")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=12", "-Da-input=14")
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :a"
        model2[1].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=2", "-Da-input=14")
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :a"
        model3[1].message == "It works from project :b"

        and:
        // TODO - should not invalidate all cached state
        fixture.assertStateRecreated {
            gradlePropertyChanged()
            buildModelQueries = 1 // TODO:configuration-cache ???
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":a", ":b", ":c")
            modelsCreated(":a", ":b")
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=2", "-Da-input=14")
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "It works from project :a"
        model4[1].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=2", "-Da-input=2")
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "It works from project :a"
        model5[1].message == "It works from project :b"

        and:
        fixture.assertStateUpdated {
            systemPropertyChanged("a-input")
            projectConfigured(":buildSrc")
            projectsConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=2", "-Da-input=2")
        def model6 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model6.size() == 2
        model6[0].message == "It works from project :a"
        model6[1].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        executer.withArguments(ENABLE_CLI, "-Pshared-input=2", "-Da-input=2", "-Db-input=new")
        def model7 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model7.size() == 2
        model7[0].message == "It works from project :a"
        model7[1].message == "It works from project :b"

        and:
        fixture.assertStateUpdated {
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
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model.size() == 4
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model2.size() == 4
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model3.size() == 4
        model3[0].message == "this is the root project"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchModelsMultipleTimesForEachProject())

        then:
        model4.size() == 4
        model4[0].message == "this is the root project"
        model4[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
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
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.empty

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.empty

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            println("changed")
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.empty

        and:
        fixture.assertStateUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.empty

        and:
        fixture.assertStateLoaded()
    }

}
