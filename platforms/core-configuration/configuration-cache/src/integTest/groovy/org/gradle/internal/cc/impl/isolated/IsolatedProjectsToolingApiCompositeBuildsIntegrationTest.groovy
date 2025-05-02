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

class IsolatedProjectsToolingApiCompositeBuildsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "invalidates cached state when plugin in buildSrc changes"() {
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
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

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
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()

        when:
        file("buildSrc/src/main/groovy/Thing.java") << """
            // change source
            class Thing { }
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertModelRecreated {
            taskInputChanged(":buildSrc:compileGroovy")
            projectConfigured(":buildSrc")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
    }

    def "invalidates cached state when plugin in child build changes"() {
        given:
        withSomeToolingModelBuilderPluginInChildBuild("plugins")
        settingsFile << """
            includeBuild("plugins")
            include("a")
            include("b")
        """
        buildFile << """
            plugins {
                id("my.plugin")
            }
        """
        file("a/build.gradle") << """
            plugins {
                id("my.plugin")
            }
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertModelStored {
            projectConfigured(":plugins")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertModelLoaded()

        when:
        file("plugins/src/main/groovy/Thing.java") << """
            // change source
            class Thing { }
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertModelRecreated {
            taskInputChanged(":plugins:compileGroovy")
            projectConfigured(":plugins")
            projectConfigured(":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }
    }

    def "caches BuildAction that queries models from included build "() {
        given:
        withSomeToolingModelBuilderPluginInChildBuild("plugins")
        settingsFile << """
            includeBuild("plugins")
            includeBuild("libs")
        """
        file("libs/settings.gradle") << """
            include("a")
            include("b")
        """
        file("libs/build.gradle") << """
            plugins {
                id("my.plugin")
            }
        """
        file("libs/a/build.gradle") << """
            plugins {
                id("my.plugin")
            }
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model.size() == 2
        model[0].message == "It works from project :libs"
        model[1].message == "It works from project :libs:a"

        and:
        fixture.assertModelStored {
            projectConfigured(":plugins")
            projectsConfigured(":", ":libs:b")
            buildModelCreated()
            modelsCreated(":libs", ":libs:a")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :libs"
        model2[1].message == "It works from project :libs:a"

        and:
        fixture.assertModelLoaded()

        when:
        file("libs/build.gradle") << """
            // some change
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :libs"
        model3[1].message == "It works from project :libs:a"

        and:
        fixture.assertModelUpdated {
            fileChanged("libs/build.gradle")
            projectConfigured(":plugins")
            projectsConfigured(":libs:a", ":libs:b") // TODO - should not be configured, but this currently happens to calculate the dependency substitutions
            modelsCreated(":libs")
            modelsReused(":", ":plugins", ":libs:a", ":libs:b")
        }
    }
}
