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

        given:
        server.expect("configure-root")
        server.expectConcurrent("model-root", "configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        // TODO - get rid of usage of --parallel
        executer.withArguments(ENABLE_CLI, "--parallel")
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"
    }

    TestFile apply(TestFile dir) {
        def buildFile = dir.file("build.gradle")
        buildFile << """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
            plugins.apply(my.MyPlugin)
        """
        return buildFile
    }
}
