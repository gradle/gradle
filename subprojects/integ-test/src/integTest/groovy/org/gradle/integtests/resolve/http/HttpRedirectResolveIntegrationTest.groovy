/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve.http

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.server.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue

class HttpRedirectResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule public final HttpServer server2 = new HttpServer()

    public void "resolves module artifacts via HTTP redirect"() {
        server.start()
        server2.start()

        given:
        def module = ivyRepo().module('group', 'projectA').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.0' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "http://localhost:${server2.port}/redirected/group/projectA/1.0/ivy-1.0.xml")
        server2.expectGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.jar', "http://localhost:${server2.port}/redirected/group/projectA/1.0/projectA-1.0.jar")
        server2.expectGet('/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        succeeds('listJars')
    }

    @Issue('GRADLE-2196')
    public void "resolves artifact-only module via HTTP redirect"() {
        server.start()
        server2.start()

        given:
        def module = ivyRepo().module('group', 'projectA').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.0@zip' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.zip']
}
"""

        when:
        server.expectGetMissing('/repo/group/projectA/1.0/ivy-1.0.xml')
        server.expectHeadRedirected('/repo/group/projectA/1.0/projectA-1.0.zip', "http://localhost:${server2.port}/redirected/group/projectA/1.0/projectA-1.0.zip")
        server2.expectHead('/redirected/group/projectA/1.0/projectA-1.0.zip', module.jarFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.zip', "http://localhost:${server2.port}/redirected/group/projectA/1.0/projectA-1.0.zip")
        server2.expectGet('/redirected/group/projectA/1.0/projectA-1.0.zip', module.jarFile)

        then:
        succeeds('listJars')
    }
}
