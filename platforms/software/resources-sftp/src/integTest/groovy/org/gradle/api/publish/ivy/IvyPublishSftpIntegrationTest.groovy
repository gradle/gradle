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

package org.gradle.api.publish.ivy

import org.gradle.test.fixtures.server.sftp.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule

class IvyPublishSftpIntegrationTest extends AbstractIvyPublishIntegTest {

    @Rule
    final SFTPServer server = new SFTPServer(temporaryFolder)

    IvySftpRepository getIvySftpRepo(boolean m2Compatible = false, String dirPattern = null) {
        new IvySftpRepository(server, '/repo', m2Compatible, dirPattern)
    }

    def setup() {
        // SFTP test fixture does not handle parallel resolution requests
        executer.beforeExecute {
            it.withArgument("--max-workers=1")
        }
    }

    private void buildAndSettingsFilesForPublishing() {
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials(PasswordCredentials)
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """
    }

    def "can publish to a SFTP repository with layout #layout"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible)
        def module = ivySftpRepo.module("org.group.name", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        configureRepositoryCredentials("sftp", "sftp", "ivy")
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials(PasswordCredentials)
                        layout "$layout"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        module.jar.expectParentMkdir()
        module.jar.expectFileUpload()
        // TODO - should not check on each upload to a particular directory
        module.jar.sha1.expectParentCheckdir()
        module.jar.sha1.expectFileUpload()
        module.jar.sha256.expectParentCheckdir()
        module.jar.sha256.expectFileUpload()
        module.jar.sha512.expectParentCheckdir()
        module.jar.sha512.expectFileUpload()
        module.ivy.expectParentCheckdir()
        module.ivy.expectFileUpload()
        module.ivy.sha1.expectParentCheckdir()
        module.ivy.sha1.expectFileUpload()
        module.ivy.sha256.expectParentCheckdir()
        module.ivy.sha256.expectFileUpload()
        module.ivy.sha512.expectParentCheckdir()
        module.ivy.sha512.expectFileUpload()
        module.moduleMetadata.expectParentCheckdir()
        module.moduleMetadata.expectFileUpload()
        module.moduleMetadata.sha1.expectParentCheckdir()
        module.moduleMetadata.sha1.expectFileUpload()
        module.moduleMetadata.sha256.expectParentCheckdir()
        module.moduleMetadata.sha256.expectFileUpload()
        module.moduleMetadata.sha512.expectParentCheckdir()
        module.moduleMetadata.sha512.expectFileUpload()

        then:
        succeeds 'publish'

        module.assertMetadataAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        where:
        layout   | m2Compatible
        'gradle' | false
        'maven'  | true
    }

    def "can publish to a SFTP repository with pattern layout and m2Compatible #m2Compatible"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible, "[module]/[organisation]/[revision]")
        def module = ivySftpRepo.module("org.group.name", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        configureRepositoryCredentials("sftp", "sftp", "ivy")
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials(PasswordCredentials)
                        patternLayout {
                            artifact "${ivySftpRepo.baseArtifactPattern}"
                            ivy "${ivySftpRepo.baseIvyPattern}"
                            m2compatible = $m2Compatible
                        }
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        module.jar.expectParentMkdir()
        module.jar.expectFileUpload()
        // TODO - should not check on each upload to a particular directory
        module.jar.sha1.expectParentCheckdir()
        module.jar.sha1.expectFileUpload()
        module.jar.sha256.expectParentCheckdir()
        module.jar.sha256.expectFileUpload()
        module.jar.sha512.expectParentCheckdir()
        module.jar.sha512.expectFileUpload()
        module.ivy.expectParentCheckdir()
        module.ivy.expectFileUpload()
        module.ivy.sha1.expectParentCheckdir()
        module.ivy.sha1.expectFileUpload()
        module.ivy.sha256.expectParentCheckdir()
        module.ivy.sha256.expectFileUpload()
        module.ivy.sha512.expectParentCheckdir()
        module.ivy.sha512.expectFileUpload()

        then:
        succeeds 'publish'

        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        where:
        m2Compatible << [true, false]
    }

    def "publishing to a SFTP repo when directory creation fails"() {
        given:
        configureRepositoryCredentials("sftp", "sftp", "ivy")
        buildAndSettingsFilesForPublishing()

        when:
        def directory = '/repo/org.group.name/publish/2'
        directory.tokenize('/').findAll().inject('') { path, token ->
            def currentPath = "$path/$token"
            server.expectLstat(currentPath)
            currentPath
        }
        server.expectMkdirBroken('/repo')

        then:
        fails 'publish'
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
            .assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
            .assertHasCause("Could not create resource '${ivySftpRepo.uri}'.")
    }

    def "publishing to a SFTP repo when file uploading fails"() {
        given:
        configureRepositoryCredentials("sftp", "sftp", "ivy")
        buildAndSettingsFilesForPublishing()
        def module = ivySftpRepo.module('org.group.name', 'publish', '2')

        when:
        module.jar.expectParentMkdir()
        module.jar.expectUploadBroken()

        then:
        fails 'publish'
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
            .assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
            .assertHasCause("Could not write to resource '${module.jar.uri}'.")

        cleanup:
        server.clearSessions()
    }
}
