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

class IsolatedProjectsToolingApiCoupledProjectsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def "projects are treated as coupled when parent mutates child project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("build.gradle") << """
            project(":a") {
                plugins.apply(my.MyPlugin)
            }
            project(":b") {
                plugins.apply(my.MyPlugin)
            }
        """
        file("a/build.gradle") << """
            myExtension.message = "project a"
        """
        file("b/build.gradle") << """
            myExtension.message = "project b"
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 3
        model[0].message == "project a"
        model[1].message == "project b"
        model[2].message == "It works from project :c"

        and:
        fixture.assertStateStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c")
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 3
        model2[0].message == "project a"
        model2[1].message == "project b"
        model2[2].message == "It works from project :c"

        and:
        fixture.assertStateLoaded()

        when:
        file("build.gradle") << """
            project(":a") {
                afterEvaluate {
                    myExtension.message = "new project a"
                }
            }
        """
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 3
        // TODO - should use the updated message
        model3[0].message == "project a"
        model3[1].message == "project b"
        model3[2].message == "It works from project :c"

        and:
        fixture.assertStateRecreatedWithProblems {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            // TODO - should invalidate the model for projects a and b
            modelsCreated()
            problem("Build file 'build.gradle': Cannot access project ':a' from project ':'", 2)
            problem("Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }
    }

    def "projects are treated as coupled when child mutates parent project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            parent.plugins.apply(my.MyPlugin)
            parent.myExtension.message = "root project"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        // TODO - should include model from root
        model.size() == 1
        model[0].message == "It works from project :b"

        and:
        fixture.assertStateStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":a")
            buildModelCreated()
            // TODO - should create model for root
            modelsCreated(":b")
            problem("Build file 'a/build.gradle': Cannot access project ':' from project ':a'", 2)
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 1
        model2[0].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            // Some change
        """
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 1
        model3[0].message == "It works from project :b"

        and:
        fixture.assertStateRecreatedWithProblems {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":a")
            // TODO - should invalidate the model for root
            modelsCreated()
            problem("Build file 'a/build.gradle': Cannot access project ':' from project ':a'", 2)
        }
    }

    def "projects are treated as coupled when project queries a sibling project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            myExtension.message = "the message"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            def otherProject = project(':a')
            myExtension.message = otherProject.myExtension.message
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "the message"
        model[1].message == "the message"

        and:
        fixture.assertStateStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":c")
            buildModelCreated()
            modelsCreated(":a", ":b")
            problem("Build file 'b/build.gradle': Cannot access project ':a' from project ':b'")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "the message"
        model2[1].message == "the message"

        and:
        fixture.assertStateLoaded()

        when:
        file("b/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "the message"
        model3[1].message == "the message"

        and:
        fixture.assertStateRecreatedWithProblems {
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            // TODO - should invalidate the model for project a
            projectConfigured(":a")
            modelsCreated(":b")
            problem("Build file 'b/build.gradle': Cannot access project ':a' from project ':b'")
        }
    }
}
