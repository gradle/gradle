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

import org.gradle.test.fixtures.server.http.HttpResourceHandler
import org.gradle.test.fixtures.server.http.HttpServer

import java.util.concurrent.CopyOnWriteArrayList

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

abstract class AbstractRedirectResolveIntegrationTest extends AbstractRedirectResolveBaseIntegrationTest {

    private static final String VERSION = '1.0-dev01+abc'
    private static final String ESCAPED = '1.0-dev01%2Babc'

    def "resolves module artifacts via HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.jar', "${backingServer.uri}/redirected/group/projectA/1.0/projectA-1.0.jar")
        backingServer.expectGet('/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        succeeds('listJars')
    }

    def "prints last redirect location in case of failure"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGetBroken('/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        fails('listJars')

        and:
        failureCauseContains("Could not get resource '${server.uri}/repo/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Could not GET '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'")
    }

    def "prints last redirect location in case of timeout"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGetBlocking('/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        executer.beforeExecute { withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000") }
        fails('listJars')

        and:
        failureCauseContains("Could not get resource '${server.uri}/repo/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Could not GET '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Read timed out")
    }

    def "keeps the escaping of an absolute redirect location while resolving"() {
        given:
        def published = publishVersionWithAPlus()
        def requested = recordRawPathsOf(backingServer)

        when:
        server.expectGetRedirected(
            "/repo/group/projectA/${VERSION}/ivy-${VERSION}.xml",
            "${backingServer.uri}/redirected/group/projectA/${ESCAPED}/ivy-${ESCAPED}.xml"
        )
        backingServer.expectGet(
            "/redirected/group/projectA/${VERSION}/ivy-${VERSION}.xml", published.ivyFile
        )
        server.expectGetRedirected(
            "/repo/group/projectA/${VERSION}/projectA-${VERSION}.jar",
            "${backingServer.uri}/redirected/group/projectA/${ESCAPED}/projectA-${ESCAPED}.jar"
        )
        backingServer.expectGet(
            "/redirected/group/projectA/${VERSION}/projectA-${VERSION}.jar", published.jarFile
        )

        then:
        succeeds('listJars')
        requested == [
            "/redirected/group/projectA/${ESCAPED}/ivy-${ESCAPED}.xml".toString(),
            "/redirected/group/projectA/${ESCAPED}/projectA-${ESCAPED}.jar".toString()
        ]
    }

    def "keeps the escaping of a relative redirect location while resolving"() {
        given:
        def published = publishVersionWithAPlus()
        def requested = recordRawPathsOf(server)

        when:
        server.expectGetRedirected(
            "/repo/group/projectA/${VERSION}/ivy-${VERSION}.xml",
            "/relocated/group/projectA/${ESCAPED}/ivy-${ESCAPED}.xml"
        )
        server.expectGet(
            "/relocated/group/projectA/${VERSION}/ivy-${VERSION}.xml", published.ivyFile
        )
        server.expectGetRedirected(
            "/repo/group/projectA/${VERSION}/projectA-${VERSION}.jar",
            "/relocated/group/projectA/${ESCAPED}/projectA-${ESCAPED}.jar"
        )
        server.expectGet(
            "/relocated/group/projectA/${VERSION}/projectA-${VERSION}.jar", published.jarFile
        )

        then:
        succeeds('listJars')
        // This server also saw the request it redirected, which carries a literal '+'.
        requested.containsAll([
            "/relocated/group/projectA/${ESCAPED}/ivy-${ESCAPED}.xml".toString(),
            "/relocated/group/projectA/${ESCAPED}/projectA-${ESCAPED}.jar".toString()
        ])
    }

    private publishVersionWithAPlus() {
        buildFile << configurationWithIvyDependencyAndExpectedArtifact(
            "group:projectA:$VERSION", "projectA-${VERSION}.jar"
        )
        ivyRepo().module('group', 'projectA', VERSION).publish()
    }

    private List<String> recordRawPathsOf(HttpServer target) {
        def paths = new CopyOnWriteArrayList<String>()
        target.addHandler({ t, request, response ->
            paths.add(request.rawPath)
        } as HttpResourceHandler)
        paths
    }
}
