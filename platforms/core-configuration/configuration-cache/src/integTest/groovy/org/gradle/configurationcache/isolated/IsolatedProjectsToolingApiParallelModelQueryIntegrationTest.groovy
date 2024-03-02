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

package org.gradle.configurationcache.isolated


import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsToolingApiParallelModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "intermediate model is cached and reused for nested concurrent requests"() {
        withSomeToolingModelBuilderPluginInBuildSrc("""
            Thread.sleep(3000) // Simulate long-running builder to ensure overlap
        """)

        settingsFile << """
            rootProject.name = "root"
        """

        apply(testDirectory)

        // Prebuild buildSrc
        server.expect("configure-root")

        run()

        given:
        server.expectConcurrent("nested-1", "nested-2", "nested-3")
        server.expect("configure-root") // project will be configured only once
        server.expect("finished")

        when:
        withIsolatedProjects()
        def models = runBuildAction(new FetchCustomModelInParallel("${server.uri}"))

        then:
        models.size == 3
        models[0].message == "It works from project :"

        and:
        fixture.assertStateStored {
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":", 1)
        }
    }

    TestFile apply(TestFile dir) {
        def buildFile = dir.file("build.gradle")
        buildFile << """
            plugins {
                id("my.plugin")
            }
        """
        configuring(buildFile)
        return buildFile
    }

    TestFile configuring(TestFile buildFile) {
        buildFile << """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
    }
}
