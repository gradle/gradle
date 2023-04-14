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

package org.gradle.api.publish.ivy

import org.eclipse.jetty.http.HttpStatus
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.gradle.util.Matchers.matchesRegexp

class IvyPublishHttpIntegTest extends AbstractIvyPublishIntegTest {
    private static final int HTTP_UNRECOVERABLE_ERROR = 415
    private static final Credentials BAD_CREDENTIALS = new DefaultPasswordCredentials('testuser', 'bad')
    @Rule
    ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)
    @Rule
    HttpServer server = new HttpServer()

    private IvyHttpModule module
    private IvyHttpRepository ivyHttpRepo

    final String group = "org.gradle"
    final String name = "publish"
    final String version = "2"

    def setup() {
        ivyHttpRepo = new IvyHttpRepository(server, ivyRepo)
        module = ivyHttpRepo.module(group, name, version)
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.start()

        settingsFile << "rootProject.name = '$name'"
    }

    def "can publish to unauthenticated HTTP repository (extra checksums = #extraChecksums)"() {
        given:
        buildFile << publicationBuildWithoutCredentials(version, group, ivyHttpRepo.uri)

        if (!extraChecksums) {
            executer.withArgument("-Dorg.gradle.internal.publish.checksums.insecure=true")
            module.withoutExtraChecksums()
        }

        and:
        module.jar.expectPut()
        module.jar.sha1.expectPut()
        if (extraChecksums) {
            module.jar.sha256.expectPut()
            module.jar.sha512.expectPut()
        }
        module.ivy.expectPut(HttpStatus.CREATED_201)
        module.ivy.sha1.expectPut(HttpStatus.CREATED_201)
        if (extraChecksums) {
            module.ivy.sha256.expectPut(HttpStatus.CREATED_201)
            module.ivy.sha512.expectPut(HttpStatus.CREATED_201)
        }
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        if (extraChecksums) {
            module.moduleMetadata.sha256.expectPut()
            module.moduleMetadata.sha512.expectPut()
        }

        when:
        succeeds 'publish'

        then:
        module.assertMetadataAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        and:
        progressLogging.uploadProgressLogged(module.moduleMetadata.uri)
        progressLogging.uploadProgressLogged(module.ivy.uri)
        progressLogging.uploadProgressLogged(module.jar.uri)

        where:
        extraChecksums << [true, false]
    }

    def "can publish to a repository even if it doesn't support sha256/sha512 signatures"() {
        given:
        buildFile << publicationBuildWithoutCredentials(version, group, ivyHttpRepo.uri)

        and:
        maxUploadAttempts = 1

        when:
        module.artifact.expectPut()
        module.artifact.sha1.expectPut()
        module.artifact.sha256.expectPutBroken()
        module.artifact.sha512.expectPutBroken()
        module.ivy.expectPut()
        module.ivy.sha1.expectPut()
        module.ivy.sha256.expectPutBroken()
        module.ivy.sha512.expectPutBroken()
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        module.moduleMetadata.sha256.expectPutBroken()
        module.moduleMetadata.sha512.expectPutBroken()

        then:
        succeeds 'publish'
        outputContains("remote repository doesn't support sha-256. This will not fail the build.")
        outputContains("remote repository doesn't support sha-512. This will not fail the build.")
    }

    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        org.gradle.api.credentials.PasswordCredentials credentials = new DefaultPasswordCredentials('testuser', 'password')
        configureRepositoryCredentials(credentials.username, credentials.password, "ivy")
        buildFile << publicationBuild(version, group, ivyHttpRepo.uri)

        and:
        server.authenticationScheme = authScheme
        expectPublishModuleWithCredentials(module, credentials)

        when:
        run 'publish'

        then:
        module.assertMetadataAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        and:
        progressLogging.uploadProgressLogged(module.ivy.uri)
        progressLogging.uploadProgressLogged(module.moduleMetadata.uri)
        progressLogging.uploadProgressLogged(module.jar.uri)

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    @UnsupportedWithConfigurationCache(because = "inline credentials")
    def "can publish to authenticated repository using inline credentials and #authScheme auth"() {
        given:
        PasswordCredentials credentials = new DefaultPasswordCredentials('testuser', 'password')
        buildFile << publicationBuild(version, group, ivyHttpRepo.uri, "ivy","""
        credentials(PasswordCredentials) {
            username = "${credentials.username}"
            password = "${credentials.password}"
        }
        """)

        and:
        server.authenticationScheme = authScheme
        expectPublishModuleWithCredentials(module, credentials)

        when:
        run 'publish'

        then:
        module.assertMetadataAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        and:
        progressLogging.uploadProgressLogged(module.ivy.uri)
        progressLogging.uploadProgressLogged(module.moduleMetadata.uri)
        progressLogging.uploadProgressLogged(module.jar.uri)

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    def "reports failure publishing with #credsName credentials to authenticated repository using #authScheme auth"() {
        given:
        def credentialsBlock = ''
        if (creds != null) {
            configureRepositoryCredentials(creds.username, creds.password, "ivy")
            credentialsBlock = "credentials(PasswordCredentials)"
        }
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            version = '2'
            group = 'org.gradle'
            publishing {
                repositories {
                    ivy {
                        $credentialsBlock
                        url "${ivyHttpRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        and:
        server.authenticationScheme = authScheme
        server.allowPut('/repo/org.gradle/publish/2/publish-2.jar', 'testuser', 'password')

        when:
        fails 'publish'

        then:
        failure.assertHasDescription('Execution failed for task \':publishIvyPublicationToIvyRepository\'.')
        failure.assertHasCause('Failed to publish publication \'ivy\' to repository \'ivy\'')
        failure.assertThatCause(CoreMatchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme        | credsName | creds
        AuthScheme.BASIC  | 'empty'   | null
        AuthScheme.DIGEST | 'empty'   | null
        AuthScheme.NTLM   | 'empty'   | null
        AuthScheme.BASIC  | 'bad'     | BAD_CREDENTIALS
        AuthScheme.DIGEST | 'bad'     | BAD_CREDENTIALS
        AuthScheme.NTLM   | 'bad'     | BAD_CREDENTIALS
    }

    def "reports failure publishing to HTTP repository"() {
        given:
        def repositoryPort = server.port
        buildFile << publicationBuildWithoutCredentials(version, group, ivyHttpRepo.uri)

        and:
        server.addBroken("/")

        when:
        fails 'publish'

        then:
        failure.assertHasDescription('Execution failed for task \':publishIvyPublicationToIvyRepository\'.')
        failure.assertHasCause('Failed to publish publication \'ivy\' to repository \'ivy\'')
        failure.assertThatCause(CoreMatchers.containsString('Received status code 500 from server: broken'))

        when:
        server.stop()

        then:
        fails 'publish'

        and:
        failure.assertHasDescription('Execution failed for task \':publishIvyPublicationToIvyRepository\'.')
        failure.assertHasCause('Failed to publish publication \'ivy\' to repository \'ivy\'')
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${repositoryPort} (\\[.*\\])? failed: Connection refused.*"))
    }

    def "uses first configured pattern for publication"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'
            publishing {
                repositories {
                    ivy {
                        artifactPattern "${ivyHttpRepo.artifactPattern}"
                        artifactPattern "http://localhost:${server.port}/alternative/[module]/[artifact]-[revision].[ext]"
                        ivyPattern "${ivyHttpRepo.ivyPattern}"
                        ivyPattern "http://localhost:${server.port}/secondary-ivy/[module]/ivy-[revision].xml"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        and:
        module.jar.expectPut()
        module.jar.sha1.expectPut()
        module.jar.sha256.expectPut()
        module.jar.sha512.expectPut()
        module.ivy.expectPut()
        module.ivy.sha1.expectPut()
        module.ivy.sha256.expectPut()
        module.ivy.sha512.expectPut()

        when:
        run 'publish'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        outputContains "Publication of Gradle Module Metadata is disabled because you have configured an Ivy repository with a non-standard layout"
        !module.ivy.file.text.contains(MetaDataParser.GRADLE_6_METADATA_MARKER)
    }

    void "can publish large artifact to authenticated repository"() {
        given:
        def largeJar = file("large.jar")
        new RandomAccessFile(largeJar, "rw").withCloseable {
            it.length = 1024 * 1024 * 10 // 10 mb
        }

        configureRepositoryCredentials("testuser", "password", "ivy")
        buildFile << """
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy {
                        credentials(PasswordCredentials)
                        url "${ivyHttpRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        configurations {
                            runtime {
                                artifact('${largeJar.toURI()}') {
                                    name 'publish'
                                }
                            }
                        }
                    }
                }
            }
        """

        and:
        module.jar.expectPut('testuser', 'password')
        module.jar.sha1.expectPut('testuser', 'password')
        module.jar.sha256.expectPut('testuser', 'password')
        module.jar.sha512.expectPut('testuser', 'password')
        module.ivy.expectPut('testuser', 'password')
        module.ivy.sha1.expectPut('testuser', 'password')
        module.ivy.sha256.expectPut('testuser', 'password')
        module.ivy.sha512.expectPut('testuser', 'password')

        when:
        run 'publish'

        then:
        module.assertIvyAndJarFilePublished()
        module.ivyFile.assertIsFile()
        module.jarFile.assertIsCopyOf(new TestFile(largeJar))
    }

    void "does not upload meta-data file if artifact upload fails"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy {
                        url "${ivyHttpRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        and:
        module.jar.expectPutBroken(HTTP_UNRECOVERABLE_ERROR)

        when:
        fails ':publish'

        then:
        module.jarFile.assertExists()
        module.ivyFile.assertDoesNotExist()
    }

    def "retries artifact upload for transient network error"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy { url "${ivyHttpRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        and:
        module.jar.expectPutBroken()
        module.jar.expectPutBroken()
        module.jar.expectPut()
        module.jar.sha1.expectPut()
        module.jar.sha256.expectPut()
        module.jar.sha512.expectPut()

        module.ivy.expectPutBroken()
        module.ivy.expectPut(HttpStatus.CREATED_201)
        module.ivy.sha1.expectPut(HttpStatus.CREATED_201)
        module.ivy.sha256.expectPut(HttpStatus.CREATED_201)
        module.ivy.sha512.expectPut(HttpStatus.CREATED_201)

        module.moduleMetadata.expectPutBroken()
        module.moduleMetadata.expectPut()
        module.moduleMetadata.sha1.expectPut()
        module.moduleMetadata.sha256.expectPut()
        module.moduleMetadata.sha512.expectPut()

        when:
        succeeds 'publish'

        then:
        module.assertMetadataAndJarFilePublished()
    }

    def "doesn't publish Gradle metadata if custom pattern is used for artifact"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'
            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                        patternLayout {
                           $layout
                        }
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
        run 'publish'

        then:
        outputContains "Publication of Gradle Module Metadata is disabled because you have configured an Ivy repository with a non-standard layout"

        where:
        layout << [
            """
                artifact "org/foo/[revision]/[artifact](-[classifier]).[ext]"
                ivy "org/foo/[revision]/[artifact](-[classifier]).[ext]"
            """,
            'artifact "org/foo/[revision]/[artifact](-[classifier]).[ext]"'
        ]
    }

    def "does publish Gradle metadata if custom pattern is used only for Ivy file"() {
        def customIvyRepo = new IvyFileRepository(ivyRepo.rootDir, false, null, '[module]-[revision].ivy')
        def customModule = customIvyRepo.module("org.gradle", "publish", "2")
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'
            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                        patternLayout {
                            artifact "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])"
                            ivy "[organisation]/[module]/[revision]/[module]-[revision].ivy"
                        }
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
        run 'publish'

        then:
        customModule.assertMetadataAndJarFilePublished()
    }

    void "can publish artifact to authenticated repository using credentials provider"() {
        given:
        buildFile << publicationBuildWithCredentialsProvider('2', 'org.gradle', ivyHttpRepo.uri)

        and:
        PasswordCredentials credentials = new DefaultPasswordCredentials('username', 'password')
        expectPublishModuleWithCredentials(module, credentials)

        when:
        executer.withArguments("-PivyUsername=${credentials.username}", "-PivyPassword=${credentials.password}")
        succeeds 'publish'

        then:
        module.assertMetadataAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    def "fails at configuration time with helpful error message when username and password provider has no value"() {
        given:
        buildFile << publicationBuildWithCredentialsProvider('2', 'org.gradle', ivyHttpRepo.uri)

        when:
        succeeds 'jar'

        and:
        succeeds 'tasks'

        and:
        fails 'publish'

        then:
        notExecuted(':jar', ':publishIvyPublicationToIvyRepository')
        failure.assertHasDescription("Credentials required for this build could not be resolved.")
        failure.assertHasCause("The following Gradle properties are missing for 'ivy' credentials:")
        failure.assertHasErrorOutput("- ivyUsername")
        failure.assertHasErrorOutput("- ivyPassword")
    }

    @Issue("https://github.com/gradle/gradle/issues/14902")
    def "does not fail when publishing is set to always up to date"() {
        given:
        configureRepositoryCredentials("foo", "bar", "ivy")
        buildFile << publicationBuild(version, group, ivyHttpRepo.uri)
        server.authenticationScheme = AuthScheme.BASIC
        org.gradle.api.credentials.PasswordCredentials credentials = new DefaultPasswordCredentials('foo', 'bar')
        expectPublishModuleWithCredentials(module, credentials)

        when:
        buildFile << """
        tasks.withType(PublishToIvyRepository).configureEach {
            outputs.upToDateWhen { true }
        }
        """

        then:
        succeeds 'publish'
    }

    private static String publicationBuildWithoutCredentials(String version, String group, URI uri) {
        return publicationBuild(version, group, uri, "ivy", '')
    }

    private static String publicationBuildWithCredentialsProvider(String version, String group, URI uri, Class<? extends Credentials> credentialsType = org.gradle.api.credentials.PasswordCredentials.class) {
        return publicationBuild(version, group, uri, "ivy", "credentials(${credentialsType.simpleName})")
    }

    private static String publicationBuild(String version, String group, URI uri, String repoName = "ivy", String credentialsBlock = "credentials(PasswordCredentials)") {
        return """
            plugins {
                id 'java'
                id 'ivy-publish'
            }
            version = '$version'
            group = '$group'

            publishing {
                repositories {
                    ivy {
                        name "$repoName"
                        url "$uri"
                        $credentialsBlock
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

    private static void expectPublishModuleWithCredentials(IvyHttpModule module, org.gradle.api.credentials.PasswordCredentials credentials) {
        module.jar.expectPut(credentials)
        module.jar.sha1.expectPut(credentials)
        module.jar.sha256.expectPut(credentials)
        module.jar.sha512.expectPut(credentials)
        module.ivy.expectPut(credentials)
        module.ivy.sha1.expectPut(credentials)
        module.ivy.sha256.expectPut(credentials)
        module.ivy.sha512.expectPut(credentials)
        module.moduleMetadata.expectPut(credentials)
        module.moduleMetadata.sha1.expectPut(credentials)
        module.moduleMetadata.sha256.expectPut(credentials)
        module.moduleMetadata.sha512.expectPut(credentials)
    }

}
