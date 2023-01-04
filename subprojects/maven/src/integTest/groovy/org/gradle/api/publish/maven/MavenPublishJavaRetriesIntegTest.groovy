/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

class MavenPublishJavaRetriesIntegTest extends AbstractMavenPublishIntegTest {

    TestKeyStore keyStore

    @Rule public final HttpServer server = new HttpServer()

    MavenHttpRepository mavenRemoteRepo
    MavenHttpModule module

    def setup() {
        keyStore = TestKeyStore.init(file("keystore"))
        server.start()

        mavenRemoteRepo = new MavenHttpRepository(server, "/repo", mavenRepo)
        module = mavenRemoteRepo.module("org.gradle.test", "testMavenRetries", "1.9")
    }

    def cleanup() {
        server.stop()
    }

    def "should publish with intermittent network issues"() {
        given:
        keyStore.enableSslWithServerCert(server)
        settingsFile.text = "rootProject.name = 'testMavenRetries'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:1.+"
            }

            publishing {
                repositories {
                    maven {
                        url '${mavenRemoteRepo.uri}'
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        versionMapping {
                            allVariants {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
        """

        when:
        keyStore.configureServerCert(executer)
        expectPublication()

        ExecutionResult result = run "publish", '--info'

        then:
        verifyPublications()
        outputContains("Waiting 1000ms before next retry, 2 retries left")
        outputContains("Waiting 2000ms before next retry, 1 retries left")
        outputContains("after 2 retries")
    }


    def expectPublication() {
        server.expect("/repo/org/gradle/test/testMavenRetries/1.9/testMavenRetries-1.9.jar", ['PUT'], new HttpServer.ServiceUnavailableAction("intermittent network issue"))
        server.expect("/repo/org/gradle/test/testMavenRetries/1.9/testMavenRetries-1.9.jar", ['PUT'], new HttpServer.ServiceUnavailableAction("intermittent network issue"))
        module.artifact.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.sha256.expectPut()
        module.artifact.sha512.expectPut()
        module.artifact.md5.expectPut()
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPublish()
        module.pom.expectPublish()
        module.moduleMetadata.expectPublish()
    }

    def verifyPublications() {
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/testMavenRetries-1.9.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        assert module.rootMetaData.versions == ["1.9"]
        true
    }

}
