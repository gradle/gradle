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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.hamcrest.CoreMatchers
import org.junit.Rule
import org.mortbay.jetty.HttpStatus
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.gradle.util.Matchers.matchesRegexp
import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9

class IvyPublishHttpIntegTest extends AbstractIvyPublishIntegTest {
    private static final int HTTP_UNRECOVERABLE_ERROR = 415
    private static final String BAD_CREDENTIALS = '''
credentials {
    username 'testuser'
    password 'bad'
}
'''
    @Rule ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)
    @Rule HttpServer server = new HttpServer()

    private IvyHttpModule module
    private IvyHttpRepository ivyHttpRepo

    def setup() {
        ivyHttpRepo = new IvyHttpRepository(server, ivyRepo)
        module = ivyHttpRepo.module("org.gradle", "publish", "2")
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can publish to unauthenticated HTTP repository (extra checksums = #extraChecksums)"() {
        given:
        server.start()
        settingsFile << 'rootProject.name = "publish"'
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
        module.ivy.expectPut(HttpStatus.ORDINAL_201_Created)
        module.ivy.sha1.expectPut(HttpStatus.ORDINAL_201_Created)
        if (extraChecksums) {
            module.ivy.sha256.expectPut(HttpStatus.ORDINAL_201_Created)
            module.ivy.sha512.expectPut(HttpStatus.ORDINAL_201_Created)
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

    @ToBeFixedForInstantExecution
    def "can publish to a repository even if it doesn't support sha256/sha512 signatures"() {
        given:
        server.start()
        settingsFile << 'rootProject.name = "publish"'
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
        outputContains("Remote repository doesn't support sha-256")
        outputContains("Remote repository doesn't support sha-512")
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy {
                        credentials {
                            username 'testuser'
                            password 'password'
                        }
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
        module.jar.expectPut('testuser', 'password')
        module.jar.sha1.expectPut('testuser', 'password')
        module.jar.sha256.expectPut('testuser', 'password')
        module.jar.sha512.expectPut('testuser', 'password')
        module.ivy.expectPut('testuser', 'password')
        module.ivy.sha1.expectPut('testuser', 'password')
        module.ivy.sha256.expectPut('testuser', 'password')
        module.ivy.sha512.expectPut('testuser', 'password')
        module.moduleMetadata.expectPut('testuser', 'password')
        module.moduleMetadata.sha1.expectPut('testuser', 'password')
        module.moduleMetadata.sha256.expectPut('testuser', 'password')
        module.moduleMetadata.sha512.expectPut('testuser', 'password')

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

    @Unroll
    @ToBeFixedForInstantExecution
    def "reports failure publishing with #credsName credentials to authenticated repository using #authScheme auth"() {
        given:
        server.start()

        and:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            version = '2'
            group = 'org.gradle'
            publishing {
                repositories {
                    ivy {
                        $creds
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
        AuthScheme.BASIC  | 'empty'   | ''
        AuthScheme.DIGEST | 'empty'   | ''
        AuthScheme.NTLM   | 'empty'   | ''
        AuthScheme.BASIC  | 'bad'     | BAD_CREDENTIALS
        AuthScheme.DIGEST | 'bad'     | BAD_CREDENTIALS
        AuthScheme.NTLM   | 'bad'     | BAD_CREDENTIALS
    }

    @ToBeFixedForInstantExecution
    def "reports failure publishing to HTTP repository"() {
        given:
        server.start()
        def repositoryPort = server.port

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

    @ToBeFixedForInstantExecution
    def "uses first configured pattern for publication"() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
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

    @Requires(FIX_TO_WORK_ON_JAVA9)
    @Issue('provide a different large jar')
    @ToBeFixedForInstantExecution
    public void "can publish large artifact (tools.jar) to authenticated repository"() {
        given:
        server.start()
        def toolsJar = Jvm.current().toolsJar

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy {
                        credentials {
                            username 'testuser'
                            password 'password'
                        }
                        url "${ivyHttpRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        configurations {
                            runtime {
                                artifact('${toolsJar.toURI()}') {
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
        module.jarFile.assertIsCopyOf(new TestFile(toolsJar))
    }

    @ToBeFixedForInstantExecution
    public void "does not upload meta-data file if artifact upload fails"() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
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

    @ToBeFixedForInstantExecution
    def "retries artifact upload for transient network error"() {
        given:
        server.start()
        settingsFile << 'rootProject.name = "publish"'
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
        module.ivy.expectPut(HttpStatus.ORDINAL_201_Created)
        module.ivy.sha1.expectPut(HttpStatus.ORDINAL_201_Created)
        module.ivy.sha256.expectPut(HttpStatus.ORDINAL_201_Created)
        module.ivy.sha512.expectPut(HttpStatus.ORDINAL_201_Created)

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

    @ToBeFixedForInstantExecution
    @Unroll
    def "doesn't publish Gradle metadata if custom pattern is used"() {
        given:

        settingsFile << 'rootProject.name = "publish"'
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

}
