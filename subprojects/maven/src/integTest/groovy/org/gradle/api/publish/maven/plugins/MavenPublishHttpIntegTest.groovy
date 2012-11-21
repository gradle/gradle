/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.MavenHttpModule
import org.gradle.integtests.fixtures.MavenHttpRepository
import org.gradle.integtests.fixtures.ProgressLoggingFixture
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

class MavenPublishHttpIntegTest extends AbstractIntegrationSpec {

    @Rule HttpServer server
    @Rule ProgressLoggingFixture progressLogging

    MavenHttpRepository mavenRemoteRepo
    MavenHttpModule module

    def repoPath = "/repo"
    String group
    String name
    String version

    def setup() {
        server.start()

        mavenRemoteRepo = new MavenHttpRepository(server, repoPath, mavenRepo)
        group = "org.gradle"
        name = "publish"
        version = "2"
        module = mavenRemoteRepo.module(group, name, version)

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '$version'
            group = '$group'

            publishing {
                repositories {
                    maven {
                        url "http://localhost:${server.port}${repoPath}"
                    }
                }
            }
        """
    }

    def "can publish to an unauthenticated http repo"() {
        given:
        module.expectArtifactPut()
        module.expectArtifactSha1Put()
        module.expectArtifactMd5Put()
        module.expectRootMetaDataGetMissing()
        module.expectRootMetaDataPut()
        module.expectRootMetaDataSha1Put()
        module.expectRootMetaDataMd5Put()
        module.expectPomPut()
        module.expectPomSha1Put()
        module.expectPomMd5Put()

        when:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.verifyPomChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.verifyArtifactChecksums()

        module.verifyRootMetaDataChecksums()
        module.rootMetaData.versions == ["2"]
    }

}
