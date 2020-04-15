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

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

abstract class AbstractRedirectResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule HttpServer backingServer = new HttpServer()

    abstract String getFrontServerBaseUrl();
    /**
     * The goal is to deprecate the download of artifacts over HTTP as it is a security vulnerability.
     */
    abstract boolean shouldWarnAboutDeprecation();

    def module = ivyRepo().module('group', 'projectA').publish()

    abstract void beforeServerStart();

    def setupServer() {
        beforeServerStart()
        server.useHostname()
        backingServer.useHostname()
        backingServer.start()
    }

    @Override
    def setup() {
        setupServer()
        executer.withWarningMode(WarningMode.All)
    }

    void optionallyExpectDeprecation() {
        if (shouldWarnAboutDeprecation()) {
            outputContains("Following insecure redirects has been deprecated. This is scheduled to be removed in Gradle 7.0.")
            outputContains("Switch "); outputContains(" repository ")
            outputContains(" to redirect to a secure protocol (like HTTPS) or allow insecure protocols.")
        }
    }

    def "resolves module artifacts via HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.jar', "${backingServer.uri}/redirected/group/projectA/1.0/projectA-1.0.jar")
        backingServer.expectGet('/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        if (shouldWarnAboutDeprecation()) {
            int frontServerDeprecationCount = server.uri.scheme == "http" ? 1 : 0
            int backServerDeprecationCount = backingServer.uri.scheme == "http" ? 2 : 0
            executer.expectDeprecationWarnings(frontServerDeprecationCount + backServerDeprecationCount)
        }
        succeeds('listJars')

        and:
        optionallyExpectDeprecation()
    }

    def "prints last redirect location in case of failure"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGetBroken('/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        if (shouldWarnAboutDeprecation()) {
            executer.expectDeprecationWarnings(insecureServerCount())
        }
        fails('listJars')

        and:
        optionallyExpectDeprecation()
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
        if (shouldWarnAboutDeprecation()) {
            executer.expectDeprecationWarnings(insecureServerCount())
        }
        fails('listJars')

        and:
        optionallyExpectDeprecation()
        failureCauseContains("Could not get resource '${server.uri}/repo/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Could not GET '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Read timed out")
    }

    def configurationWithIvyDependencyAndExpectedArtifact(String dependency, String expectedArtifact) {
        """
            repositories {
                ivy {
                    url "$frontServerBaseUrl/repo"
                    metadataSources {
                        ivyDescriptor()
                        artifact()
                    }
                }
            }
            configurations { compile }
            dependencies { compile '$dependency' }
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['$expectedArtifact']
                }
            }
        """
    }

    /**
     * The number of servers involved in the redirect chain using an insecure protocol.
     */
    private int insecureServerCount() {
        [backingServer.uri.scheme == "http", server.uri.scheme == "http"].count { it }.intValue()
    }

}
