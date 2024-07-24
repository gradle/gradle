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

package org.gradle.internal.cc.impl.tapi

import org.gradle.internal.cc.impl.actions.FetchCustomModelForEachProjectInTree

class ConfigurationCacheToolingApiCompositeBuildsIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

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
        withConfigurationCacheForModels()
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured = 4
        }

        when:
        withConfigurationCacheForModels()
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()

        when:
        file("buildSrc/src/main/groovy/Thing.java") << """
            // change source
            class Thing { }
        """

        withConfigurationCacheForModels()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateRecreated {
            taskInputChanged(":buildSrc:compileGroovy")
            projectConfigured = 4
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
        withConfigurationCacheForModels()
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured = 4
        }

        when:
        withConfigurationCacheForModels()
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        fixture.assertStateLoaded()

        when:
        file("plugins/src/main/groovy/Thing.java") << """
            // change source
            class Thing { }
        """

        withConfigurationCacheForModels()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateRecreated {
            taskInputChanged(":plugins:compileGroovy")
            projectConfigured = 4
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
        withConfigurationCacheForModels()
        def model = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model.size() == 2
        model[0].message == "It works from project :libs"
        model[1].message == "It works from project :libs:a"

        and:
        fixture.assertStateStored {
            projectConfigured = 5
        }

        when:
        withConfigurationCacheForModels()
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :libs"
        model2[1].message == "It works from project :libs:a"

        and:
        fixture.assertStateLoaded()

        when:
        file("libs/build.gradle") << """
            // some change
        """

        withConfigurationCacheForModels()
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInTree())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :libs"
        model3[1].message == "It works from project :libs:a"

        and:
        fixture.assertStateRecreated {
            fileChanged("libs/build.gradle")
            projectConfigured = 5
        }
    }
}
