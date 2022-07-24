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


import org.gradle.integtests.resolve.resource.sftp.AbstractSftpDependencyResolutionTest

class IvySftpRepoDynamicRevisionIntegrationTest extends AbstractSftpDependencyResolutionTest {
    def "uses latest version from version range and latest status"() {
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

            dependencies {
                compile group: "group", name: "projectA", version: "1.+"
                compile group: "group", name: "projectB", version: "latest.integration"
            }

            configurations.all {
                resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
            }

            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when: "Version 1.1 is published"
        def projectA1 = ivySftpRepo.module("group", "projectA", "1.1")
        projectA1.publish()
        ivySftpRepo.module("group", "projectA", "2.0").publish()
        def projectB1 = ivySftpRepo.module("group", "projectB", "1.1")
        projectB1.publish()

        and:
        server.expectStat('/repo/group/projectA/')
        server.expectDirectoryList('/repo/group/projectA/')

        projectA1.ivy.expectMetadataRetrieve()
        projectA1.ivy.expectFileDownload()

        server.expectStat('/repo/group/projectB/')
        server.expectDirectoryList('/repo/group/projectB/')

        projectB1.ivy.expectMetadataRetrieve()
        projectB1.ivy.expectFileDownload()

        projectA1.jar.expectMetadataRetrieve()
        projectA1.jar.expectFileDownload()

        projectB1.jar.expectMetadataRetrieve()
        projectB1.jar.expectFileDownload()

        and:
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "New versions are published"
        server.resetExpectations()
        def projectA2 = ivySftpRepo.module("group", "projectA", "1.2")
        projectA2.publish()
        def projectB2 = ivySftpRepo.module("group", "projectB", "2.2")
        projectB2.publish()

        and:
        server.expectStat('/repo/group/projectA/')
        server.expectDirectoryList('/repo/group/projectA/')

        projectA2.ivy.expectMetadataRetrieve()
        projectA2.ivy.expectFileDownload()

        server.expectStat('/repo/group/projectB/')
        server.expectDirectoryList('/repo/group/projectB/')

        projectB2.ivy.expectMetadataRetrieve()
        projectB2.ivy.expectFileDownload()

        projectA2.jar.expectMetadataRetrieve()
        projectA2.jar.expectFileDownload()

        projectB2.jar.expectMetadataRetrieve()
        projectB2.jar.expectFileDownload()

        and:
        run 'retrieve'

        then: "New versions are used"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-2.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.2.jar').assertIsCopyOf(projectB2.jarFile)
    }
}
