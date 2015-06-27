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

import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

@LeaksFileHandles
class MavenPublishHttpIntegTest extends AbstractMavenPublishIntegTest {

    @Rule
    HttpServer server

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
        buildFile << publicationBuild(version, group, mavenRemoteRepo.uri)
    }

    def "can publish to an unauthenticated http repo"() {
        given:
        expectModulePublish(module)

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

    @Unroll
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        def credentials = new DefaultPasswordCredentials('username', 'password')

        buildFile << """
            publishing.repositories.maven.credentials {
                username '${credentials.username}'
                password '${credentials.password}'
            }
        """

        server.authenticationScheme = authScheme

        module.artifact.expectPut(credentials)
        module.artifact.sha1.expectPut(credentials)
        module.artifact.md5.expectPut(credentials)
        module.rootMetaData.expectGetMissing(credentials)
        module.rootMetaData.expectPut(credentials)
        module.rootMetaData.sha1.expectPut(credentials)
        module.rootMetaData.md5.expectPut(credentials)
        module.pom.expectPut(credentials)
        module.pom.sha1.expectPut(credentials)
        module.pom.md5.expectPut(credentials)

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

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    def "reports failure publishing with wrong credentials using #authScheme"() {
        given:
        def credentials = new DefaultPasswordCredentials('wrong', 'wrong')

        buildFile << """
            publishing.repositories.maven.credentials {
                username '${credentials.username}'
                password '${credentials.password}'
            }
        """

        server.authenticationScheme = authScheme
        module.artifact.expectPut(401, credentials)
        module.pom.expectPut(401, credentials)

        when:
        fails 'publish'

        then:
        failure.assertHasDescription('Execution failed for task \':publishMavenPublicationToMavenRepository\'.')
        failure.assertHasCause('Failed to publish publication \'maven\' to repository \'maven\'')
        failure.assertHasCause("Failed to deploy artifacts: Could not transfer artifact org.gradle:publish:jar:2 from/to remote (http://localhost:${server.port}/repo): Could not write to resource 'org/gradle/publish/2/publish-2.jar'")
        // Cause goes missing through the maven classes, but does end up logged to stderr
        failure.error.contains("Could not PUT '${module.artifact.uri}'. Received status code 401 from server: Unauthorized")

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    def "reports failure when required credentials are not provided #authScheme"() {
        given:
        server.authenticationScheme = authScheme
        module.artifact.expectPut(401)
        module.pom.expectPut(401)

        when:
        fails 'publish'

        then:
        failure.assertHasDescription('Execution failed for task \':publishMavenPublicationToMavenRepository\'.')
        failure.assertHasCause('Failed to publish publication \'maven\' to repository \'maven\'')
        failure.assertHasCause("Failed to deploy artifacts: Could not transfer artifact org.gradle:publish:jar:2 from/to remote (http://localhost:${server.port}/repo): Could not write to resource 'org/gradle/publish/2/publish-2.jar'")
        // Cause goes missing through the maven classes, but does end up logged to stderr
        failure.error.contains("Could not PUT '${module.artifact.uri}'. Received status code 401 from server: Unauthorized")

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Issue("GRADLE-3312")
    def "can publish to a http repo via redirects"() {
        given:
        HttpServer redirectServer = new HttpServer()
        redirectServer.start()

        mavenRemoteRepo = new MavenHttpRepository(server, repoPath, mavenRepo)
        group = "org.gradle"
        name = "publish"
        version = "2"
        module = mavenRemoteRepo.module(group, name, version)

        buildFile.text = publicationBuild(version, group, new URI("${redirectServer.uri}/repo"))
        expectRedirectPublish(module, server.getUri(), redirectServer)

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

        cleanup:
        redirectServer.stop()
    }

    private String publicationBuild(String version, String group, URI uri) {
        return """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '$version'
            group = '$group'

            publishing {
                repositories {
                    maven {
                        url "$uri"
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

    private void expectModulePublish(MavenHttpModule module) {
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

    private void expectRedirectPublish(MavenHttpModule module, URI targetServerUri, HttpServer httpServer) {
        String redirectUri = targetServerUri.toString()
        [module.artifact, module.pom, module.rootMetaData].each { artifact ->
            [artifact, artifact.sha1, artifact.md5].each { innerArtifact ->
                httpServer.expectPutRedirected(innerArtifact.path, "${redirectUri}${innerArtifact.path}")
                innerArtifact.expectPut()
            }
        }
    }
}
