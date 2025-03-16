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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitHttpRepository
import org.junit.Rule

class ParallelSourceDependencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer httpServer = new BlockingHttpServer()
    @Rule GitHttpRepository repo = new GitHttpRepository(httpServer, "test", temporaryFolder.getTestDirectory())

    def setup() {
        httpServer.start()

        settingsFile << """
            rootProject.name = 'consumer'
            gradle.rootProject {
                subprojects {
                    configurations {
                        compile
                    }
                    dependencies {
                        compile 'test:test:1.2'
                    }
                    tasks.register('resolve') {
                        // Not a dependency, so that cloning happens at execution time (in parallel)
                        doLast {
                            configurations.compile.each { }
                        }
                    }
                }
            }
            sourceControl.vcsMappings.withModule("test:test") {
                from(GitVersionControlSpec) {
                    url = uri('${repo.url}')
                }
            }
        """

        repo.file("settings.gradle") << """
            rootProject.name = 'test'
            gradle.rootProject {
                configurations.create('default')
                group = 'test'
                version = '1.2'
            }
        """
        repo.commit('initial')
        repo.createLightWeightTag('1.2')
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "can populate into same dir in parallel"() {
        given:
        createDirs("A", "B", "C", "D")
        settingsFile << """
            include 'A', 'B', 'C', 'D'
        """
        buildFile << """
            subprojects {
                def projectName = project.name
                tasks.resolve.doFirst {
                    ${httpServer.callFromBuildUsingExpression("projectName")}
                }
            }
        """

        when:
        // Wait for each project to list versions concurrently
        httpServer.expectConcurrent("A", "B", "C", "D")
        // Only one project should clone
        repo.expectListVersions()
        repo.expectCloneSomething()

        then:
        succeeds('resolve', '--parallel', '--max-workers=4')

        when:
        // Wait for each project to list versions concurrently
        httpServer.expectConcurrent("A", "B", "C", "D")
        // Only one project should list versions
        repo.expectListVersions()

        then:
        succeeds('resolve', '--parallel', '--max-workers=4')
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "can populate from multiple Gradle invocations in parallel"() {
        given:
        createDirs("A")
        settingsFile << """
            include 'A'
        """

        expect:
        // Wait for each build to list versions
        httpServer.expectConcurrent(repo.listVersions(), repo.listVersions())
        // Only one build should clone
        repo.expectCloneSomething()

        executer.withTasks('resolve')
        def build1 = executer.start()

        executer.withTasks('resolve')
        def build2 = executer.start()

        build1.waitForFinish()
        build2.waitForFinish()

        // Wait for each build to list versions
        httpServer.expectConcurrent(repo.listVersions(), repo.listVersions())

        executer.withTasks('resolve')
        def build3 = executer.start()

        executer.withTasks('resolve')
        def build4 = executer.start()

        build3.waitForFinish()
        build4.waitForFinish()
    }
}
