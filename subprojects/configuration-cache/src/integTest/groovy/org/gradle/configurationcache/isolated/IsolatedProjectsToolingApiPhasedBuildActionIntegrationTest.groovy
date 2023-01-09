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

class IsolatedProjectsToolingApiPhasedBuildActionIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    @Ignore("https://github.com/gradle/gradle/issues/23196")
    def "caches execution of phased BuildAction that queries custom tooling model"() {
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
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":")
            modelsCreated(":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            // some change
        """

        executer.withArguments(ENABLE_CLI)
        def models3 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages3 = models3.left
        messages3.size() == 2
        messages3[0] == "It works from project :"
        messages3[1] == "It works from project :a"
        def model3 = models3.right
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }
        outputContains("creating model for root project 'root'")
    }

    def "caches execution of phased BuildAction that queries custom tooling model and that may, but does not actually, run tasks"() {
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
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            // Empty list means "run tasks defined by build logic or default tasks"
            forTasks([])
        }

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":")
            modelsCreated(":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks([])
        }

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")
    }

    def "caches execution of phased BuildAction that queries custom tooling model and that runs tasks"() {
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
            task thing { }
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks(["thing"])
        }

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":")
            modelsCreated(":a")
        }
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.ignoreBuildSrc.assertTasksExecuted(":a:thing")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks(["thing"])
        }

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")
        result.ignoreBuildSrc.assertTasksExecuted(":a:thing")
    }
}
