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

package org.gradle.api.publish.maven

import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

class MavenPublishHttpIntegTest extends AbstractMavenPublishIntegTest {

    @Rule
    HttpServer server
    @Rule
    HttpServer redirectServer

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
        module = mavenRemoteRepo.module(group, name, version).withModuleMetadata()

        settingsFile << 'rootProject.name = "publish"'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can publish to an unauthenticated http repo (with extra checksums = #extraChecksums)"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)

        if (!extraChecksums) {
            executer.withArgument("-Dorg.gradle.internal.publish.checksums.insecure=true")
            module.withoutExtraChecksums()
        }
        expectModulePublish(module, extraChecksums)

        when:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        module.rootMetaData.versions == ["2"]
        module.moduleMetadata.verifyChecksums()

        where:
        extraChecksums << [true, false]
    }

    @ToBeFixedForInstantExecution
    def "can publish to a repository even if it doesn't support sha256/sha512 signatures"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)
        maxUploadAttempts = 1

        when:
        module.artifact.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.sha256.expectPutBroken()
        module.artifact.sha512.expectPutBroken()
        module.artifact.md5.expectPut()
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPut()
        module.rootMetaData.sha1.expectPut()
        module.rootMetaData.sha256.expectPutBroken()
        module.rootMetaData.sha512.expectPutBroken()
        module.rootMetaData.md5.expectPut()
        module.pom.expectPut()
        module.pom.sha1.expectPut()
        module.pom.sha256.expectPutBroken()
        module.pom.sha512.expectPutBroken()
        module.pom.md5.expectPut()
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        module.moduleMetadata.sha256.expectPutBroken()
        module.moduleMetadata.sha512.expectPutBroken()
        module.moduleMetadata.md5.expectPut()

        then:
        succeeds 'publish'
        outputContains("Remote repository doesn't support sha-256")
        outputContains("Remote repository doesn't support sha-512")
    }


    @Unroll
    @ToBeFixedForInstantExecution
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        PasswordCredentials credentials = new DefaultPasswordCredentials('username', 'password')
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, credentials)

        server.authenticationScheme = authScheme

        module.artifact.expectPut(credentials)
        module.artifact.sha1.expectPut(credentials)
        module.artifact.sha256.expectPut(credentials)
        module.artifact.sha512.expectPut(credentials)
        module.artifact.md5.expectPut(credentials)
        module.rootMetaData.expectGetMissing(credentials)
        module.rootMetaData.expectPut(credentials)
        module.rootMetaData.sha1.expectPut(credentials)
        module.rootMetaData.sha256.expectPut(credentials)
        module.rootMetaData.sha512.expectPut(credentials)
        module.rootMetaData.md5.expectPut(credentials)
        module.pom.expectPut(credentials)
        module.pom.sha1.expectPut(credentials)
        module.pom.sha256.expectPut(credentials)
        module.pom.sha512.expectPut(credentials)
        module.pom.md5.expectPut(credentials)
        module.moduleMetadata.expectPut(credentials)
        module.moduleMetadata.sha1.expectPut(credentials)
        module.moduleMetadata.sha256.expectPut(credentials)
        module.moduleMetadata.sha512.expectPut(credentials)
        module.moduleMetadata.md5.expectPut(credentials)

        when:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.moduleMetadata.verifyChecksums()
        module.rootMetaData.verifyChecksums()
        module.rootMetaData.versions == ["2"]

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "reports failure publishing with wrong credentials using #authScheme"() {
        given:
        PasswordCredentials credentials = new DefaultPasswordCredentials('wrong', 'wrong')
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, credentials)

        server.authenticationScheme = authScheme
        module.artifact.expectPut(401, credentials)

        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Could not PUT '${module.artifact.uri}'. Received status code 401 from server: Unauthorized")

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "reports failure when required credentials are not provided #authScheme"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)
        server.authenticationScheme = authScheme
        module.artifact.expectPut(401)

        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'maven' to repository 'maven'")
        failure.assertHasCause("Could not PUT '${module.artifact.uri}'. Received status code 401 from server: Unauthorized")

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    @Issue("GRADLE-3312")
    @ToBeFixedForInstantExecution
    def "can publish to a http repo via redirects"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)
        redirectServer.start()
        buildFile.text = publicationBuild(version, group, new URI("${redirectServer.uri}/repo"))

        redirectServer.expectGetRedirected(module.rootMetaData.path, "${server.uri}${module.rootMetaData.path}")
        module.rootMetaData.expectGetMissing()

        expectModulePublishViaRedirect(module, server.getUri(), redirectServer)

        when:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        module.rootMetaData.versions == ["2"]
    }

    @Issue("GRADLE-3312")
    @ToBeFixedForInstantExecution
    def "can publish to an authenticated http repo via redirects"() {
        given:
        redirectServer.start()

        PasswordCredentials credentials = new DefaultPasswordCredentials('username', 'password')
        buildFile.text = publicationBuild(version, group, new URI("${redirectServer.uri}/repo"), credentials)

        redirectServer.expectGetRedirected(module.rootMetaData.path, "${server.uri}${module.rootMetaData.path}", credentials)
        module.rootMetaData.expectGetMissing()

        expectModulePublishViaRedirect(module, server.getUri(), redirectServer, credentials)

        when:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact = file("build/libs/publish-2.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact)
        module.artifact.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        module.rootMetaData.versions == ["2"]
    }

    @Issue("gradle/gradle#1641")
    @ToBeFixedForInstantExecution
    def "can publish a new version of a module already present in the target repository"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)
        expectModulePublish(module)

        when:
        succeeds 'publish'

        and:
        buildFile.text = publicationBuild("3", group, mavenRemoteRepo.uri)
        module = mavenRemoteRepo.module(group, name, "3")

        then:
        module.artifact.expectPublish()
        module.pom.expectPublish()
        module.moduleMetadata.expectPublish()

        and:
        module.rootMetaData.expectGet()
        module.rootMetaData.expectPublish()

        and:
        succeeds 'publish'

        then:
        def localPom = file("build/publications/maven/pom-default.xml").assertIsFile()
        def localArtifact3 = file("build/libs/publish-3.jar").assertIsFile()

        module.pomFile.assertIsCopyOf(localPom)
        module.pom.verifyChecksums()
        module.artifactFile.assertIsCopyOf(localArtifact3)
        module.artifact.verifyChecksums()

        module.moduleMetadata.verifyChecksums()

        module.rootMetaData.verifyChecksums()
        module.rootMetaData.versions == ["2", "3"]
    }

    @ToBeFixedForInstantExecution
    def "retries artifact upload for transient network error"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)

        module.artifact.expectPutBroken()
        module.artifact.expectPutBroken()
        module.artifact.expectPublish()

        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPutBroken()
        module.rootMetaData.expectPublish()

        module.pom.expectPutBroken()
        module.pom.expectPublish()

        module.moduleMetadata.expectPutBroken()
        module.moduleMetadata.expectPublish()

        when:
        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
    }

    private String publicationBuild(String version, String group, URI uri, PasswordCredentials credentials = null) {
        String credentialsBlock = credentials ? """
                        credentials{
                            username '${credentials.username}'
                            password '${credentials.password}'
                        }
                        """ : ''
        return """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '$version'
            group = '$group'

            publishing {
                repositories {
                    maven {
                        url "$uri"
                        ${credentialsBlock}
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

    private void expectModulePublish(MavenHttpModule module, boolean extraChecksums = true) {
        module.artifact.expectPublish(extraChecksums)
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPublish(extraChecksums)
        module.pom.expectPublish(extraChecksums)
        module.moduleMetadata.expectPublish(extraChecksums)
    }

    private void expectModulePublishViaRedirect(MavenHttpModule module, URI targetServerUri, HttpServer httpServer, PasswordCredentials credentials = null) {
        String redirectUri = targetServerUri.toString()
        [module.artifact, module.pom, module.rootMetaData, module.moduleMetadata].each { artifact ->
            [artifact, artifact.sha1, artifact.md5, artifact.sha256, artifact.sha512].each { innerArtifact ->
                httpServer.expectPutRedirected(innerArtifact.path, "${redirectUri}${innerArtifact.path}", credentials)
                innerArtifact.expectPut()
            }
        }
    }
}
