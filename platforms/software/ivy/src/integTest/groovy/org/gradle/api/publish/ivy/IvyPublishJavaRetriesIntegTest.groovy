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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.junit.Rule

class IvyPublishJavaRetriesIntegTest extends AbstractIvyPublishIntegTest {

    TestKeyStore keyStore

    @Rule public final HttpServer server = new HttpServer()

    IvyHttpRepository ivyRemoteRepo
    IvyHttpModule module

    def setup() {
        keyStore = TestKeyStore.init(file("keystore"))
        server.start()

        ivyRemoteRepo = new IvyHttpRepository(server, "/repo", ivyRepo)
        module = ivyRemoteRepo.module("org.gradle.test", "testIvyRetries", "1.9")
    }

    def cleanup() {
        server.stop()
    }

    def "should publish with intermittent network issues"() {
        given:
        keyStore.enableSslWithServerCert(server)
        settingsFile.text = "rootProject.name = 'testIvyRetries'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    ivy {
                        url = "${ivyRemoteRepo.uri}"
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
        server.expect("/repo/org.gradle.test/testIvyRetries/1.9/testIvyRetries-1.9.jar", ['PUT'], new HttpServer.ServiceUnavailableAction("intermittent network issue"))
        server.expect("/repo/org.gradle.test/testIvyRetries/1.9/testIvyRetries-1.9.jar", ['PUT'], new HttpServer.ServiceUnavailableAction("intermittent network issue"))
        module.artifact.expectPut()
        module.ivy.expectPut()
        module.ivy.sha1.expectPut()
        module.ivy.sha256.expectPut()
        module.ivy.sha512.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.sha256.expectPut()
        module.artifact.sha512.expectPut()
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        module.moduleMetadata.sha256.expectPut()
        module.moduleMetadata.sha512.expectPut()
    }

    def verifyPublications() {
        def localIvyXml = file("build/publications/ivy/ivy.xml").assertIsFile()
        def localArtifact = file("build/libs/testIvyRetries-1.9.jar").assertIsFile()

        module.ivyFile.assertIsCopyOf(localIvyXml)
        module.ivy.verifyChecksums()
        module.jarFile.assertIsCopyOf(localArtifact)
        module.jar.verifyChecksums()
        true
    }
}
