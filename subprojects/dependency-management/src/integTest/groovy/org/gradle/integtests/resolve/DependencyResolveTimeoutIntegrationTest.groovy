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
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.ToBeImplemented
import org.junit.Rule
import org.mortbay.jetty.Handler
import org.mortbay.jetty.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch

class DependencyResolveTimeoutIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private static final String GROUP_ID = 'group'

    @Rule
    RequestTimeoutHttpServer requestTimeoutServer = new RequestTimeoutHttpServer()

    MavenHttpRepository requestTimeoutMavenRepo
    MavenHttpRepository resolvableMavenRepo
    MavenHttpModule moduleInRequestTimeoutRepo
    MavenHttpModule moduleInResolvableRepo

    def setup() {
        requestTimeoutMavenRepo = requestTimeoutMavenHttpServer()
        resolvableMavenRepo = mavenHttpRepo
        resolvableMavenRepo.server.start()
        moduleInRequestTimeoutRepo = requestTimeoutMavenRepo.module(GROUP_ID, 'a', '1.0').publish()
        moduleInResolvableRepo = resolvableMavenRepo.module(GROUP_ID, 'a', '1.0').publish()
    }

    def "fails single buildscript dependency resolution if HTTP connection exceeds timeout"() {
        buildFile << """
            buildscript {
                ${mavenRepository(requestTimeoutMavenRepo)}

                dependencies {
                    classpath '${mavenModuleCoordinates(moduleInRequestTimeoutRepo)}'
                }
            }
        """

        when:
        moduleInRequestTimeoutRepo.pom.expectGet()
        fails('resolve')

        then:
        assertDependencyReadTimeout(moduleInRequestTimeoutRepo)
    }

    def "fails single application dependency resolution if HTTP connection exceeds timeout"() {
        given:
        buildFile << """
            ${mavenRepository(requestTimeoutMavenRepo)}
            ${customConfigDependencyAssignment(moduleInResolvableRepo)}
            ${configSyncTask()}
        """

        when:
        moduleInRequestTimeoutRepo.pom.expectGet()
        fails('resolve')

        then:
        assertDependencyReadTimeout(moduleInRequestTimeoutRepo)
        !file('libs').isDirectory()
    }

    def "fails concurrent application dependency resolution if HTTP connection exceeds timeout"() {
        MavenHttpModule moduleB = requestTimeoutMavenRepo.module(GROUP_ID, 'b', '2.0').publish()
        MavenHttpModule moduleC = requestTimeoutMavenRepo.module(GROUP_ID, 'c', '3.0').publish()

        given:
        buildFile << """
            ${mavenRepository(requestTimeoutMavenRepo)}
            ${customConfigDependencyAssignment(moduleInResolvableRepo, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        moduleInRequestTimeoutRepo.pom.expectGet()
        moduleB.pom.expectGet()
        moduleC.pom.expectGet()
        fails('resolve', '--max-workers=3')

        then:
        assertDependencyReadTimeout(moduleInRequestTimeoutRepo)
        assertDependencyReadTimeout(moduleB)
        assertDependencyReadTimeout(moduleC)
        !file('libs').isDirectory()
    }

    @ToBeImplemented("Should resolve from second repository")
    def "try from next repository if resolution times out"() {
        given:
        buildFile << """
            ${mavenRepository(requestTimeoutMavenRepo)}
            ${mavenRepository(resolvableMavenRepo)}
            ${customConfigDependencyAssignment(moduleInResolvableRepo)}
            ${configSyncTask()}
        """

        when:
        moduleInRequestTimeoutRepo.pom.expectGet()
        moduleInResolvableRepo.pom.expectGet()
        moduleInResolvableRepo.artifact.expectGet()
        fails('resolve')
        //succeeds('resolve')

        then:
        assertDependencyReadTimeout(moduleInRequestTimeoutRepo)
        !file('libs').isDirectory()
        //file('libs').assertHasDescendants('a-1.0.jar')
    }

    MavenHttpRepository requestTimeoutMavenHttpServer() {
        requestTimeoutServer.start()
        new MavenHttpRepository(requestTimeoutServer, '/request-timeout-maven', maven(file('request_timeout_maven_repo')))
    }

    static class RequestTimeoutHttpServer extends HttpServer {
        CountDownLatch latch = new CountDownLatch(1)

        @Override
        Handler getCustomHandler() {
            return new AbstractHandler() {
                @Override
                void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
                    latch.await()
                }
            }
        }

        @Override
        void after() {
            latch.countDown()
            super.after()
        }
    }

    private String mavenRepository(MavenRepository repo) {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }

    private String customConfigDependencyAssignment(MavenHttpModule... modules) {
        """
            configurations {
                deps
            }
            
            dependencies {
                deps ${modules.collect { "'${mavenModuleCoordinates(it)}'" }.join(', ')}
            }
        """
    }

    private String configSyncTask() {
        """
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    private String mavenModuleCoordinates(MavenModule module) {
        "$module.groupId:$module.artifactId:$module.version"
    }

    private void assertDependencyReadTimeout(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactId}.jar (${mavenModuleCoordinates(module)})")
        failure.assertHasCause('Read timed out')
    }
}
