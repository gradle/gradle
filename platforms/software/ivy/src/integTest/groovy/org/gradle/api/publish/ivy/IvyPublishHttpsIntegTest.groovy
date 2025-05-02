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



package org.gradle.api.publish.ivy

import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.junit.Rule

class IvyPublishHttpsIntegTest extends AbstractIvyPublishIntegTest {
    TestKeyStore keyStore

    @Rule public final HttpServer server = new HttpServer()

    IvyHttpRepository ivyRemoteRepo
    IvyHttpModule module

    def setup() {
        keyStore = TestKeyStore.init(file("keystore"))
        server.start()

        ivyRemoteRepo = new IvyHttpRepository(server, "/repo", ivyRepo)
        module = ivyRemoteRepo.module('org.gradle', 'publish', '2').allowAll()
    }

    def "publish with server certificate"() {
        given:
        keyStore.enableSslWithServerCert(server)
        initBuild()

        when:
        expectPublication()
        keyStore.configureServerCert(executer)
        succeeds 'publish'

        then:
        verifyPublications()
    }

    def "publish with server and client certificate"() {
        given:
        keyStore.enableSslWithServerAndClientCerts(server)
        initBuild()

        when:
        expectPublication()
        keyStore.configureServerAndClientCerts(executer)
        succeeds 'publish'

        then:
        verifyPublications()
    }

    def "decent error message when client can't authenticate server"() {
        keyStore.enableSslWithServerCert(server)
        initBuild()

        when:
        keyStore.configureIncorrectServerCert(executer)
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        fails 'publish'

        then:
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Could not write to resource '${module.jar.uri}'")
    }

    def "build fails when server can't authenticate client"() {
        keyStore.enableSslWithServerAndBadClientCert(server)
        initBuild()

        when:
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        keyStore.configureServerAndClientCerts(executer)

        fails 'publish'

        then:
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Could not write to resource '${module.jar.uri}'")
    }

    def expectPublication() {
        module.jar.expectPut()
        module.jar.sha1.expectPut()
        module.jar.sha256.expectPut()
        module.jar.sha512.expectPut()
        module.ivy.expectPut()
        module.ivy.sha1.expectPut()
        module.ivy.sha256.expectPut()
        module.ivy.sha512.expectPut()
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        module.moduleMetadata.sha256.expectPut()
        module.moduleMetadata.sha512.expectPut()
    }

    def verifyPublications() {
        def localIvyXml = file("build/publications/ivy/ivy.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.ivyFile.assertIsCopyOf(localIvyXml)
        module.ivy.verifyChecksums()
        module.jarFile.assertIsCopyOf(localArtifact)
        module.jar.verifyChecksums()
        true
    }

    def initBuild() {
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            group = 'org.gradle'
            version = '2'

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
    }

}
