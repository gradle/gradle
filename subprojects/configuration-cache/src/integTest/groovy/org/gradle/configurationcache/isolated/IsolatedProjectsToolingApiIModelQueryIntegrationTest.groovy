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

import org.gradle.configurationcache.fixtures.SomeToolingModel
import org.gradle.tooling.model.gradle.GradleBuild

class IsolatedProjectsToolingApiIModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "caches creation of custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")

        when:
        buildFile << """
            // some change
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = fetchModel()

        then:
        model3.message == "It works from project :"

        and:
        outputContains("Creating tooling model as configuration cache cannot be reused because file 'build.gradle' has changed.")
        outputContains("creating model for root project 'root'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
    }

    def "can ignore problems and cache custom model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works from project :"
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "Build file 'build.gradle': Cannot access project ':a' from project ':'",
                "Build file 'build.gradle': Cannot access project ':b' from project ':'",
            )
        }
        result.assertHasPostBuildOutput("Configuration cache entry stored with 2 problems.")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"
        outputContains("Reusing configuration cache.")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")
    }

    def "caches calculation of GradleBuild model"() {
        given:
        settingsFile << """
            include("a")
            include("b")
            println("configuring build")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 3
        model.projects[0].name == "root"
        model.projects[1].name == "a"
        model.projects[2].name == "b"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("configuring build")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = fetchModel(GradleBuild)

        then:
        model2.rootProject.name == "root"
        model2.projects.size() == 3
        model2.projects[0].name == "root"
        model2.projects[1].name == "a"
        model2.projects[2].name == "b"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("configuring build")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")
    }

    def "can query and cache different models"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
            println("configuring build")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("configuring build")
        outputContains("creating model for root project 'root'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model3 = fetchModel(GradleBuild)

        then:
        model3 instanceof GradleBuild
        model3.projects.size() == 1

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("configuring build")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = fetchModel(GradleBuild)

        then:
        model4 instanceof GradleBuild
        model4.projects.size() == 1

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model5 = fetchModel()

        then:
        model5 instanceof SomeToolingModel
        model5.message == "It works from project :"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")
    }
}
