/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.mortbay.jetty.HttpStatus
import spock.lang.Issue

public class IvyPublishIntegrationTest {
    @Rule
    public final GradleDistribution dist = new GradleDistribution()
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule
    public final HttpServer server = new HttpServer()

    @Test
    public void canPublishToLocalFileRepository() {
        dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
        dist.testFile("build.gradle") << '''
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
        executer.withTasks("uploadArchives").run()

        def uploadedJar = dist.testFile('build/repo/org.gradle/publish/2/publish-2.jar')
        def uploadedIvy = dist.testFile('build/repo/org.gradle/publish/2/ivy-2.xml')
        uploadedJar.assertIsCopyOf(dist.testFile('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()
    }

    @Issue("GRADLE-1811")
    @Test
    public void canGenerateTheIvyXmlWithoutPublishing() {
        //this is more like documenting the current behavior.
        //Down the road we should add explicit task to create ivy.xml file

        //given
        dist.testFile("build.gradle") << '''
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
        //when
        executer.withTasks("ivyXml").run()

        //then
        def ivyXml = dist.file('ivy.xml')
        ivyXml.assertIsFile()
    }

    @Test
    public void canPublishToUnauthenticatedHttpRepository() {
        server.start()

        dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
        dist.testFile("build.gradle") << """
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
        def uploadedJar = dist.testFile('uploaded.jar')
        def uploadedIvy = dist.testFile('uploaded.xml')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', uploadedJar, HttpStatus.ORDINAL_200_OK)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', uploadedIvy, HttpStatus.ORDINAL_201_Created)

        executer.withTasks("uploadArchives").run()

        uploadedJar.assertIsCopyOf(dist.testFile('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()
    }

    @Test
    public void canPublishToAuthenticatedHttpRepository() {
        server.start()

        dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
        dist.testFile("build.gradle") << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            credentials {
                username 'user'
                password 'password'
            }
            url "http://localhost:${server.port}"
        }
    }
}
"""

        def uploadedJar = dist.testFile('uploaded.jar')
        def uploadedIvy = dist.testFile('uploaded.xml')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', 'user', 'password', uploadedJar)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', 'user', 'password', uploadedIvy)

        executer.withTasks("uploadArchives").run()

        uploadedJar.assertIsCopyOf(dist.testFile('build/libs/publish-2.jar'))
        uploadedIvy.assertIsFile()
    }

    @Test
    public void reportsFailedPublishToHttpRepository() {
        server.start()
        server.addBroken("/")

        dist.testFile("build.gradle") << """
apply plugin: 'java'
uploadArchives {
    repositories {
        ivy {
            url "http://localhost:${server.port}"
        }
    }
}
"""

        def result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configuration \':archives\'.')
        result.assertThatCause(Matchers.containsString('Received status code 500 from server: broken'))

        server.stop()

        result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configuration \':archives\'.')
        result.assertHasCause('java.net.ConnectException: Connection refused')
    }

        @Test
        public void usesFirstConfiguredPatternForPublication() {
            server.start()

            dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
            dist.testFile("build.gradle") << """
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
            def uploadedJar = dist.testFile('uploaded.jar')
            def uploadedIvy = dist.testFile('uploaded.xml')
            server.expectPut('/primary/publish/publish-2.jar', uploadedJar, HttpStatus.ORDINAL_200_OK)
            server.expectPut('/primary-ivy/publish/ivy-2.xml', uploadedIvy, HttpStatus.ORDINAL_201_Created)

            executer.withTasks("uploadArchives").run()

            uploadedJar.assertIsCopyOf(dist.testFile('build/libs/publish-2.jar'))
            uploadedIvy.assertIsFile()
        }

}
