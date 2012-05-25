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
import org.gradle.integtests.fixtures.HttpServer
import org.hamcrest.Matchers
import org.junit.Rule
import org.mortbay.jetty.HttpStatus
import spock.lang.Issue
import spock.lang.Unroll

public class IvyPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    public void canPublishToLocalFileRepository() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << '''
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "build/repo/"
        }
    }
}
'''
        when:
        succeeds 'uploadArchives'

        then:
        def uploadedIvy = file('build/repo/org.gradle/publish/2/ivy-2.xml')
        uploadedIvy.assertIsFile()
        def uploadedJar = file('build/repo/org.gradle/publish/2/publish-2.jar')
        uploadedJar.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    @Issue("GRADLE-1811")
    public void canGenerateTheIvyXmlWithoutPublishing() {
        //this is more like documenting the current behavior.
        //Down the road we should add explicit task to create ivy.xml file

        given:
        buildFile << '''
apply plugin: 'java'

configurations {
  myJars
}

task myJar(type: Jar)

artifacts {
  'myJars' myJar
}

task ivyXml(type: Upload) {
  descriptorDestination = file('ivy.xml')
  uploadDescriptor = true
  configuration = configurations.myJars
}
'''
        when:
        succeeds 'ivyXml'

        then:
        file('ivy.xml').assertIsFile()
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
        def uploadedIvy = file('uploaded.xml')
        def uploadedJar = file('uploaded.jar')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', uploadedJar, HttpStatus.ORDINAL_200_OK)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', uploadedIvy, HttpStatus.ORDINAL_201_Created)

        and:
        succeeds 'uploadArchives'

        then:
        uploadedJar.assertIsCopyOf(file('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()
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
        def uploadedJar = file('uploaded.jar')
        def uploadedIvy = file('uploaded.xml')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', 'testuser', 'password', uploadedJar)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', 'testuser', 'password', uploadedIvy)

        then:
        succeeds 'uploadArchives'

        and:
        uploadedJar.assertIsCopyOf(file('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
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
        def uploadedJar = file('uploaded.jar')
        def uploadedIvy = file('uploaded.xml')
        server.expectPut('/primary/publish/publish-2.jar', uploadedJar, HttpStatus.ORDINAL_200_OK)
        server.expectPut('/primary-ivy/publish/ivy-2.xml', uploadedIvy, HttpStatus.ORDINAL_201_Created)

        then:
        succeeds 'uploadArchives'

        and:
        uploadedJar.assertIsCopyOf(file('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()
    }

}
