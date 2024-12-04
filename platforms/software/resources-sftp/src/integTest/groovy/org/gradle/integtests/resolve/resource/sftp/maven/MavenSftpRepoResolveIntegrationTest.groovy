/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.resource.sftp.maven

import org.gradle.integtests.resolve.resource.sftp.AbstractSftpDependencyResolutionTest

class MavenSftpRepoResolveIntegrationTest extends AbstractSftpDependencyResolutionTest {

    void "can resolve dependencies from a SFTP Maven repository"() {
        given:
        def mavenSftpRepo = getMavenSftpRepo()
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                maven {
                    url = "${mavenSftpRepo.uri}"
                    credentials {
                        username = 'sftp'
                        password = 'sftp'
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
        module.pom.expectMetadataRetrieve()
        module.pom.expectFileDownload()

        module.artifact.expectMetadataRetrieve()
        module.artifact.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'
    }

    def "cannot add invalid authentication types for sftp repo"() {
        given:
        def mavenSftpRepo = getMavenSftpRepo()
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()
        and:
        buildFile << """
repositories {
    maven {
        url = "${mavenSftpRepo.uri}"
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
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 'sftp'")
    }
}
