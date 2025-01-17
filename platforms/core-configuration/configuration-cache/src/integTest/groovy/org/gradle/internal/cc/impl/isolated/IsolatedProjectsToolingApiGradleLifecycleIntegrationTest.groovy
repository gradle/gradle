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

package org.gradle.internal.cc.impl.isolated


class IsolatedProjectsToolingApiGradleLifecycleIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "runs lifecycle callbacks only on reconfigured projects"() {
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
        // Intentionally do not apply the plugin to 'b'
        file("b/build.gradle") << ""

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":b")
            buildModelCreated()
            modelsCreated(":", ":a")
        }

        outputContains("Callback before root project 'root'")
        outputContains("Callback before project ':a'")
        outputContains("Callback before project ':b'")


        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        fixture.assertModelLoaded()
        outputDoesNotContain("Callback before")


        when:
        buildFile << """
            myExtension.message = "updated message for root"
        """

        withIsolatedProjects()
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "updated message for root"
        model3[1].message == "It works from project :a"

        fixture.assertModelUpdated {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":")
            modelsReused(":a", ":b", ":buildSrc")
        }

        outputContains("Callback before root project 'root'")
        outputDoesNotContain("Callback before project ':a'")
        outputDoesNotContain("Callback before project ':b'")


        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        fixture.assertModelLoaded()
        outputDoesNotContain("Callback before")


        when:
        file("a/build.gradle") << """
            myExtension.message = "updated message for :a"
        """

        withIsolatedProjects()
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "updated message for root"
        model5[1].message == "updated message for :a"

        and:
        fixture.assertModelUpdated {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a")
            modelsReused(":", "b", ":buildSrc")
        }

        outputContains("Callback before root project 'root'")
        outputContains("Callback before project ':a'")
        outputDoesNotContain("Callback before project ':b'")
    }

}
