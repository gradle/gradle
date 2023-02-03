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

package org.gradle.integtests.resolve.resource.sftp.ivy

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.resolve.resource.sftp.AbstractSftpDependencyResolutionTest

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
        module.ivy.expectMetadataRetrieve()

        then:
        fails 'retrieve'
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause("""Could not find org.group.name:projectA:1.2.
Searched in the following locations:
  - ${module.ivy.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'ivy.xml' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
""")
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
        server.expectStat('/repo/org.group.name/projectA/')

        then:
        fails 'retrieve'
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause("""Could not find any matches for org.group.name:projectA:1.+ as no versions of org.group.name:projectA are available.
Searched in the following locations:
  - ${ivySftpRepo.uri}/org.group.name/projectA/
Required by:
""")
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
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause('Could not resolve org.group.name:projectA:1.2')
            .assertHasCause("Password authentication not supported or invalid credentials for SFTP server at ${ivySftpRepo.serverUri}")
    }

    void "resolve dependencies from a SFTP Ivy repository with unsupported password authentication"() {
        given:
        server.withPasswordAuthenticationDisabled()
        and:
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
        fails 'retrieve'

        then:
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause('Could not resolve org.group.name:projectA:1.2')
            .assertHasCause("Password authentication not supported or invalid credentials for SFTP server at ${ivySftpRepo.serverUri}")
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
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
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
        def projectA = ivySftpRepo.module('org.group.name', 'projectA', '1.2')
        projectA.ivy.expectMetadataRetrieveBroken()

        and:
        failure = executer.withTasks('retrieve').runWithFailure()

        then:
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause('Could not resolve org.group.name:projectA:1.2')
            .assertHasCause("Could not get resource '${projectA.ivy.uri}'")
    }

    def "cannot add invalid authentication types for sftp repo"() {
        given:
        def remoteIvyRepo = getIvySftpRepo()
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()
        and:
        buildFile << """
repositories {
    ivy {
        url "${getIvySftpRepo().uri}"
        authentication {
            auth(BasicAuthentication)
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        expect:
        fails 'retrieve'
        and:
        assertTaskFailureDescription(":retrieve")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 'sftp'")
    }

    private void assertTaskFailureDescription(String taskSelector) {
        if (GradleContextualExecuter.configCache) {
            failureDescriptionContains("Configuration cache state could not be cached:")
            failureDescriptionContains(taskSelector)
        } else {
            def description = "Execution failed for task '${taskSelector}'."
            failure.assertHasDescription(description)
        }
    }
}
