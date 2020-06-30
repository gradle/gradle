/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.ivy

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class IvyHttpRepoResolveIntegrationTest extends AbstractIvyRemoteRepoResolveIntegrationTest {

    @Rule
    final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    @Override
    RepositoryServer getServer() {
        return server
    }

    @ToBeFixedForInstantExecution(because = "broken file collection")
    void "fails when configured with AwsCredentials"() {
        given:
        def remoteIvyRepo = server.remoteIvyRepo
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${remoteIvyRepo.uri}"
                    credentials(AwsCredentials) {
                        accessKey "someKey"
                        secretKey "someSecret"
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:

        fails 'retrieve'
        then:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not resolve org.group.name:projectA:1.2.")
        failure.assertHasCause("Credentials must be an instance of: ${PasswordCredentials.canonicalName}")
    }

    @ToBeFixedForInstantExecution
    public void "can resolve and cache dependencies with missing status and publication date"() {
        given:
        def module = server.remoteIvyRepo.module('group', 'projectA', '1.2')
        module.withXml({
            def infoAttribs = asNode().info[0].attributes()
            infoAttribs.remove("status")
            infoAttribs.remove("publication")
        }).publish()

        println module.ivyFile.text

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${server.remoteIvyRepo.uri}"
                    $server.validCredentials
                }
            }
            configurations { compile }
            dependencies { compile 'group:projectA:1.2' }
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
                }
            }
        """
        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds 'listJars'
        progressLogger.downloadProgressLogged(module.ivy.uri)
        progressLogger.downloadProgressLogged(module.jar.uri)

        when:
        server.resetExpectations()

        then:
        succeeds 'listJars'
    }

    @ToBeFixedForInstantExecution
    void "skip subsequent Ivy repositories on timeout and recovers for later resolution"() {
        given:
        executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000")
        def repo1 = server.getRemoteIvyRepo("/repo1")
        def repo2 = server.getRemoteIvyRepo("/repo2")
        def module1 = repo1.module('group', 'projectA').publish()
        def module2 = repo2.module('group', 'projectA').publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${repo1.uri}"
                    $server.validCredentials
                }
                ivy {
                    url "${repo2.uri}"
                    $server.validCredentials
                }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'group:projectA:1.0'
            }
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
                }
            }
        """

        when:
        // Timeout connecting to repo1: do not continue search to repo2
        module1.ivy.expectGetBlocking()

        then:
        fails'listJars'
        failureHasCause("Could not resolve group:projectA:1.0")
        failureHasCause("Could not GET '$repo1.uri/group/projectA/1.0/ivy-1.0.xml'")
        failureHasCause('Read timed out')

        when:
        server.resetExpectations()
        module1.ivy.expectGetMissing()
        module2.ivy.expectGet()
        module2.jar.expectDownload()

        then:
        succeeds('listJars')
    }

    /**
     * Ivy equivalent of "does not query Maven repository for modules without a group, name or version" in
     * {@link org.gradle.integtests.resolve.maven.MavenHttpRepoResolveIntegrationTest}
     */
    def "does not query Ivy repository for modules without a group, name or version"() {
        given:
        def remoteIvyRepo = server.remoteIvyRepo
        buildFile << """
            repositories {
                ivy {
                    url '${remoteIvyRepo.uri}'
                }
            }
            configurations { compile }
            dependencies {
                compile ':name1:1.0'
                compile ':name1:1.0'
                compile ':name2:[1.0, 2.0]'
                compile ':name3:1.0-SNAPSHOT'
                compile 'group1::1.0'
                compile 'group2::[1.0, 2.0]'
                compile 'group3::1.0-SNAPSHOT'
                compile 'group:name'
            }
            task resolve {
                doLast {
                    assert configurations.compile.resolve()
                }
            }
        """

        when:
        fails 'resolve'

        then:

        failure.assertHasCause('Could not find :name1:1.0.')
        failure.assertHasCause('Could not find any matches for :name2:[1.0, 2.0] as no versions of :name2 are available.')
        failure.assertHasCause('Could not find :name3:1.0-SNAPSHOT.')
        failure.assertHasCause('Could not find group1::1.0.')
        failure.assertHasCause('Could not find any matches for group2::[1.0, 2.0] as no versions of group2: are available.')
        failure.assertHasCause('Could not find group3::1.0-SNAPSHOT.')
        failure.assertHasCause('Could not find group:name:.')
    }
}
