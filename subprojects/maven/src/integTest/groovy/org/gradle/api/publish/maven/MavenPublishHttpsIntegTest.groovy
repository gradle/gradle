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

package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule

@LeaksFileHandles
class MavenPublishHttpsIntegTest extends AbstractMavenPublishIntegTest {
    TestKeyStore keyStore

    @Rule public final HttpServer server = new HttpServer()

    MavenHttpRepository mavenRemoteRepo
    MavenHttpModule module

    def setup() {
        keyStore = TestKeyStore.init(file("keystore"))
        server.start()

        mavenRemoteRepo = new MavenHttpRepository(server, "/repo", mavenRepo)
        module = mavenRemoteRepo.module('org.gradle', 'publish', '2')
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
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Failed to deploy artifacts: Could not transfer artifact org.gradle:publish:jar:2 from/to remote (https://localhost:${server.sslPort}/repo): Could not write to resource 'org/gradle/publish/2/publish-2.jar'")
        // TODO:DAZ Get this exception into the cause
        failure.error.contains("peer not authenticated")
    }

    def "decent error message when server can't authenticate client"() {
        keyStore.enableSslWithServerAndBadClientCert(server)
        initBuild()

        when:
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        keyStore.configureServerAndClientCerts(executer)

        fails 'publish'

        then:
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Failed to deploy artifacts: Could not transfer artifact org.gradle:publish:jar:2 from/to remote (https://localhost:${server.sslPort}/repo): Could not write to resource 'org/gradle/publish/2/publish-2.jar'")
        failure.error.contains("peer not authenticated")
    }

    def expectPublication() {
        module.artifact.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.md5.expectPut()
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPut()
        module.rootMetaData.sha1.expectPut()
        module.rootMetaData.md5.expectPut()
        module.pom.expectPut()
        module.pom.sha1.expectPut()
        module.pom.md5.expectPut()
    }

    def verifyPublications() {
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        assert module.rootMetaData.versions == ["2"]
        true
    }

    def initBuild() {
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            group = 'org.gradle'
            version = '2'

            publishing {
                repositories {
                    maven {
                        url '${mavenRemoteRepo.uri}'
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
    }

}
