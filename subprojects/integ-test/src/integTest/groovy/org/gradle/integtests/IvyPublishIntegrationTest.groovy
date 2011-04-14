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
package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test

public class IvyPublishIntegrationTest {
    @Rule
    public final GradleDistribution dist = new GradleDistribution()
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule
    public final HttpServer server = new HttpServer()

    @Test
    public void canPublishUsingAnonymousHttp() {
        server.start()

        dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
        dist.testFile("build.gradle") << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            name = 'gradleReleases'
            artifactPattern "http://localhost:${server.port}/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        }
    }
}
"""
        def uploadedJar = dist.testFile('uploaded.jar')
        def uploadedIvy = dist.testFile('uploaded.xml')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', uploadedJar)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', uploadedIvy)

        executer.withTasks("uploadArchives").run()

        uploadedJar.assertIsFile()
        uploadedIvy.assertIsFile()
    }

    @Test
    public void canPublishUsingAuthenticatedHttp() {
        server.start()

        dist.testFile("settings.gradle").text = 'rootProject.name = "publish"'
        dist.testFile("build.gradle") << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            name = 'gradleReleases'
            userName = 'user'
            password = 'password'
            realm = 'test'
            artifactPattern "http://localhost:${server.port}/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        }
    }
}
"""

        def uploadedJar = dist.testFile('uploaded.jar')
        def uploadedIvy = dist.testFile('uploaded.xml')
        server.expectPut('/org.gradle/publish/2/publish-2.jar', 'user', 'password', uploadedJar)
        server.expectPut('/org.gradle/publish/2/ivy-2.xml', 'user', 'password', uploadedIvy)

        executer.withTasks("uploadArchives").run()

        uploadedJar.assertIsFile()
        uploadedIvy.assertIsFile()
    }

    @Test
    public void reportsFailedHttpPublish() {
        server.start()

        dist.testFile("build.gradle") << """
apply plugin: 'java'
uploadArchives {
    repositories {
        ivy {
            name = 'gradleReleases'
            artifactPattern "http://localhost:${server.port}/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        }
    }
}
"""

        def result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configurations [configuration \':archives\'].')
        result.assertThatCause(Matchers.containsString('Received status code 404 from server: Not Found'))

        server.stop()

        result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configurations [configuration \':archives\'].')
        result.assertHasCause('java.net.ConnectException: Connection refused')
    }
}
