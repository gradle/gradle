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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

@Requires(TestPrecondition.JDK6_OR_LATER)
@Unroll
class IvySftpRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule
    final SFTPServer server = new SFTPServer(this)

    IvySftpRepository getIvySftpRepo(boolean m2Compatible, String dirPattern = null) {
        new IvySftpRepository(server, '/repo', m2Compatible, dirPattern)
    }

    IvySftpRepository getIvySftpRepo(String contextPath) {
        new IvySftpRepository(server, contextPath, false, null)
    }

    void "can resolve dependencies from a SFTP Ivy repository with #layout layout"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible)
        def module = ivySftpRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

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
        server.expectInit()
        module.ivy.expectMetadataRetrieve()
        module.ivy.expectFileDownload()

        module.jar.expectMetadataRetrieve()
        module.jar.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'

        where:
        layout   | m2Compatible
        'gradle' | false
        'maven'  | true
    }

    void "can resolve dependencies from a SFTP Ivy repository with pattern layout and m2compatible: #m2Compatible"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible, "[module]/[organisation]/[revision]")
        def module = ivySftpRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

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
        server.expectInit()
        module.ivy.expectMetadataRetrieve()
        module.ivy.expectFileDownload()

        module.jar.expectMetadataRetrieve()
        module.jar.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'

        where:
        m2Compatible << [false, true]
    }

    void "can resolve dependencies from a SFTP Ivy repository with multiple patterns configured"() {
        given:
        def thirdPartyIvySftpRepo = getIvySftpRepo(false, "third-party/[organisation]/[module]/[revision]")
        def thirdPartyModule = thirdPartyIvySftpRepo.module('other', '3rdParty', '1.2')
        thirdPartyModule.publish()

        and:
        def companyIvySftpRepo = getIvySftpRepo(false, "company/[module]/[revision]")
        def companyModule = companyIvySftpRepo.module('company', 'original', '1.1')
        companyModule.publish()


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
        server.expectInit()
        thirdPartyModule.ivy.expectMetadataRetrieve()
        thirdPartyModule.ivy.expectFileDownload()

        thirdPartyModule.jar.expectMetadataRetrieve()
        thirdPartyModule.jar.expectFileDownload()

        server.expectMetadataRetrieve('/repo/third-party/company/original/1.1/ivy-1.1.xml')

        companyModule.ivy.expectMetadataRetrieve()
        companyModule.ivy.expectFileDownload()

        server.expectMetadataRetrieve('/repo/third-party/company/original/1.1/original-1.1.jar')

        companyModule.jar.expectMetadataRetrieve()
        companyModule.jar.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants '3rdParty-1.2.jar', 'original-1.1.jar'
    }

    void "can resolve dependencies from multiple SFTP Ivy repositories"() {
        given:
        def ivySftpRepo1 = getIvySftpRepo('/repo1')
        def ivySftpRepo2 = getIvySftpRepo('/repo2')
        def repo1Module = ivySftpRepo1.module('org.group.name', 'projectA', '1.2')
        repo1Module.publish()
        def repo2Module = ivySftpRepo2.module('org.group.name', 'projectB', '1.3')
        repo2Module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${ivySftpRepo1.uri}"
                    credentials {
                        username 'sftp1'
                        password 'sftp1'
                    }
                }

                ivy {
                    url "${ivySftpRepo2.uri}"
                    credentials {
                        username 'sftp2'
                        password 'sftp2'
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2', 'org.group.name:projectB:1.3' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        server.expectInit()
        repo1Module.ivy.expectMetadataRetrieve()
        repo1Module.ivy.expectFileDownload()
        repo1Module.jar.expectMetadataRetrieve()
        repo1Module.jar.expectFileDownload()

        server.expectInit()
        server.expectMetadataRetrieve('/repo1/org.group.name/projectB/1.3/ivy-1.3.xml')
        repo2Module.ivy.expectMetadataRetrieve()
        repo2Module.ivy.expectFileDownload()
        server.expectMetadataRetrieve('/repo1/org.group.name/projectB/1.3/projectB-1.3.jar')
        repo2Module.jar.expectMetadataRetrieve()
        repo2Module.jar.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar', 'projectB-1.3.jar'
    }
}

