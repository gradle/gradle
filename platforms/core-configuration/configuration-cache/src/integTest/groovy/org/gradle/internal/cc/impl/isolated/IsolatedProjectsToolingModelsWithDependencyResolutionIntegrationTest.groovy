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

class IsolatedProjectsToolingModelsWithDependencyResolutionIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "caches BuildAction that queries model that performs dependency resolution"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
            include("d")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":c"))
            }
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """
        file("d/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 4
        model[0].message == "project :a classpath = 2"
        model[1].message == "project :b classpath = 1"
        model[2].message == "project :c classpath = 0"
        model[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c", ":d")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 4
        model2[0].message == "project :a classpath = 2"
        model2[1].message == "project :b classpath = 1"
        model2[2].message == "project :c classpath = 0"
        model2[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 4
        model3[0].message == "project :a classpath = 2"
        model3[1].message == "project :b classpath = 1"
        model3[2].message == "project :c classpath = 0"
        model3[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":d", ":buildSrc")
        }
    }

    def "updates cached state when project dependency is added to project"() {
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
        fixture.assertStateLoaded()

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
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            projectConfigured(":b") // has not been consumed by project dependency previously, but is now
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
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
        fixture.assertStateLoaded()

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
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }
    }

    def "updates cached state when project dependency graph is added to project"() {
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
            dependencies {
                implementation(project(":c"))
            }
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
        model[1].message == "project :b classpath = 1"
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
        model2[1].message == "project :b classpath = 1"
        model2[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

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
        model3[0].message == "project :a classpath = 2"
        model3[1].message == "project :b classpath = 1"
        model3[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            projectConfigured(":b") // has not been consumed by project dependency previously, but is now
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 3
        model4[0].message == "project :a classpath = 2"
        model4[1].message == "project :b classpath = 1"
        model4[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 3
        model5[0].message == "project :a classpath = 2"
        model5[1].message == "project :b classpath = 1"
        model5[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }
    }

    def "updates cached state when project dependency graph is removed from project"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":c"))
            }
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 3
        model[0].message == "project :a classpath = 2"
        model[1].message == "project :b classpath = 1"
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
        model2[0].message == "project :a classpath = 2"
        model2[1].message == "project :b classpath = 1"
        model2[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle").replace('implementation(project(":b"))', "")

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 3
        model3[0].message == "project :a classpath = 0"
        model3[1].message == "project :b classpath = 1"
        model3[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 3
        model4[0].message == "project :a classpath = 0"
        model4[1].message == "project :b classpath = 1"
        model4[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 3
        model5[0].message == "project :a classpath = 0"
        model5[1].message == "project :b classpath = 1"
        model5[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":buildSrc")
        }
    }

    def "updates cached state when upstream project dependency changes"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation project(":b")
            }
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation project(":c")
            }
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 3
        model[0].message == "project :a classpath = 2"
        model[1].message == "project :b classpath = 1"
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
        model2[0].message == "project :a classpath = 2"
        model2[1].message == "project :b classpath = 1"
        model2[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("c/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 3
        model3[0].message == "project :a classpath = 2"
        model3[1].message == "project :b classpath = 1"
        model3[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("c/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b", ":c")
            modelsReused(":", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 3
        model4[0].message == "project :a classpath = 2"
        model4[1].message == "project :b classpath = 1"
        model4[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("b/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 3
        model5[0].message == "project :a classpath = 2"
        model5[1].message == "project :b classpath = 1"
        model5[2].message == "project :c classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b")
            modelsReused(":", ":c", ":buildSrc")
        }
    }

    def "caches BuildAction when there are cycles in the dependency graph"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
            include("d")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":c"))
            }
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":a"))
            }
        """
        file("d/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 4
        model[0].message == "project :a classpath = 3"
        model[1].message == "project :b classpath = 3"
        model[2].message == "project :c classpath = 3"
        model[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c", ":d")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 4
        model2[0].message == "project :a classpath = 3"
        model2[1].message == "project :b classpath = 3"
        model2[2].message == "project :c classpath = 3"
        model2[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            dependencies {
                implementation(project(":d"))
            }
        """
        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 4
        model3[0].message == "project :a classpath = 4"
        model3[1].message == "project :b classpath = 4"
        model3[2].message == "project :c classpath = 4"
        model3[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            projectConfigured(":d")
            modelsCreated(":a", ":b", ":c")
            modelsReused(":", ":d", ":buildSrc")
        }
    }

    def "caches phased build action that queries model that performs dependency resolution"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
            include("d")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":c"))
            }
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """
        file("d/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages = models.left
        messages.size() == 4
        messages[0] == "project :a classpath = 2"
        messages[1] == "project :b classpath = 1"
        messages[2] == "project :c classpath = 0"
        messages[3] == "project :d classpath = 0"

        def model = models.right
        model.size() == 4
        model[0].message == "project :a classpath = 2"
        model[1].message == "project :b classpath = 1"
        model[2].message == "project :c classpath = 0"
        model[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c", ":d")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages2 = models2.left
        messages2.size() == 4
        messages2[0] == "project :a classpath = 2"
        messages2[1] == "project :b classpath = 1"
        messages2[2] == "project :c classpath = 0"
        messages2[3] == "project :d classpath = 0"

        def model2 = models2.right
        model2.size() == 4
        model2[0].message == "project :a classpath = 2"
        model2[1].message == "project :b classpath = 1"
        model2[2].message == "project :c classpath = 0"
        model2[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            // some change
        """
        executer.withArguments(ENABLE_CLI)
        def models3 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages3 = models3.left
        messages3.size() == 4
        messages3[0] == "project :a classpath = 2"
        messages3[1] == "project :b classpath = 1"
        messages3[2] == "project :c classpath = 0"
        messages3[3] == "project :d classpath = 0"

        def model3 = models3.right
        model3.size() == 4
        model3[0].message == "project :a classpath = 2"
        model3[1].message == "project :b classpath = 1"
        model3[2].message == "project :c classpath = 0"
        model3[3].message == "project :d classpath = 0"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", ":b", ":c", ":d", ":buildSrc")
        }
    }
}
