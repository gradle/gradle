/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.http.BlockingHttpServer.ExpectedRequest
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule

class IdeaModelParallelArtifactDownloadIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)

    def setup() {
        server.start()
        settingsFile.touch()

        // For a fresh dependency cache
        toolingApi.requireIsolatedUserHome()
    }

    def "tooling API IdeaProject model downloads default, sources, and javadoc artifacts in parallel"(){
        given:
        def m1 = mavenRepo.module('test', 'test1', '1.0').artifact(classifier: 'sources').artifact(classifier: 'javadoc').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').artifact(classifier: 'sources').artifact(classifier: 'javadoc').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').artifact(classifier: 'sources').artifact(classifier: 'javadoc').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').artifact(classifier: 'sources').artifact(classifier: 'javadoc').publish()
        def modules = [m1, m2, m3, m4]

        buildFile << """
            plugins {
                id("java-library")
                id("idea")
            }

            repositories {
                maven {
                    url = '$server.uri'
                }
            }

            dependencies {
                implementation('test:test1:1.0')
                implementation('test:test2:1.0')
                implementation('test:test3:1.0')
                implementation('test:test4:1.0')
            }

            idea {
                module {
                    downloadSources = true
                    downloadJavadoc = true
                    scopes = [
                        'COMPILE': [
                            'plus': [configurations.runtimeClasspath],
                            'minus': []
                        ]
                    ]
                }
            }
        """

        // POMs are downloaded in parallel as part of graph resolution.
        server.expectConcurrent(modules.collect { m -> server.get(m.pom.path).sendFile(m.pom.file) } as ExpectedRequest[])

        // Prod artifacts
        server.expectConcurrent(modules.collect { m -> server.get(m.artifact.path).sendFile(m.artifact.file) } as ExpectedRequest[])

        // Sources
        // Optional artifacts (from Maven derived variants) are HEAD-probed in ExternalResourceResolver before the actual GET.
        server.expectConcurrent(modules.collect { m -> server.head(m.getArtifact(classifier: 'sources').path) } as ExpectedRequest[])
        server.expectConcurrent(modules.collect { m ->
            def a = m.getArtifact(classifier: 'sources')
            server.get(a.path).sendFile(a.file)
        } as ExpectedRequest[])

        // Javadoc
        server.expectConcurrent(modules.collect { m -> server.head(m.getArtifact(classifier: 'javadoc').path) } as ExpectedRequest[])
        server.expectConcurrent(modules.collect { m ->
            def a = m.getArtifact(classifier: 'javadoc')
            server.get(a.path).sendFile(a.file)
        } as ExpectedRequest[])

        expect:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.model(IdeaProject).withArguments('--max-workers', '4').get()
        }
    }

}
