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


class ConfigurationCacheToolingApiGradleLifecycleIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
        createDirs("a", "b") // avoid missing subproject directories warning
    }

    def "runs all lifecycle callbacks on cache miss"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")

            gradle.lifecycle.beforeProject {
                println("Callback before \$it")
            }
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withConfigurationCacheForModels()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        fixture.assertStateStored {
            projectConfigured = 4
        }

        and:
        outputContains("Callback before root project 'root'")
        outputContains("Callback before project ':a'")
        outputContains("Callback before project ':b'")


        when:
        withConfigurationCacheForModels()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        fixture.assertStateLoaded()
        outputDoesNotContain("Callback before")


        when:
        buildFile << """
            myExtension.message = "updated message for root"
        """

        withConfigurationCacheForModels()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "updated message for root"
        model3[1].message == "It works from project :a"

        and:
        fixture.assertStateRecreated {
            fileChanged("build.gradle")
            projectConfigured = 4
        }

        and:
        outputContains("Callback before root project 'root'")
        outputContains("Callback before project ':a'")
        outputContains("Callback before project ':b'")


        when:
        withConfigurationCacheForModels()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        fixture.assertStateLoaded()
        outputDoesNotContain("Callback before")


        when:
        file("a/build.gradle") << """
            myExtension.message = "updated message for :a"
        """

        withConfigurationCacheForModels()
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "updated message for root"
        model5[1].message == "updated message for :a"

        and:
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured = 4
        }

        and:
        outputContains("Callback before root project 'root'")
        outputContains("Callback before project ':a'")
        outputContains("Callback before project ':b'")
    }

}
