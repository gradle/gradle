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
        executer.inDirectory(testDirectory)
        assertNoDefinedBuild(testDirectory)
        executer.ignoreMissingSettingsFile()
    }

    private static void assertNoDefinedBuild(TestFile testDirectory) {
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
    def "shows deprecation warning when executing #task task in undefined build"() {
        expect:
        executer.expectDocumentedDeprecationWarning("Executing Gradle tasks as part of an undefined build has been deprecated. This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#executing_gradle_without_a_settings_file_has_been_deprecated")
        succeeds(task) // should fail for all tasks in Gradle 7.0
        // In Gradle 7.0, we should test the .gradle folder is not created

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
