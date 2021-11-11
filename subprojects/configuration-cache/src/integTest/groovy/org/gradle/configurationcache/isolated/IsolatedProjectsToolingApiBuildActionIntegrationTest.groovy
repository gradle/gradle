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

import spock.lang.Ignore

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
        fixture.assertStateRecreated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            modelsCreated(":")
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
    }

    def "caches BuildAction that queries model that performs dependency resolution"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
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
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 3
        model[0].message == "project :a classpath = 0"
        model[1].message == "project :b classpath = 0"
        model[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 3
        model2[0].message == "project :a classpath = 0"
        model2[1].message == "project :b classpath = 0"
        model2[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded {
        }

        when:
        file("a/build.gradle") << """
            dependencies {
                implementation(project(":b"))
            }
        """
        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 3
        model3[0].message == "project :a classpath = 1"
        model3[1].message == "project :b classpath = 0"
        model3[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            projectConfigured(":b") // was not involved in dependency resolution, but is now
            modelsCreated(":a")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 3
        model4[0].message == "project :a classpath = 1"
        model4[1].message == "project :b classpath = 0"
        model4[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded {
        }

        when:
        file("a/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 3
        model5[0].message == "project :a classpath = 1"
        model5[1].message == "project :b classpath = 0"
        model5[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            projectConfigured(":b") // should not be configured
            modelsCreated(":a")
        }
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
            project.providers.gradleProperty("some-input").get()
        """)
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, "-Psome-input=12")
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 1
        model[0].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":b")
            buildModelCreated()
            modelsCreated(":a")
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Psome-input=12")
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 1
        model2[0].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()

        when:
        executer.withArguments(ENABLE_CLI, "-Psome-input=2")
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 1
        model3[0].message == "It works from project :a"

        and:
        fixture.assertStateRecreated {
            gradlePropertyChanged("some-input")
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":b")
            modelsCreated(":a")
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Psome-input=2")
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 1
        model4[0].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
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
        fixture.assertStateRecreated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            modelsCreated(":")
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

    @Ignore("https://github.com/gradle/gradle/pull/18858 - Those phased build actions no longer have 'isRunsTasks' set to true")
    def "caches execution of phased BuildAction that queries custom tooling model and that runs tasks"() {
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
        fixture.assertStateRecreated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            modelsCreated(":")
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
