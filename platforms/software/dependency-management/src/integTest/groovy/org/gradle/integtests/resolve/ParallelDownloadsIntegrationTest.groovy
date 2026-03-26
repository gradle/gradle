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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.internal.resource.transport.http.DefaultHttpSettings
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class ParallelDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    public BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    String getAuthConfig() { '' }

    def "downloads artifacts in parallel from a Maven repo - #expression"() {
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').publish()

        buildFile << """
            repositories {
                maven {
                    url = '$blockingServer.uri'
                    $authConfig
                }
            }
            configurations { compile }
            dependencies {
                compile 'test:test1:1.0'
                compile 'test:test2:1.0'
                compile 'test:test3:1.0'
                compile 'test:test4:1.0'
            }
            task resolve {
                inputs.files configurations.compile
                def files = $expression
                doLast {
                    println files as List
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
            blockingServer.get(m2.artifact.path).sendFile(m2.artifact.file),
            blockingServer.get(m3.artifact.path).sendFile(m3.artifact.file),
            blockingServer.get(m4.artifact.path).sendFile(m4.artifact.file))

        expect:
        executer.withArguments('--max-workers', '4')
        succeeds("resolve")

        where:
        expression                                                                          | _
        "configurations.compile"                                                            | _
        "configurations.compile.files"                                                      | _
        "configurations.compile.incoming.files"                                             | _
        "configurations.compile.incoming.artifacts.artifactFiles"                           | _
        "configurations.compile.incoming.artifactView {}.files"                             | _
        "configurations.compile.incoming.artifactView { componentFilter { true } }.files"   | _
    }

    def "downloads artifacts in parallel from an Ivy repo"() {
        def m1 = ivyRepo.module('test', 'test1', '1.0').publish()
        def m2 = ivyRepo.module('test', 'test2', '1.0').publish()
        def m3 = ivyRepo.module('test', 'test3', '1.0').publish()
        def m4 = ivyRepo.module('test', 'test4', '1.0').publish()

        buildFile << """
            repositories {
                ivy {
                    url = '$blockingServer.uri'
                    $authConfig
                }
            }
            configurations { compile }
            dependencies {
                compile 'test:test1:1.0'
                compile 'test:test2:1.0'
                compile 'test:test3:1.0'
                compile 'test:test4:1.0'
            }
            task resolve {
                def files = configurations.compile
                inputs.files files
                doLast {
                    println files
                }
            }
"""

        given:
        blockingServer.expectConcurrent(
            blockingServer.get(m1.ivy.path).sendFile(m1.ivy.file),
            blockingServer.get(m2.ivy.path).sendFile(m2.ivy.file),
            blockingServer.get(m3.ivy.path).sendFile(m3.ivy.file),
            blockingServer.get(m4.ivy.path).sendFile(m4.ivy.file))
        blockingServer.expectConcurrent(
            blockingServer.get(m1.jar.path).sendFile(m1.jar.file),
            blockingServer.get(m2.jar.path).sendFile(m2.jar.file),
            blockingServer.get(m3.jar.path).sendFile(m3.jar.file),
            blockingServer.get(m4.jar.path).sendFile(m4.jar.file))

        expect:
        executer.withArguments('--max-workers', '4')
        succeeds("resolve")
    }

    def "downloads more dependencies in parallel than the number of max workers"() {
        def maxWorkers = 2 // Some arbitrary small number of max workers

        // Ideally this would be the same as the metadata parallelism, but since we perform
        // artifact transforms and artifact downloads in the same executor, we currently
        // constrain artifact download parallelism to the max workers.
        def expectedArtifactParallelism = maxWorkers
        def expectedMetadataParallelism = DefaultHttpSettings.DEFAULT_MAX_PER_ROUTE

        def maxParallelism = Math.max(expectedMetadataParallelism, expectedArtifactParallelism)
        // Create a number of dependencies equal to some some multiple of the max expected parallelism
        List<MavenModule> dependencies = (0..<(maxParallelism * 3)).collect {
            mavenRepo.module('test', "test$it", '1.0').publish()
        }

        buildFile << """
            repositories {
                maven {
                    url = '$blockingServer.uri'
                    $authConfig
                }
            }
            configurations {
                compile
            }
            dependencies {
                ${dependencies.collect { "compile('test:${it.artifactId}:1.0')" }.join('\n')}
            }
            task resolve {
                def files = configurations.compile
                inputs.files files
                doLast {
                    println files
                }
            }
        """

        given:
        def metadataRequests = blockingServer.expectConcurrentAndBlock(
            expectedMetadataParallelism,
            dependencies.collect {
                blockingServer.get(it.pom.path).sendFile(it.pom.file)
            }
        )
        def requests = blockingServer.expectConcurrentAndBlock(
            expectedArtifactParallelism,
            dependencies.collect {
                blockingServer.get(it.artifact.path).sendFile(it.artifact.file)
            }
        )

        expect:
        executer.withArguments('--max-workers', Integer.toString(maxWorkers))
        def build = executer.withTasks("resolve").start()

        for (int i = 0; i < divideExact(dependencies.size(), expectedMetadataParallelism); i++) {
            metadataRequests.waitForAllPendingCalls()
            metadataRequests.release(expectedMetadataParallelism)
        }

        int artifactIterations = divideExact(dependencies.size(), expectedArtifactParallelism)
        for (int i = 0; i < artifactIterations; i++) {
            requests.waitForAllPendingCalls()
            requests.release(expectedArtifactParallelism)
        }

        build.waitForFinish()
    }

    private static int divideExact(int x, int y) {
        if (x % y != 0) {
            throw new ArithmeticException("Division is not exact; remainder is " + (x % y))
        }
        return x / y
    }

    def "component metadata rules are executed synchronously"() {
        def m1 = ivyRepo.module('test', 'test1', '1.0').publish()
        def m2 = ivyRepo.module('test', 'test2', '1.0').publish()
        def m3 = ivyRepo.module('test', 'test3', '1.0').publish()
        def m4 = ivyRepo.module('test', 'test4', '1.0').publish()

        buildFile << """
            repositories {
                ivy {
                    url = '$blockingServer.uri'
                    $authConfig
                }
            }

            configurations { compile }
            def lock = new java.util.concurrent.locks.ReentrantLock()

            dependencies {
                compile 'test:test1:1.0'
                compile 'test:test2:1.0'
                compile 'test:test3:1.0'
                compile 'test:test4:1.0'

                components {
                    all { ComponentMetadataDetails details ->
                        if (!lock.tryLock()) {
                            throw new AssertionError("Rule called concurrently")
                        }
                        lock.unlock()
                    }
                    withModule("test:test1") { ComponentMetadataDetails details ->
                        // need to make sure that rules are not executed concurrently
                        // because they can share state (typically... this lock!)
                        if (!lock.tryLock()) {
                            throw new AssertionError("Rule called concurrently")
                        }
                        lock.unlock()
                    }
                }
            }
            task resolve {
                def files = configurations.compile
                inputs.files files
                doLast {
                    println files
                }
            }
"""

        given:
        blockingServer.expectConcurrent(
            blockingServer.get(m1.ivy.path).sendFile(m1.ivy.file),
            blockingServer.get(m2.ivy.path).sendFile(m2.ivy.file),
            blockingServer.get(m3.ivy.path).sendFile(m3.ivy.file),
            blockingServer.get(m4.ivy.path).sendFile(m4.ivy.file))
        blockingServer.expectConcurrent(
            blockingServer.get(m1.jar.path).sendFile(m1.jar.file),
            blockingServer.get(m2.jar.path).sendFile(m2.jar.file),
            blockingServer.get(m3.jar.path).sendFile(m3.jar.file),
            blockingServer.get(m4.jar.path).sendFile(m4.jar.file))

        expect:
        executer.withArguments('--max-workers', '4')
        succeeds("resolve")
    }

    @Issue("gradle/gradle#2415")
    def "should not deadlock when downloading parent pom concurrently with a top-level dependency"() {
        given:
        def child1 = mavenRepo.module("org", "child1", "1.0")
        child1.parent("org", "parent1", "1.0")
        child1.publish()

        def child2 = mavenRepo.module("org", "child2", "1.0")
        child2.parent("org", "parent2", "1.0")
        child2.publish()

        def parent1 = mavenRepo.module("org", "parent1", "1.0")
        parent1.hasPackaging('pom')
        parent1.publish()

        def parent2 = mavenRepo.module("org", "parent2", "1.0")
        parent2.hasPackaging('pom')
        parent2.publish()

        buildFile << """
            repositories {
                maven {
                    url = '$blockingServer.uri'
                    $authConfig
                }
            }

            configurations { compile }
            dependencies {
                compile 'org:child1:1.0'
                compile 'org:child2:1.0'
            }

            task resolve {
                def files = configurations.compile
                inputs.files files
                doLast {
                    println files
                }
            }
        """

        when:
        def getChildrenConcurrently = blockingServer.expectConcurrentAndBlock(
            blockingServer.get(child1.pom.path).sendFile(child1.pom.file),
            blockingServer.get(child2.pom.path).sendFile(child2.pom.file),
        )

        def getParentsConcurrently = blockingServer.expectConcurrentAndBlock(
            blockingServer.get(parent1.pom.path).sendFile(parent1.pom.file),
            blockingServer.get(parent2.pom.path).sendFile(parent2.pom.file),
        )

        def jars = blockingServer.expectConcurrentAndBlock(
            blockingServer.get(child1.artifact.path).sendFile(child1.artifact.file),
            blockingServer.get(child2.artifact.path).sendFile(child2.artifact.file),
        )

        then:
        executer.withArguments('--max-workers', '4')
        def build = executer.withTasks("resolve").start()

        getChildrenConcurrently.waitForAllPendingCalls()
        getChildrenConcurrently.releaseAll()

        getParentsConcurrently.waitForAllPendingCalls()
        getParentsConcurrently.releaseAll()

        jars.waitForAllPendingCalls()
        jars.release(2)

        build.waitForFinish()
    }

}
