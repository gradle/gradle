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

import org.gradle.util.GradleVersion
import org.gradle.util.Jvm
import org.gradle.util.TestFile
import org.gradle.util.TextUtil
import org.hamcrest.Matchers
import org.junit.Rule
import org.mortbay.jetty.HttpStatus
import spock.lang.Unroll
import org.gradle.integtests.fixtures.*

import static org.gradle.integtests.fixtures.UserAgentMatcher.matchesNameAndVersion

public class IvyHttpPublishIntegrationTest extends AbstractIntegrationSpec {
    private static final String BAD_CREDENTIALS = '''
credentials {
    username 'testuser'
    password 'bad'
}
'''
    @Rule
    public final HttpServer server = new HttpServer()

    @Rule ProgressLoggingFixture progressLogging

    private IvyModule module

    def setup() {
        def repo = new IvyFileRepository(distribution.testFile('ivy-repo'))
        module = repo.module("org.gradle", "publish", "2")
        module.moduleDir.mkdirs()
        //for unknown os tests
        file("gradle.properties") << System.properties.findAll {key, value -> key.startsWith("os.")}.collect {key, value -> "systemProp.${key}=$value"}.join("\n")
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
    }

    public void canPublishToUnauthenticatedHttpRepository() {
        given:
        server.start()
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

uploadArchives {
    repositories {
        ivy {
            url "http://localhost:${server.port}"
        }
    }
}
"""
        when:
        expectUpload('/org.gradle/publish/2/publish-2.jar', module, module.jarFile, HttpStatus.ORDINAL_200_OK)
        expectUpload('/org.gradle/publish/2/ivy-2.xml', module, module.ivyFile, HttpStatus.ORDINAL_201_Created)

        and:
        succeeds 'uploadArchives'

        then:
        module.ivyFile.assertIsFile()
        module.assertChecksumPublishedFor(module.ivyFile)

        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.assertChecksumPublishedFor(module.jarFile)
        and:
        progressLogging.uploadProgressLogged("http://localhost:${server.port}/org.gradle/publish/2/ivy-2.xml")
        progressLogging.uploadProgressLogged("http://localhost:${server.port}/org.gradle/publish/2/publish-2.jar")
    }


    @Unroll
    def "can publish to authenticated repository using #authScheme auth"() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
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
            url "http://localhost:${server.port}"
        }
    }
}
"""

        when:
        server.authenticationScheme = authScheme
        expectUpload('/org.gradle/publish/2/publish-2.jar', module, module.jarFile, 'testuser', 'password')
        expectUpload('/org.gradle/publish/2/ivy-2.xml', module, module.ivyFile, 'testuser', 'password')

        then:
        succeeds 'uploadArchives'

        and:
        module.ivyFile.assertIsFile()
        module.assertChecksumPublishedFor(module.ivyFile)
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.assertChecksumPublishedFor(module.jarFile)

        and:
        progressLogging.uploadProgressLogged("http://localhost:${server.port}/org.gradle/publish/2/ivy-2.xml")
        progressLogging.uploadProgressLogged("http://localhost:${server.port}/org.gradle/publish/2/publish-2.jar")

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    def "reports failure publishing with #credsName credentials to authenticated repository using #authScheme auth"() {
        given:
        server.start()

        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            $creds
            url "http://localhost:${server.port}"
        }
    }
}
"""

        and:
        server.authenticationScheme = authScheme
        server.allowPut('/org.gradle/publish/2/publish-2.jar', 'testuser', 'password')

        then:
        fails 'uploadArchives'

        and:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \':archives\'.')
        failure.assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | credsName | creds
        HttpServer.AuthScheme.BASIC  | 'empty'   | ''
        HttpServer.AuthScheme.DIGEST | 'empty'   | ''
        HttpServer.AuthScheme.BASIC  | 'bad'     | BAD_CREDENTIALS
        HttpServer.AuthScheme.DIGEST | 'bad'     | BAD_CREDENTIALS
    }

    public void reportsFailedPublishToHttpRepository() {
        given:
        server.start()
        def repositoryUrl = "http://localhost:${server.port}"

        buildFile << """
apply plugin: 'java'
uploadArchives {
    repositories {
        ivy {
            url "${repositoryUrl}"
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
        failure.assertHasCause('Could not publish configuration \':archives\'.')
        failure.assertThatCause(Matchers.containsString('Received status code 500 from server: broken'))

        when:
        server.stop()

        then:
        fails 'uploadArchives'

        and:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \':archives\'.')
        failure.assertHasCause("org.apache.http.conn.HttpHostConnectException: Connection to ${repositoryUrl} refused")
    }

    public void usesFirstConfiguredPatternForPublication() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
    apply plugin: 'java'
    version = '2'
    group = 'org.gradle'
    uploadArchives {
        repositories {
            ivy {
                artifactPattern "http://localhost:${server.port}/primary/[module]/[artifact]-[revision].[ext]"
                artifactPattern "http://localhost:${server.port}/alternative/[module]/[artifact]-[revision].[ext]"
                ivyPattern "http://localhost:${server.port}/primary-ivy/[module]/ivy-[revision].xml"
                ivyPattern "http://localhost:${server.port}/secondary-ivy/[module]/ivy-[revision].xml"
            }
        }
    }
    """

        when:
        expectUpload('/primary/publish/publish-2.jar', module, module.jarFile, HttpStatus.ORDINAL_200_OK)
        expectUpload('/primary-ivy/publish/ivy-2.xml', module, module.ivyFile)

        then:
        succeeds 'uploadArchives'

        and:
        module.ivyFile.assertIsFile()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    public void "can publish large artifact (tools.jar) to authenticated repository"() {
        given:
        server.start()
        def toolsJar = TextUtil.escapeString(Jvm.current().toolsJar)

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'base'
version = '2'
group = 'org.gradle'

configurations {
    tools
}
artifacts {
    tools(file('$toolsJar')) {
        name 'tools'
    }
}

uploadTools {
    repositories {
        ivy {
            credentials {
                username 'testuser'
                password 'password'
            }
            url "http://localhost:${server.port}"
        }
    }
}
"""

        when:
        def uploadedToolsJar = module.moduleDir.file('toolsJar')
        expectUpload('/org.gradle/publish/2/tools-2.jar', module, uploadedToolsJar, 'testuser', 'password')
        expectUpload('/org.gradle/publish/2/ivy-2.xml', module, module.ivyFile, 'testuser', 'password')

        then:
        succeeds 'uploadTools'

        and:
        module.ivyFile.assertIsFile()
        uploadedToolsJar.assertIsCopyOf(new TestFile(toolsJar));

    }

    public void "does not upload meta-data file if artifact upload fails"() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "http://localhost:${server.port}"
        }
    }
}
"""
        when:
        server.expectPut("/org.gradle/publish/2/publish-2.jar", module.jarFile, HttpStatus.ORDINAL_500_Internal_Server_Error)

        then:
        fails 'uploadArchives'

        and:
        module.jarFile.assertExists()
        module.ivyFile.assertDoesNotExist()
    }

    private void expectUpload(String path, IvyModule module, TestFile file, int statusCode = HttpStatus.ORDINAL_200_OK) {
        server.expectPut(path, file, statusCode)
        server.expectPut("${path}.sha1", module.sha1File(file), statusCode)
    }

    private void expectUpload(String path, IvyModule module, TestFile file, String username, String password) {
        server.expectPut(path, username, password, file)
        server.expectPut("${path}.sha1", username, password, module.sha1File(file))
    }
}
