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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.server.sftp.MavenSftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.test.fixtures.server.sftp.SftpArtifact
import org.junit.Rule

class MavenPublishSftpIntegrationTest extends AbstractMavenPublishIntegTest {
    @Rule
    final SFTPServer server = new SFTPServer(temporaryFolder)

    MavenSftpRepository getMavenSftpRepo() {
        new MavenSftpRepository(server, '/repo')
    }

    def setup() {
        // SFTP test fixture does not handle parallel resolution requests
        executer.beforeExecute {
            it.withArgument("--max-workers=1")
        }
    }

    def "can publish to a SFTP repository"() {
        given:
        def mavenSftpRepo = getMavenSftpRepo()
        def module = mavenSftpRepo.module('org.group.name', 'publish', '2').withModuleMetadata()

        configureRepositoryCredentials("sftp", "sftp", "maven")
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    maven {
                        url "${mavenSftpRepo.uri}"
                        credentials(PasswordCredentials)
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        expectPublish(module.artifact, true)
        expectPublish(module.pom)
        expectPublish(module.moduleMetadata)

        module.rootMetaData.expectLstatMissing()
        expectPublish(module.rootMetaData)

        and:
        succeeds 'publish'

        then:
        module.backingModule.assertPublishedAsJavaModule()
        module.parsedPom.scopes.isEmpty()
    }

    private static void expectPublish(SftpArtifact pom, boolean parentMkdir = false) {
        if (parentMkdir) {
            pom.expectParentMkdir()
        } else {
            pom.expectParentCheckdir()
        }
        pom.expectFileUpload()
        pom.sha1.expectParentCheckdir()
        pom.sha1.expectFileUpload()
        pom.sha256.expectParentCheckdir()
        pom.sha256.expectFileUpload()
        pom.sha512.expectParentCheckdir()
        pom.sha512.expectFileUpload()
        pom.md5.expectParentCheckdir()
        pom.md5.expectFileUpload()
    }
}
