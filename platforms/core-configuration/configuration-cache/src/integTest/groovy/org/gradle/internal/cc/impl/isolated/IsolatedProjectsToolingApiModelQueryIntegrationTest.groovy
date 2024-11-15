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
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.internal.ToBeImplemented

class IsolatedProjectsToolingApiModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "caches creation of custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
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
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        withIsolatedProjects()
        def model3 = fetchModel()

        then:
        model3.message == "this is the root project"

        and:
        fixture.assertModelUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":buildSrc")
        }
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model4 = fetchModel()

        then:
        model4.message == "this is the root project"

        and:
        fixture.assertModelLoaded()
    }

    @ToBeImplemented("when Isolated Projects becomes incremental for task execution")
    def "can cache models with tasks"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)

            tasks.register("dummyTask") {
                println("Configuration of dummyTask")
                doLast {
                    println("Execution of dummyTask")
                }
            }
        """

        when: "requesting a model together with running a task"
        withIsolatedProjects()
        fetchModel(SomeToolingModel, ":dummyTask")

        then: "only relevant projects are configured, while work graph and the model are stored in cache"
        fixture.assertModelStored {
            runsTasks = true
            // TODO:isolated desired behavior
//            projectsConfigured(":buildSrc", ":") // Note :a and :b were not configured
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            modelsCreated(":")
        }
        outputContains("Configuration of dummyTask")
        outputContains("Execution of dummyTask")

        when: "repeating the request"
        withIsolatedProjects()
        fetchModel(SomeToolingModel, ":dummyTask")

        then: "no projects are configured, work graph and the model are loaded, tasks are executed before the model is returned"
        fixture.assertModelLoaded {
            runsTasks = true
        }
        outputDoesNotContain("Configuration of dummyTask")
        outputDoesNotContain("creating model")
        outputContains("Execution of dummyTask")
    }

    def "can cache models with tasks using internal option"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)

            tasks.register("dummyTask") {
                println("Configuration of dummyTask")
                doLast {
                    println("Execution of dummyTask")
                }
            }
        """

        when: "requesting a model together with running a task"
        withIsolatedProjects("-Dorg.gradle.internal.isolated-projects.configure-on-demand.tasks=true")
        fetchModel(SomeToolingModel, ":dummyTask")

        then: "only relevant projects are configured, while work graph and the model are stored in cache"
        fixture.assertModelStored {
            runsTasks = true
            projectsConfigured(":buildSrc", ":") // Note :a and :b were not configured
            modelsCreated(":")
        }
        outputContains("Configuration of dummyTask")
        outputContains("Execution of dummyTask")

        when: "repeating the request"
        withIsolatedProjects("-Dorg.gradle.internal.isolated-projects.configure-on-demand.tasks=true")
        fetchModel(SomeToolingModel, ":dummyTask")

        then: "no projects are configured, work graph and the model are loaded, tasks are executed before the model is returned"
        fixture.assertModelLoaded {
            runsTasks = true
        }
        outputDoesNotContain("Configuration of dummyTask")
        outputDoesNotContain("creating model")
        outputContains("Execution of dummyTask")
    }

    def "can skip tasks execution during model building"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)

            tasks.register("dummyTask")
        """

        when:
        withIsolatedProjects()
        fetchModel(SomeToolingModel, "help")

        then:
        notExecuted("help")
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":")
        }

        when:
        withIsolatedProjects()
        fetchModel(SomeToolingModel, ":dummyTask")

        then:
        executed(":dummyTask")
        fixture.assertModelStored {
            runsTasks = true
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":")
        }
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
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works from project :"
        fixture.assertModelStoredWithProblems {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'", 2)
        }

        when:
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"
        fixture.assertModelLoaded()
    }

    def "caches calculation of GradleBuild model"() {
        given:
        settingsFile << """
            include("a")
            include("b")
            println("configuring build")
        """
        buildFile << """
            throw new RuntimeException("should not be called")
        """

        when:
        withIsolatedProjects()
        def model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 3
        model.projects[0].name == "root"
        model.projects[1].name == "a"
        model.projects[2].name == "b"

        and:
        fixture.assertModelStored {
            buildModelCreated()
        }
        outputContains("configuring build")

        when:
        withIsolatedProjects()
        def model2 = fetchModel(GradleBuild)

        then:
        model2.rootProject.name == "root"
        model2.projects.size() == 3
        model2.projects[0].name == "root"
        model2.projects[1].name == "a"
        model2.projects[2].name == "b"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("configuring build")
    }

    def "can query and cache different models"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
            println("configuring build")
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
        outputContains("configuring build")
        outputContains("creating model for root project 'root'")

        when:
        withIsolatedProjects()
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")

        when:
        withIsolatedProjects()
        def model3 = fetchModel(GradleProject)

        then:
        model3 instanceof GradleProject

        and:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            modelsCreated(":", 2) // Requested `GradleProject` and intermediate `IsolatedGradleProject`
        }
        outputContains("configuring build")
        outputDoesNotContain("creating model")

        when:
        withIsolatedProjects()
        def model4 = fetchModel(GradleProject)

        then:
        model4 instanceof GradleProject

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")

        when:
        withIsolatedProjects()
        def model5 = fetchModel()

        then:
        model5 instanceof SomeToolingModel
        model5.message == "It works from project :"

        and:
        fixture.assertModelLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")
    }

    def "can store fingerprint for reused projects"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        // Materializing of `ValueSource` at configuration time is leading to serializing its `Class`
        file("a/build.gradle") << """
            import org.gradle.api.provider.ValueSourceParameters

            plugins.apply(my.MyPlugin)

            abstract class MyValueSource implements ValueSource<String, ValueSourceParameters.None> {

                @Override
                String obtain() {
                    return "Foo"
                }
            }

            def a = providers.of(MyValueSource) {}
            println(a.get())
        """

        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        file("buildSrc/build.gradle") << """
            // change it
        """

        and:
        withIsolatedProjects()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelUpdated {
            fileChanged("buildSrc/build.gradle")
            projectsConfigured(":buildSrc")
            modelsCreated()
            modelsReused(":a", ":b", ":")
        }

        and:
        withIsolatedProjects()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelLoaded()
    }
}
