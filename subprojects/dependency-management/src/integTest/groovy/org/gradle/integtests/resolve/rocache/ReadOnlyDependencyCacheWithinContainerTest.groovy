/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.rocache

import org.gradle.containers.GradleInContainer
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.IgnoreIf

@Requires(UnitTestPreconditions.HasDocker)
@IgnoreIf({ GradleContextualExecuter.embedded }) // needs real Gradle distribution to run in container
class ReadOnlyDependencyCacheWithinContainerTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    BlockingHttpServer synchronizer
    MavenHttpModule core
    MavenHttpModule utils

    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        core = repo.module("org.readonly", "core", "1.0")
        utils = repo.module("org.readonly", "util", "1.0")
        core.dependsOn(utils)
        [core, utils]
    }

    @Override
    def setup() {
        synchronizer = new BlockingHttpServer()
        synchronizer.hostAlias = "host.testcontainers.internal"
        synchronizer.start()
        GradleInContainer.exposeHostPort(synchronizer.port)
    }

    @Override
    def cleanup() {
        synchronizer.stop()
    }

    @Override
    protected void checkIncubationMessage() {
        // not checked because running in containers
    }

    def "can use a read-only cache within a container"() {
        given:
        exposeServerToContainers()
        def container = newContainer()
        def testBuildDir = createContainerBuild()

        when:
        synchronizer.expect("build")
        // The HEAD requests are because the URL of the repository is different
        // when we build it for R/O cache and when we use it in the build in a
        // container
        expectRefresh(core)
        expectRefresh(utils)

        def result = container
            .withBuildDir(testBuildDir)
            .succeeds("resolve")

        then:
        result.with {
            assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/core/1.0/")
            assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/util/1.0/")
        }

        cleanup:
        container.stopContainer()
    }

    def "can use a read-only cache within multiple containers concurrently (daemon in container=#daemon)"() {
        given:
        def ids = (0..3)
        exposeServerToContainers()
        def containers = ids.collect { id ->
            // The HEAD requests are because the URL of the repository is different
            // when we build it for R/O cache and when we use it in the build in a
            // container
            expectRefresh(core)
            expectRefresh(utils)
            def testBuildDir = createContainerBuild("build$id")
            newContainer()
                .withBuildDir(testBuildDir)
                .withExecuter {
                    if (daemon) {
                        requireDaemon()
                        requireIsolatedDaemons()
                    }
                    it
                }
        }

        synchronizer.expectConcurrent(ids.collect { "build$it".toString() })

        when:
        def results = Collections.synchronizedCollection([])
        containers.collect { container ->
            Thread.start {
                results << container.succeeds("resolve")
            }
        }*.join()

        then:
        results.each { result ->
            result.with {
                assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/core/1.0/")
                assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/util/1.0/")
            }
        }

        when:
        synchronizer.expectConcurrent(ids.collect { "build$it".toString() })
        results = Collections.synchronizedCollection([])
        containers.collect { container ->
            Thread.start {
                results << container.succeeds("resolve")
            }
        }*.join()

        then: "for next resolve, HEAD requests are redundant"
        results.each { result ->
            result.with {
                assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/core/1.0/")
                assertOutputContains("/gradle-home/caches/modules-2/files-2.1/org.readonly/util/1.0/")
            }
        }

        cleanup:
        containers*.stopContainer()

        where:
        daemon << [false, true]
    }

    private static void expectRefresh(MavenHttpModule module) {
        module.pom.expectHead()
        module.pom.sha1.expectGet()
        module.moduleMetadata.expectHead()
        module.moduleMetadata.sha1.expectGet()
        module.artifact.expectHead()
        module.artifact.sha1.expectGet()
    }

    private void exposeServerToContainers() {
        GradleInContainer.exposeHostPort(server.port)
    }

    private TestFile createContainerBuild(String id = 'build') {
        def testBuildDir = temporaryFolder.createDir("test-build-$id")
        testBuildDir.file("settings.gradle") << """
            rootProject.name = "test-build-in-container"
        """
        testBuildDir.file("build.gradle") << """
            plugins {
                id 'java-library'
            }

            repositories {
                maven {
                   allowInsecureProtocol = true
                   url "http://host.testcontainers.internal:${server.port}/repo"
                }
            }

            dependencies {
                implementation "org.readonly:core:1.0"
            }

            tasks.register("resolve") {
                doLast {
                    ${synchronizer.callFromTaskAction(id)}
                    configurations.compileClasspath.files.forEach {
                        println it
                    }
                }
            }
        """
        testBuildDir
    }

    GradleInContainer newContainer() {
        new GradleInContainer(distribution, testDirectoryProvider)
            .bindReadOnly(roCacheDir, "/dependency-cache")
            .withExecuter {
                withReadOnlyCacheDir("/dependency-cache")
            }
    }

}
