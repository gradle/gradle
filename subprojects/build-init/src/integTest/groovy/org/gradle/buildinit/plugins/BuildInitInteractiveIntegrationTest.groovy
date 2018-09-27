/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.TextUtil

class BuildInitInteractiveIntegrationTest extends AbstractInitIntegrationSpec {

    String projectTypePrompt = "Select type of project to generate:"
    String dslPrompt = "Select build script DSL:"
    String basicType = "1: basic"
    String projectNamePrompt = "Project name (default: some-thing)"
    String convertMavenBuildPrompt = "Found a Maven build. Generate a Gradle build from this?"

    def "prompts user when run from an interactive session"() {
        when:
        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks("init")
        def handle = executer.start()

        // Select 'basic'
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains(projectTypePrompt)
            assert handle.standardOutput.contains(basicType)
            assert !handle.standardOutput.contains("pom")
        }
        handle.stdinPipe.write(("1" + TextUtil.platformLineSeparator).bytes)

        // Select 'kotlin'
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains(dslPrompt)
        }
        handle.stdinPipe.write(("2" + TextUtil.platformLineSeparator).bytes)

        // Select default project name
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains(projectNamePrompt)
        }
        handle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        handle.stdinPipe.close()
        handle.waitForFinish()

        then:
        dslFixtureFor(BuildInitDsl.KOTLIN).assertGradleFilesGenerated()
    }

    def "prompts user when run from an interactive session and pom.xml present"() {
        when:
        pom()

        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks("init")
        def handle = executer.start()

        // Select 'yes'
        ConcurrentTestUtil.poll(20) {
            assert handle.standardOutput.contains(convertMavenBuildPrompt)
        }
        handle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        handle.stdinPipe.close()
        handle.waitForFinish()

        !handle.standardOutput.contains(projectTypePrompt)
        !handle.standardOutput.contains(dslPrompt)
        !handle.standardOutput.contains(projectNamePrompt)

        then:
        dslFixtureFor(BuildInitDsl.GROOVY).assertGradleFilesGenerated()
    }

    def "user can skip Maven conversion when pom.xml present"() {
        when:
        pom()

        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks("init")
        def handle = executer.start()

        // Select 'no'
        ConcurrentTestUtil.poll(20) {
            assert handle.standardOutput.contains(convertMavenBuildPrompt)
        }
        handle.stdinPipe.write(("no" + TextUtil.platformLineSeparator).bytes)

        // Select 'basic'
        ConcurrentTestUtil.poll(20) {
            assert handle.standardOutput.contains(projectTypePrompt)
            assert handle.standardOutput.contains(basicType)
            assert !handle.standardOutput.contains("pom")
        }
        handle.stdinPipe.write(("1" + TextUtil.platformLineSeparator).bytes)

        // Select 'kotlin'
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains(dslPrompt)
        }
        handle.stdinPipe.write(("2" + TextUtil.platformLineSeparator).bytes)

        // Select default project name
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains(projectNamePrompt)
        }
        handle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        handle.stdinPipe.close()
        handle.waitForFinish()

        then:
        dslFixtureFor(BuildInitDsl.KOTLIN).assertGradleFilesGenerated()
    }
}
