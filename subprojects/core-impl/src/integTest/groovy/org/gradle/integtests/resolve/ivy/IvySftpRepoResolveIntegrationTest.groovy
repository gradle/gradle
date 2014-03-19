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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ivy.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule
import spock.lang.Unroll

@Unroll
class IvySftpRepoResolveIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final SFTPServer server = new SFTPServer(this)

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    IvySftpRepository getIvySftpRepo(boolean m2Compatible, String dirPattern = null) {
        new IvySftpRepository(server, '/repo', m2Compatible, dirPattern)
    }

    void "can resolve dependencies from a SFTP Ivy repository with #layout layout"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible)
        ivySftpRepo.module('org.group.name', 'projectA', '1.2').publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
                    }
                    layout '$layout'
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

        where:
        layout   | m2Compatible
        'gradle' | false
        'maven'  | true
    }

    void "can resolve dependencies from a SFTP Ivy repository with pattern layout and m2compatible: #m2Compatible"() {
        given:

        def ivySftpRepo = getIvySftpRepo(m2Compatible, "[module]/[organisation]/[revision]")
        ivySftpRepo.module('org.group.name', 'projectA', '1.2').publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo.uri}"
                    credentials {
                        username 'sftp'
                        password 'sftp'
                    }
                    layout "pattern", {
                        artifact "${ivySftpRepo.baseArtifactPattern}"
                        ivy "${ivySftpRepo.baseIvyPattern}"
                        m2compatible = $m2Compatible
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

        where:
        m2Compatible << [false, true]
    }

    void "can resolve dependencies from a SFTP Ivy repository with multiple patterns configured"() {
        given:
        def thirdPartyIvySftpRepo = getIvySftpRepo(false, "third-party/[organisation]/[module]/[revision]")
        thirdPartyIvySftpRepo.module('other', '3rdParty', '1.2').publish()

        and:
        def companyIvySftpRepo = getIvySftpRepo(false, "company/[module]/[revision]")
        companyIvySftpRepo.module('company', 'original', '1.1').publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    credentials {
                        username 'sftp'
                        password 'sftp'
                    }
                    ivyPattern "${thirdPartyIvySftpRepo.ivyPattern}"
                    ivyPattern "${companyIvySftpRepo.ivyPattern}"
                    artifactPattern "${thirdPartyIvySftpRepo.artifactPattern}"
                    artifactPattern "${companyIvySftpRepo.artifactPattern}"
                }
            }
            configurations { compile }
            dependencies {
                compile 'other:3rdParty:1.2', 'company:original:1.1'
            }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        succeeds 'retrieve'

        then:
        file('libs').assertHasDescendants '3rdParty-1.2.jar', 'original-1.1.jar'
    }
}

