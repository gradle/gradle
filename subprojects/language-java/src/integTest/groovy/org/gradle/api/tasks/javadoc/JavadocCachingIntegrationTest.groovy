/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile

class JavadocCachingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    def setup() {
        setupProjectInDirectory(temporaryFolder.testDirectory)
    }
    def setupProjectInDirectory(TestFile projectDir) {
        projectDir.file('settings.gradle') << localCacheConfiguration()
        projectDir.file('settings.gradle') << "rootProject.name = 'test-project'"
        projectDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-api', version: '2.17.1'
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.17.1'
            }
        """
        projectDir.file("src/main/java/Foo.java") << """
            /**
             * This is Javadoc.
             */
            public class Foo {
            }
        """
    }

    def 'javadoc can be cached'() {
        when:
        withBuildCache().run "javadoc"

        then:
        taskIsNotCached()

        when:
        withBuildCache().succeeds 'clean', "javadoc"

        then:
        taskIsCached()
    }

    def "javadoc is cached if the build executed from a different directory"() {
        // Compile in a different copy of the project
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir)

        when:
        executer.inDirectory(remoteProjectDir)
        withBuildCache().run "javadoc"
        then:
        taskIsNotCached()
        remoteProjectDir.file("build/docs/javadoc/index.html").assertExists()

        // Remove the project completely
        remoteProjectDir.deleteDir()

        when:
        // Move the dependencies around by using a new Gradle user home
        executer.requireOwnGradleUserHomeDir()
        withBuildCache().run "javadoc"
        then:
        taskIsCached()
    }

    void taskIsCached() {
        result.assertTaskSkipped(":javadoc")
        file("build/docs/javadoc/index.html").assertExists()
    }

    void taskIsNotCached() {
        result.assertTaskNotSkipped(":javadoc")
    }
}
