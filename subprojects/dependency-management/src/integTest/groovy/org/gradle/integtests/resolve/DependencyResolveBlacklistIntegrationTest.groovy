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
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import org.mortbay.jetty.Handler
import org.mortbay.jetty.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch

class DependencyResolveBlacklistIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Rule
    RequestTimeoutHttpServer server = new RequestTimeoutHttpServer()

    def repo
    def module

    def setup() {
        repo = mavenHttpServer()
        module = repo.module('group', 'a', '1.0').publish()
    }

    def "fails buildscript dependency resolution if HTTP connection exceeds timeout"() {
        buildFile << """
            buildscript {
                ${mavenRepository()}

                dependencies {
                    classpath "$module.groupId:$module.artifactId:$module.version"
                }
            }
        """

        when:
        module.pom.expectGet()
        fails('resolve')

        then:
        failure.assertHasCause('Could not download a.jar (group:a:1.0)')
        failure.assertHasCause('Read timed out')
    }

    def "fails regular dependency resolution if HTTP connection exceeds timeout"() {
        given:
        buildFile << """
            ${mavenRepository()}
            
            configurations {
                deps
            }
            
            dependencies {
                deps "$module.groupId:$module.artifactId:$module.version"
            }
            
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/deps"
            }
        """

        when:
        module.pom.expectGet()
        fails('resolve')

        then:
        failure.assertHasCause('Could not download a.jar (group:a:1.0)')
        failure.assertHasCause('Read timed out')
    }

    MavenHttpRepository mavenHttpServer() {
        server.start()
        new MavenHttpRepository(server, '/maven', maven(file('maven_repo')))
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

    private String mavenRepository() {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }
}
