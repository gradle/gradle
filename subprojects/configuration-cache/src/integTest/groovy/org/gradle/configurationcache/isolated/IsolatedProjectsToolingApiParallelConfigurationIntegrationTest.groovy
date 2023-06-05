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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsToolingApiParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "projects are configured and models created in parallel when project scoped model is queried concurrently"() {
        withSomeToolingModelBuilderPluginInBuildSrc("""
            ${server.callFromBuildUsingExpression("'model-' + project.name")}
        """)
        settingsFile << """
            include("a")
            include("b")
            rootProject.name = "root"
        """
        apply(testDirectory)
        apply(file("a"))
        apply(file("b"))

        // Prebuild buildSrc
        server.expect("configure-root")
        server.expect("configure-a")
        server.expect("configure-b")
        run()

        given:
        server.expect("configure-root")
        server.expectConcurrent("model-root", "configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", ":a", ":b")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model2.size() == 3
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"
        model2[2].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            myExtension.message = 'this is project a'
        """
        file("b/build.gradle") << """
            myExtension.message = 'this is project b'
        """

        server.expect("configure-root")
        server.expectConcurrent("configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model3.size() == 3
        model3[0].message == "It works from project :"
        model3[1].message == "this is project a"
        model3[2].message == "this is project b"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b")
            modelsReused(":", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model4.size() == 3
        model4[0].message == "It works from project :"
        model4[1].message == "this is project a"
        model4[2].message == "this is project b"

        and:
        fixture.assertStateLoaded()
    }

    def "projects are configured in parallel when projects use plugins from included builds and project scoped model is queried concurrently"() {
        withSomeToolingModelBuilderPluginInChildBuild("plugins", """
            ${server.callFromBuildUsingExpression("'model-' + project.name")}
        """)
        settingsFile << """
            includeBuild("plugins")
            include("a")
            include("b")
            rootProject.name = "root"
        """
        // don't apply to root, as this is configured prior to the other projects, so the plugins are not resolved/built in parallel
        apply(file("a"))
        apply(file("b"))

        // Prebuild plugins
        server.expect("configure-a")
        server.expect("configure-b")
        run()

        given:
        server.expectConcurrent("configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0] == null
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectsConfigured(":plugins", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }
    }

    TestFile apply(TestFile dir) {
        def buildFile = dir.file("build.gradle")
        buildFile << """
            plugins {
                id("my.plugin")
            }
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
        return buildFile
    }
}
