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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.MavenSftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK6_OR_LATER)
class MavenSftpRepoResolveIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final SFTPServer server = new SFTPServer(this)

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    MavenSftpRepository getMavenSftpRepo() {
        new MavenSftpRepository(server, '/repo')
    }

    void "can resolve dependencies from a SFTP Maven repository"() {
        given:
        def mavenSftpRepo = getMavenSftpRepo()
        mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()

        and:
        buildFile << """
            repositories {
                maven {
                    url "${mavenSftpRepo.uri}"
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
        succeeds 'retrieve'

        then:
        file('libs').assertHasDescendants 'projectA-1.2.jar'
    }
}
