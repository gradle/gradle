/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class ArtifactResolutionQueryIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    @Issue('https://github.com/gradle/gradle/issues/3579')
    @IntegrationTestTimeout(60)
    @UnsupportedWithConfigurationCache(because = "task uses artifact query API")
    def 'can use artifact resolution queries in parallel to file resolution'() {
        given:
        def module = mavenHttpRepo.module('group', "artifact", '1.0').publish()
        def handler = blockingServer.expectConcurrentAndBlock(blockingServer.get(module.pom.path).sendFile(module.pom.file), blockingServer.get('/sync'))
        blockingServer.expect(blockingServer.get(module.artifact.path).sendFile(module.artifact.file))

        createDirs("query", "resolve")
        settingsFile << 'include "query", "resolve"'
        buildFile << """
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier as DMI

allprojects {
    apply plugin: 'java'
    repositories {
       maven { url = '${blockingServer.uri}/repo' }
    }

    dependencies {
        implementation 'group:artifact:1.0'
    }
}

project('query') {
    task query {
        doLast {
            '${blockingServer.uri}/sync'.toURL().text
            dependencies.createArtifactResolutionQuery()
                        .forComponents(new DefaultModuleComponentIdentifier(DMI.newId('group','artifact'),'1.0'))
                        .withArtifacts(JvmLibrary)
                        .execute()
        }
    }
}

project('resolve') {
    task resolve {
        doLast {
            configurations.compileClasspath.files.collect { it.file }
        }
    }
}
"""
        executer.requireOwnGradleUserHomeDir().requireIsolatedDaemons()

        expect:
        def build = executer.withArguments('query:query', ':resolve:resolve', '--parallel').start()

        handler.waitForAllPendingCalls()
        handler.release('/sync')
        Thread.sleep(1000)
        handler.release(module.pom.path)

        build.waitForFinish()
    }

    @Issue('https://github.com/gradle/gradle/issues/11247')
    @ToBeFixedForConfigurationCache(because = "task uses artifact query API")
    def 'respects repository content filter'() {
        given:
        def module = mavenHttpRepo.module('group', "artifact", '1.0').publish()
        module.pom.allowGetOrHead()
        module.artifact.allowGetOrHead()

        buildFile << """
            apply plugin: 'java'
            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                    content {
                        onlyForAttribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
            }
            dependencies {
                implementation 'group:artifact:1.0'
            }
            task query {
                doLast {
                    assert configurations.compileClasspath.files
                    def id = configurations.compileClasspath.incoming.resolutionResult
                        .allDependencies.first().selected.id
                    dependencies.createArtifactResolutionQuery()
                        .forComponents(id)
                        .withArtifacts(JvmLibrary, JavadocArtifact)
                        .execute()
                }
            }
        """

        expect:
        succeeds('query')
    }

    @ToBeFixedForConfigurationCache(because = "task uses artifact query API")
    def "can resolve sources and javadoc for ivy repo"() {
        given:
        ivyRepo.module('group', "artifact", '1.0')
            .configuration("javadoc")
            .configuration("sources")
            .artifact(classifier: 'sources', conf: 'sources')
            .artifact(classifier: 'javadoc', conf: 'javadoc')
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }
            ${ivyTestRepository()}
            dependencies {
                implementation 'group:artifact:1.0'
            }
            task query {
                doLast {
                    def id = configurations.compileClasspath.incoming.resolutionResult
                        .allDependencies.first().selected.id
                    ArtifactResolutionResult result = dependencies.createArtifactResolutionQuery()
                        .forComponents(id)
                        .withArtifacts(JvmLibrary, JavadocArtifact, SourcesArtifact)
                        .execute()
                    assert result.resolvedComponents.first().artifactResults*.file*.name == ['artifact-1.0-javadoc.jar', 'artifact-1.0-sources.jar']
                }
            }
        """

        expect:
        succeeds('query')
    }
}
