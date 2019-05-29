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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
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
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.gradle.util.Matchers.matchesRegexp
import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9

public class IvyHttpPublishIntegrationTest extends AbstractIntegrationSpec {
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
            url "${ivyHttpRepo.uri}"
        }
    }
}
"""

        and:
        server.authenticationScheme = authScheme
        module.jar.expectPut('testuser', 'password')
        module.jar.sha1.expectPut('testuser', 'password')
        module.ivy.expectPut('testuser', 'password')
        module.ivy.sha1.expectPut('testuser', 'password')

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
    def "reports failure publishing with #credsName credentials to authenticated repository using #authScheme auth"() {
        given:
        server.start()

        and:
        settingsFile << 'rootProject.name = "publish"'
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

    public void reportsFailedPublishToHttpRepository() {
        given:
        server.start()
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
        failure.assertThatCause(matchesRegexp(".*?Connect to localhost:${repositoryPort} (\\[.*\\])? failed: Connection refused.*"))
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
        module.ivy.expectPut()
        module.ivy.sha1.expectPut()

        when:
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    @Requires(FIX_TO_WORK_ON_JAVA9)
    @Issue('provide a different large jar')
    public void "can publish large artifact (tools.jar) to authenticated repository"() {
        given:
        server.start()
        def toolsJar = Jvm.current().toolsJar

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'base'
version = '2'
group = 'org.gradle'

configurations {
    tools
}
artifacts {
    tools(file('${toolsJar.toURI()}')) {
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
        module.ivy.expectPut('testuser', 'password')
        module.ivy.sha1.expectPut('testuser', 'password')

        when:
        run 'uploadTools'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(new TestFile(toolsJar))
    }
}
