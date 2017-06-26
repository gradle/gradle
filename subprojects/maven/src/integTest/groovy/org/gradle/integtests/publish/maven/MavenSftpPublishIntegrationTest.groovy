/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.publish.maven
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.server.sftp.MavenSftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule
import spock.lang.Ignore

class MavenSftpPublishIntegrationTest extends AbstractMavenPublishIntegTest {
    @Rule
    final SFTPServer server = new SFTPServer(this)

    MavenSftpRepository getMavenSftpRepo() {
        new MavenSftpRepository(server, '/repo')
    }

    def setup() {
        // SFTP test fixture does not handle parallel resolution requests
        executer.beforeExecute {
            it.withArgument("--max-workers=1")
        }
    }

    @Ignore
    def "can publish to a SFTP repository"() {
        given:
        def mavenSftpRepo = getMavenSftpRepo()
        def module = mavenSftpRepo.module('org.group.name', 'publish', '2')

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven'
            version = '1.0'
            group = 'org.group.name'
            repositories {
                mavenCentral()
            }
            configurations {
                deployerJars
            }
            dependencies {
                deployerJars "org.apache.maven.wagon:wagon-ssh:2.2"
            }

            uploadArchives {
                repositories.mavenDeployer {
                    addProtocolProviderJars configurations.deployerJars.files
                    repository(url: '${mavenSftpRepo.uri}') {
                        authentication(userName: 'sftp', password: 'sftp')
                    }
                }
            }
        """

        when:
        module.artifact.expectParentMkdir()
        module.artifact.expectFileUpload()
        // TODO - should not check on each upload to a particular directory
        module.artifact.sha1.expectParentCheckdir()
        module.artifact.sha1.expectFileUpload()
        module.artifact.md5.expectParentCheckdir()
        module.artifact.md5.expectFileUpload()

        module.rootMavenMetadata.expectLstatMissing()
        module.rootMavenMetadata.expectParentCheckdir()
        module.rootMavenMetadata.expectFileUpload()
        module.rootMavenMetadata.sha1.expectParentCheckdir()
        module.rootMavenMetadata.sha1.expectFileUpload()
        module.rootMavenMetadata.md5.expectParentCheckdir()
        module.rootMavenMetadata.md5.expectFileUpload()
        module.pom.expectParentCheckdir()
        module.pom.expectFileUpload()
        module.pom.sha1.expectParentCheckdir()
        module.pom.sha1.expectFileUpload()
        module.pom.md5.expectParentCheckdir()
        module.pom.md5.expectFileUpload()

        and:
        succeeds 'uploadArchives'

        then:
        module.backingModule.assertPublishedAsJavaModule()
        module.parsedPom.scopes.isEmpty()
    }
}
