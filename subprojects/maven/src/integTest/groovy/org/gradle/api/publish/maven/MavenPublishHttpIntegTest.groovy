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

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue

class MavenPublishHttpIntegTest extends AbstractMavenPublishIntegTest {

    @Rule
    HttpServer server
    @Rule
    HttpServer redirectServer

    final String repoPath = "/repo"
    final String group = "org.gradle"
    final String name = "publish"
    final String version = "2"

    MavenHttpRepository mavenRemoteRepo
    MavenHttpModule module

    def setup() {
        server.start()

        mavenRemoteRepo = new MavenHttpRepository(server, repoPath, mavenRepo)
        module = mavenRemoteRepo.module(group, name, version).withModuleMetadata()

        settingsFile << "rootProject.name = '$name'"
    }

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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
        outputContains("remote repository doesn't support SHA-256. This will not fail the build.")
        outputContains("remote repository doesn't support SHA-512. This will not fail the build.")
    }


    @ToBeFixedForConfigurationCache
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        PasswordCredentials credentials = new DefaultPasswordCredentials('username', 'password')
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, credentials)

        server.authenticationScheme = authScheme

        expectPublishModuleWithCredentials(module, credentials)

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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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
    @ToBeFixedForConfigurationCache
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
    @ToBeFixedForConfigurationCache
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
    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    def "can publish to authenticated repository using credentials Provider with inferred identity"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, "credentials(PasswordCredentials)")
        server.authenticationScheme = AuthScheme.BASIC
        PasswordCredentials credentials = new DefaultPasswordCredentials('username', 'password')
        expectPublishModuleWithCredentials(module, credentials)

        when:
        executer.withArguments("-PmavenUsername=${credentials.username}", "-PmavenPassword=${credentials.password}")

        then:
        succeeds 'publish'
    }

    def "fails at configuration time with helpful error message when username and password provider has no value"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, "credentials(PasswordCredentials)")

        when:
        succeeds 'jar'

        and:
        succeeds 'tasks'

        and:
        fails 'publish'

        then:
        notExecuted(':jar', ':publishMavenPublicationToMavenRepository')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'maven' credentials:")
        failure.assertHasErrorOutput("- mavenUsername")
        failure.assertHasErrorOutput("- mavenPassword")
    }

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle/issues/14902")
    def "does not fail when publishing is set to always up to date"() {
        given:
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri, """
        credentials {
            username 'foo'
            password 'bar'
        }
        """)
        server.authenticationScheme = AuthScheme.BASIC
        PasswordCredentials credentials = new DefaultPasswordCredentials('foo', 'bar')
        expectPublishModuleWithCredentials(module, credentials)

        when:
        buildFile << """
        tasks.withType(PublishToMavenRepository).configureEach {
            outputs.upToDateWhen { true }
        }
        """

        then:
        succeeds 'publish'
    }

    private static String publicationBuild(String version, String group, URI uri, PasswordCredentials credentials = null) {
        String credentialsBlock = credentials ? """
                        credentials {
                            username '${credentials.username}'
                            password '${credentials.password}'
                        }
                        """ : ''
        return publicationBuild(version, group, uri, credentialsBlock)
    }

    private static String publicationBuild(String version, String group, URI uri, String credentialsBlock) {
        return """
            plugins {
                id 'java'
                id 'maven-publish'
            }
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

    private static void expectPublishModuleWithCredentials(MavenHttpModule module, PasswordCredentials credentials) {
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
    }

    private static void expectModulePublish(MavenHttpModule module, boolean extraChecksums = true) {
        module.artifact.expectPublish(extraChecksums)
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPublish(extraChecksums)
        module.pom.expectPublish(extraChecksums)
        module.moduleMetadata.expectPublish(extraChecksums)
    }

    private static void expectModulePublishViaRedirect(MavenHttpModule module, URI targetServerUri, HttpServer httpServer, PasswordCredentials credentials = null) {
        String redirectUri = targetServerUri.toString()
        [module.artifact, module.pom, module.rootMetaData, module.moduleMetadata].each { artifact ->
            [artifact, artifact.sha1, artifact.md5, artifact.sha256, artifact.sha512].each { innerArtifact ->
                httpServer.expectPutRedirected(innerArtifact.path, "${redirectUri}${innerArtifact.path}", credentials)
                innerArtifact.expectPut()
            }
        }
    }
}
