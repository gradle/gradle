/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractSftpDependencyResolutionTest

class IvySftpRepoErrorsIntegrationTest extends AbstractSftpDependencyResolutionTest {
    void "resolve missing dependencies from a SFTP Ivy repository"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
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

        def module = ivySftpRepo.module('org.group.name', 'projectA', '1.2')

        when:
        server.expectInit()
        module.ivy.expectMetadataRetrieve()
        module.jar.expectMetadataRetrieve()

        then:
        fails 'retrieve'
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not find org.group.name:projectA:1.2')
    }

    void "resolve missing dynamic dependencies from a SFTP Ivy repository"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.+' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        server.expectInit()
        server.expectOpendir('/repo/org.group.name/projectA/')

        then:
        fails 'retrieve'
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not find any version that matches org.group.name:projectA:1.+')
    }

    void "resolve dependencies from a SFTP Ivy repository with invalid credentials"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'bad'
                        password 'credentials'
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
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not resolve org.group.name:projectA:1.2')
                .assertHasCause("Invalid credentials for SFTP server at ${ivySftpRepo.serverUri}")
    }

    void "resolve dependencies from an unreachable SFTP Ivy repository"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
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
        server.stop()

        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not resolve org.group.name:projectA:1.2')
                .assertHasCause("Could not connect to SFTP server at ${ivySftpRepo.serverUri}")
    }

    void 'resolve dependencies from a SFTP Ivy that returns a failure'() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
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
        ivySftpRepo.module('org.group.name', 'projectA', '1.2').ivy.expectMetadataRetrieveFailure()

        and:
        failure = executer.withStackTraceChecksDisabled().withTasks('retrieve').runWithFailure()

        then:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not resolve org.group.name:projectA:1.2')
                .assertHasCause("Could not get resource 'sftp://$ivySftpRepo.uri.host:$ivySftpRepo.uri.port/repo/org.group.name/projectA/1.2/ivy-1.2.xml'")
    }
}
