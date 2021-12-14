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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ArtifactResolutionQueryParallelDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    public BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    /**
     * FIXME expected failure; will timeout after 60 seconds
     *   ArtifactResolutionQuery attempts to download artifacts in serial, whereas fixture BlockingHttpServer#expectConcurrent expects all 12 incoming requests before returning results
     */
    def "downloads artifacts in parallel from a Maven repo"() {
        def m1 = mavenRepo.module('test', 'test1', '1.0')
            .withJavadoc()
            .withSources()
            .publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0')
            .withJavadoc()
            .withSources()
            .publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0')
            .withJavadoc()
            .withSources()
            .publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0')
            .withJavadoc()
            .withSources()
            .publish()

        def m1javadoc = getJavadocArtifact(m1)
        def m1sources = getSourcesArtifact(m1)
        def m2javadoc = getJavadocArtifact(m2)
        def m2sources = getSourcesArtifact(m2)
        def m3javadoc = getJavadocArtifact(m3)
        def m3sources = getSourcesArtifact(m3)
        def m4javadoc = getJavadocArtifact(m4)
        def m4sources = getSourcesArtifact(m4)

        buildFile << """
            plugins {
                id 'java'
            }
            repositories {
                maven {
                    url = uri('$blockingServer.uri')
                }
            }

            dependencies {
                implementation 'test:test1:1.0'
                implementation 'test:test2:1.0'
                implementation 'test:test3:1.0'
                implementation 'test:test4:1.0'
            }

            tasks.register('resolve') {
                inputs.files configurations.compileClasspath
                doLast {
                    def componentIds = configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect { it.selected.id }

                    def result = dependencies.createArtifactResolutionQuery()
                         .forComponents(componentIds)
                         .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
                         .execute()
                }
            }
        """

        given:
        blockingServer.expectConcurrent(
            blockingServer.get(m1.pom.path).sendFile(m1.pom.file),
            blockingServer.get(m2.pom.path).sendFile(m2.pom.file),
            blockingServer.get(m3.pom.path).sendFile(m3.pom.file),
            blockingServer.get(m4.pom.path).sendFile(m4.pom.file))
        blockingServer.expectConcurrent(
            blockingServer.get(m1.artifact.path).sendFile(m1.artifact.file),
            blockingServer.get(m1javadoc.path).sendFile(m1javadoc.file),
            blockingServer.get(m1sources.path).sendFile(m1sources.file),
            blockingServer.get(m2.artifact.path).sendFile(m2.artifact.file),
            blockingServer.get(m2javadoc.path).sendFile(m2javadoc.file),
            blockingServer.get(m2sources.path).sendFile(m2sources.file),
            blockingServer.get(m3.artifact.path).sendFile(m3.artifact.file),
            blockingServer.get(m3javadoc.path).sendFile(m3javadoc.file),
            blockingServer.get(m3sources.path).sendFile(m3sources.file),
            blockingServer.get(m4.artifact.path).sendFile(m4.artifact.file),
            blockingServer.get(m4javadoc.path).sendFile(m4javadoc.file),
            blockingServer.get(m4sources.path).sendFile(m4sources.file))

        expect:
        executer.withArguments('--max-workers', '12')
        succeeds("resolve")
    }

    private ModuleArtifact getJavadocArtifact(MavenModule module) {
        return getArtifactByClassifier(module, 'javadoc')
    }

    private ModuleArtifact getSourcesArtifact(MavenModule module) {
        return getArtifactByClassifier(module, 'sources')
    }

    private ModuleArtifact getArtifactByClassifier(MavenModule module, String classifier) {
        return module.getArtifact(type: 'jar', classifier: classifier)
    }

}
