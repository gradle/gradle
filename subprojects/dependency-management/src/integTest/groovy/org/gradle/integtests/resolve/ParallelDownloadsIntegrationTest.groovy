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
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class ParallelDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private final Object lock = new Object()
    private volatile int concurrentRequests
    protected volatile int maxConcurrentRequests

    protected Closure<Boolean> acceptURI

    def setup() {
        executer.withArguments('--max-workers', '4')
        // make sure we serve at least 2 requests concurrently
        // and if we don't, the timeout will unblock consumers, and the test should fail
        def barrier = new CyclicBarrier(2)
        server.beforeHandle { HttpServletRequest request ->
            if (acceptURI(request.requestURI)) {
                synchronized (lock) {
                    maxConcurrentRequests = Math.max(maxConcurrentRequests, ++concurrentRequests)
                }
                barrier.await(20, TimeUnit.SECONDS)
            }
        }
        server.afterHandle { HttpServletRequest request ->
            if (acceptURI(request.requestURI)) {
                synchronized (lock) {
                    concurrentRequests--
                }
            }
        }
    }

    @Unroll
    def "downloads artifacts in parallel using #repo"() {
        enableCountOfParallelArtifactDownloads()

        given:
        multipleFilesToDownloadFrom(repo)

        when:
        run 'resolve'

        then:
        noExceptionThrown()
        maxConcurrentRequests > 1

        where:
        repo << ['maven', 'ivy']
    }

    @Unroll
    def "downloads artifacts in parallel with project dependencies using #repo"() {
        enableCountOfParallelArtifactDownloads()

        given:
        multipleFilesToDownloadFrom(repo, 8, 8)

        when:
        run 'resolve'

        then:
        noExceptionThrown()
        maxConcurrentRequests > 1

        where:
        repo << ['maven', 'ivy']
    }

    private void multipleFilesToDownloadFrom(String repo, int externalDependenciesCount = 8, int projectDependenciesCount = 0) {
        if (repo in ['ivy', 'maven']) {
            buildFile << """
            allprojects {
                repositories {"""
            if (repo == 'maven') {
                buildFile << """
                    maven { url '$mavenHttpRepo.uri' }
        """
            } else {
                buildFile << """
                    ivy { url '$ivyHttpRepo.uri' }
        """
            }
            buildFile << """                }
                configurations {
                    compile
                }
            }
            
            dependencies {
        """
            externalDependenciesCount.times {
                if (repo == 'maven') {
                    def module = mavenHttpRepo.module("test", "test$it", "1.0").publish()
                    module.pom.expectGet()
                    module.artifact.expectGet()
                } else if (repo == 'ivy') {
                    def module = ivyHttpRepo.module("test", "test$it", "1.0").publish()
                    module.ivy.expectGet()
                    module.artifact.expectGet()
                }
                buildFile << "               compile 'test:test$it:1.0'\n"
            }
            projectDependenciesCount.times {
                settingsFile << "include 'subproject$it'\n"
                buildFile << "               compile project('subproject$it')\n"
                file("subproject$it/build.gradle") << """
                    apply plugin: 'java-library'
                """
                file("subproject$it/src/main/java/com/acme/Foo${it}.java") << """
                    package com.acme;
                    public class Foo${it} {}
                """
            }
            buildFile << """
            }
            
            task resolve {
                dependsOn configurations.compile // force execution of jar tasks for local projects
                doLast {
                    println configurations.compile.files
                }
            }
"""
        } else {
            throw new IllegalArgumentException("Repository must be one of 'ivy', 'maven'")
        }
    }

    private void enableCountOfParallelArtifactDownloads() {
        acceptURI = { String uri ->
            uri.endsWith('.jar')
        }
    }
}
