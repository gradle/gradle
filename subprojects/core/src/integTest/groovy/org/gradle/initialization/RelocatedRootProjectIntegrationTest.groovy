/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/38623")
class RelocatedRootProjectIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        settingsFile << """
            rootProject.name = 'foo'
            rootProject.projectDir = file('bar')
            gradle.rootProject {
                println("root project dir: " + projectDir)
            }
        """
        file("bar/build.gradle") << """
            def projectDir = project.projectDir
            tasks.register('hello') {
                doLast { println("hello from " + projectDir) }
            }
        """
    }

    def "runs from the build root directory"() {
        when:
        succeeds("hello")

        then:
        outputContains("root project dir: ${file("bar")}")
        outputContains("hello from ${file("bar")}")
    }

    def "runs with the build root directory given explicitly"() {
        when:
        executer.withArgument("-p").withArgument(testDirectory.absolutePath)
        succeeds("hello")

        then:
        outputContains("root project dir: ${file("bar")}")
        outputContains("hello from ${file("bar")}")
    }

    def "runs with the build root directory given explicitly and the build cache enabled"() {
        when:
        executer.withBuildCacheEnabled()
        executer.withArgument("-p").withArgument(testDirectory.absolutePath)
        succeeds("hello")

        then:
        outputContains("root project dir: ${file("bar")}")
        outputContains("hello from ${file("bar")}")
    }

    def "runs from the relocated project directory"() {
        when:
        executer.inDirectory(file("bar"))
        succeeds("hello")

        then:
        outputContains("hello from ${file("bar")}")
    }
}
