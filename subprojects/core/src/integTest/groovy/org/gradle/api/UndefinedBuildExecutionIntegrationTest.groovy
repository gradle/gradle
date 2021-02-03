/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import java.nio.file.Files

class UndefinedBuildExecutionIntegrationTest extends AbstractIntegrationSpec {
    def undefinedBuildDirectory = Files.createTempDirectory("gradle").toFile()

    def setup() {
        assertNoDefinedBuild(testDirectory)
        executer.inDirectory(testDirectory)
        executer.ignoreMissingSettingsFile()
    }

    private static void assertNoDefinedBuild(TestFile testDirectory) {
        testDirectory.file(".gradle").assertDoesNotExist()
        def currentDirectory = testDirectory
        for (; ;) {
            currentDirectory.file(settingsFileName).assertDoesNotExist()
            currentDirectory.file(settingsKotlinFileName).assertDoesNotExist()
            currentDirectory = currentDirectory.parentFile
            if (currentDirectory == null) {
                break
            }
        }
    }

    @Unroll
    def "fails when attempting to execute #task task in undefined build"() {
        when:
        fails(task)

        then:
        file(".gradle").assertDoesNotExist()
        failure.assertHasDescription "Executing Gradle tasks as part of an undefined build is not supported. " +
            "Make sure that you are executing Gradle from a folder within your Gradle project. " +
            "Your project should have a 'settings.gradle(.kts)' file in the root folder."

        where:
        task << [ProjectInternal.HELP_TASK, ProjectInternal.TASKS_TASK]
    }

    def "does not treat buildSrc as undefined build"() {
        given:
        settingsFile.touch()
        file("buildSrc/src/main/groovy/Dummy.groovy") << "class Dummy {}"

        expect:
        succeeds("help") // without deprecation warning
        result.assertTaskExecuted(":buildSrc:jar")
    }

    @Unroll
    def "does not shows deprecation warning when executing #flag in undefined build"() {
        when:
        executer.requireDaemon().requireIsolatedDaemons()
        succeeds(flag)

        then:
        file(".gradle").assertDoesNotExist()

        where:
        flag << ["--version", "--help", "-h", "-?", "--help"]
    }

    @Override
    TestFile getTestDirectory() {
        return new TestFile(undefinedBuildDirectory)
    }
}
