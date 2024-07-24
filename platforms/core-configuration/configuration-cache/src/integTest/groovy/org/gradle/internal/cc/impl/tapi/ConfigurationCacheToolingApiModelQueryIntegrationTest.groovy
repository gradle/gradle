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

import org.gradle.internal.cc.impl.actions.FetchCustomModelForEachProject
import org.gradle.internal.cc.impl.fixtures.SomeToolingModel
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild

class ConfigurationCacheToolingApiModelQueryIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
        createDirs("a", "b") // avoid missing subproject directories warning
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
        withConfigurationCacheForModels()
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        fixture.assertStateStored {
            projectConfigured = 4
        }
        outputContains("creating model for root project 'root'")

        when:
        withConfigurationCacheForModels()
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("creating model")

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """

        withConfigurationCacheForModels()
        def model3 = fetchModel()

        then:
        model3.message == "this is the root project"

        and:
        fixture.assertStateRecreated {
            fileChanged("build.gradle")
            projectConfigured = 4
        }
        outputContains("creating model for root project 'root'")

        when:
        withConfigurationCacheForModels()
        def model4 = fetchModel()

        then:
        model4.message == "this is the root project"

        and:
        fixture.assertStateLoaded()
    }

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

        when:
        withConfigurationCacheForModels()
        fetchModel(SomeToolingModel, ":dummyTask")

        then:
        fixture.assertStateStored {
            projectConfigured = 4
        }
        outputContains("Configuration of dummyTask")
        outputContains("Execution of dummyTask")

        when:
        withConfigurationCacheForModels()
        fetchModel(SomeToolingModel, ":dummyTask")

        then:
        fixture.assertStateLoaded()
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
        withConfigurationCacheForModels()
        fetchModel(SomeToolingModel, "help")

        then:
        notExecuted("help")
        fixture.assertStateStored {
            projectConfigured = 2
        }

        when:
        withConfigurationCacheForModels()
        fetchModel(SomeToolingModel, ":dummyTask")

        then:
        executed(":dummyTask")
        fixture.assertStateStored {
            projectConfigured = 2
        }
    }

    def "can ignore problems and cache custom model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
            gradle.buildFinished {
                println("build finished")
            }
        """

        when:
        withConfigurationCacheLenientForModels()
        def model = fetchModel()

        then:
        model.message == "It works from project :"
        fixture.assertStateStoredWithProblems {
            projectConfigured = 2
            problem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        withConfigurationCacheLenientForModels()
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"
        fixture.assertStateLoaded()
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
        withConfigurationCacheForModels()
        def model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 3
        model.projects[0].name == "root"
        model.projects[1].name == "a"
        model.projects[2].name == "b"

        and:
        fixture.assertStateStored {
            projectConfigured = 0
        }
        outputContains("configuring build")

        when:
        withConfigurationCacheForModels()
        def model2 = fetchModel(GradleBuild)

        then:
        model2.rootProject.name == "root"
        model2.projects.size() == 3
        model2.projects[0].name == "root"
        model2.projects[1].name == "a"
        model2.projects[2].name == "b"

        and:
        fixture.assertStateLoaded()
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
        withConfigurationCacheForModels()
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        fixture.assertStateStored {
            projectConfigured = 2
        }
        outputContains("configuring build")
        outputContains("creating model for root project 'root'")

        when:
        withConfigurationCacheForModels()
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")

        when:
        withConfigurationCacheForModels()
        def model3 = fetchModel(GradleProject)

        then:
        model3 instanceof GradleProject

        and:
        fixture.assertStateStored {
            projectConfigured = 2
        }
        outputContains("configuring build")
        outputDoesNotContain("creating model")

        when:
        withConfigurationCacheForModels()
        def model4 = fetchModel(GradleProject)

        then:
        model4 instanceof GradleProject

        and:
        fixture.assertStateLoaded()
        outputDoesNotContain("configuring build")
        outputDoesNotContain("creating model")

        when:
        withConfigurationCacheForModels()
        def model5 = fetchModel()

        then:
        model5 instanceof SomeToolingModel
        model5.message == "It works from project :"

        and:
        fixture.assertStateLoaded()
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
        withConfigurationCacheForModels()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertStateStored {
            projectConfigured = 4
        }

        when:
        file("buildSrc/build.gradle") << """
            // change it
        """

        and:
        withConfigurationCacheForModels()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertStateRecreated {
            fileChanged("buildSrc/build.gradle")
            projectConfigured = 4
        }

        and:
        withConfigurationCacheForModels()
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertStateLoaded()
    }
}
