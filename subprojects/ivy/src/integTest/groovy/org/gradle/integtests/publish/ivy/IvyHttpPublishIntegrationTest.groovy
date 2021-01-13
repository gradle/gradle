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


package org.gradle.integtests.publish.ivy


import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.gradle.util.Matchers.matchesRegexp

class IvyHttpPublishIntegrationTest extends AbstractLegacyIvyPublishTest {
    private static final String BAD_CREDENTIALS = '''
        credentials {
            username 'testuser'
            password 'bad'
        }
        '''
    @Rule
    ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)
    @Rule
    HttpServer server = new HttpServer()

    private IvyHttpModule module
    private IvyHttpRepository ivyHttpRepo

    def setup() {
        ivyHttpRepo = new IvyHttpRepository(server, ivyRepo)
        module = ivyHttpRepo.module("org.gradle", "publish", "2")
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.start()

        settingsFile << 'rootProject.name = "publish"'
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        buildFile << """
        apply plugin: 'java'
        version = '2'
        group = 'org.gradle'
        uploadArchives {
            repositories {
                ivy {
                    credentials {
                        username 'testuser'
                        password 'password'
                    }
                    url "${ivyHttpRepo.uri}"
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

        when:
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        and:
        progressLogging.uploadProgressLogged(module.ivy.uri)
        progressLogging.uploadProgressLogged(module.jar.uri)

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST, AuthScheme.NTLM]
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "reports failure publishing with #credsName credentials to authenticated repository using #authScheme auth"() {
        given:
        buildFile << """
        apply plugin: 'java'
        version = '2'
        group = 'org.gradle'
        uploadArchives {
            repositories {
                ivy {
                    $creds
                    url "${ivyHttpRepo.uri}"
                }
            }
        }
        """

        and:
        server.authenticationScheme = authScheme
        server.allowPut('/repo/org.gradle/publish/2/publish-2.jar', 'testuser', 'password')

        when:
        fails 'uploadArchives'

        then:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \'archives\'')
        failure.assertThatCause(CoreMatchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | credsName | creds
        AuthScheme.BASIC  | 'empty'   | ''
        AuthScheme.DIGEST | 'empty'   | ''
        AuthScheme.NTLM   | 'empty'   | ''
        AuthScheme.BASIC  | 'bad'     | BAD_CREDENTIALS
        AuthScheme.DIGEST | 'bad'     | BAD_CREDENTIALS
        AuthScheme.NTLM   | 'bad'     | BAD_CREDENTIALS
    }

    @ToBeFixedForConfigurationCache
    void reportsFailedPublishToHttpRepository() {
        given:
        def repositoryPort = server.port

        buildFile << """
        apply plugin: 'java'
        uploadArchives {
            repositories {
                ivy {
                    url "${ivyHttpRepo.uri}"
                }
            }
        }
        """

        when:
        server.addBroken("/")

        then:
        fails 'uploadArchives'

        and:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \'archives\'')
        failure.assertThatCause(CoreMatchers.containsString('Received status code 500 from server: broken'))

        when:
        server.stop()

        then:
        fails 'uploadArchives'

        and:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \'archives\'')
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${repositoryPort} (\\[.*\\])? failed: Connection refused.*"))
    }

    @ToBeFixedForConfigurationCache
    void usesFirstConfiguredPatternForPublication() {
        given:
        buildFile << """
        apply plugin: 'java'
        version = '2'
        group = 'org.gradle'
        uploadArchives {
            repositories {
                ivy {
                    artifactPattern "${ivyHttpRepo.artifactPattern}"
                    artifactPattern "http://localhost:${server.port}/alternative/[module]/[artifact]-[revision].[ext]"
                    ivyPattern "${ivyHttpRepo.ivyPattern}"
                    ivyPattern "http://localhost:${server.port}/secondary-ivy/[module]/ivy-[revision].xml"
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
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    @ToBeFixedForConfigurationCache
    void "can publish large artifact to authenticated repository"() {
        configureUploadTask("tools")
        given:
        def largeJar = file("large.jar")
        new RandomAccessFile(largeJar, "rw").withCloseable {
            it.length = 1024 * 1024 * 10 // 10 mb
        }

        buildFile << """
        apply plugin: 'base'
        version = '2'
        group = 'org.gradle'

        configurations {
            tools
        }
        artifacts {
            tools(file('${largeJar.toURI()}')) {
                name 'publish'
            }
        }

        uploadTools {
            repositories {
                ivy {
                    credentials {
                        username 'testuser'
                        password 'password'
                    }
                    url "${ivyHttpRepo.uri}"
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
        run 'uploadTools'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(new TestFile(largeJar))
    }

}
