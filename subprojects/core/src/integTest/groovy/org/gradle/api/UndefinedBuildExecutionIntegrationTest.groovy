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

import org.gradle.cache.internal.BuildScopeCacheDir
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class UndefinedBuildExecutionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        executer.requireOwnGradleUserHomeDir()
    }

    def "fails when attempting to execute tasks #tasks in directory with no settings or build file"() {
        when:
        fails(*tasks)

        then:
        failure.assertHasDescription("Directory '$testDirectory' does not contain a Gradle build.")
        failure.assertHasResolutions(
            "Run gradle init to create a new Gradle build in this directory.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            GET_HELP) // Don't suggest running with --scan for a missing build

        testDirectory.assertIsEmptyDir()
        assertNoProjectCaches(executer.gradleUserHomeDir)

        where:
        tasks << [["tasks"], ["unknown"]]
    }

    // Documents existing behaviour, not desired behaviour
    def "allows an included build with no settings or build file"() {
        given:
        settingsFile << """
            includeBuild("empty")
            includeBuild("lib")
        """
        def dir = file("empty")
        dir.createDir()
        file("lib/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "lib"
        """
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies { implementation "lib:lib:1.2" }
        """

        when:
        succeeds("build")

        then:
        noExceptionThrown()
    }

    def "fails when target of GradleBuild task has no settings or build file"() {
        given:
        buildFile << """
            task build(type: GradleBuild) {
                dir = 'empty'
                tasks = ['tasks']
            }
        """
        def dir = file("empty")
        dir.createDir()

        when:
        fails("build")

        then:
        assertNoProjectCaches(dir)
        failure.assertHasDescription("Execution failed for task ':build'.")
        failure.assertHasCause("Directory '$dir' does not contain a Gradle build.")
    }

    private void assertNoProjectCaches(TestFile dir) {
        def dirContents = dir.list()
        if (dirContents) {
            printFileTree(dir)
        }
        // caches: Gradle user home caches, not present in project .gradle caches directory
        // native: unpacked native platform libraries, not present in project .gradle caches directory
        // .tmp: Temporary folder for worker classpath files and configuration caching report intermediate files, not present in project .gradle caches directory
        def filteredContents = (dirContents?.findAll { !(it in ["caches", "native", ".tmp"]) })
        assert !filteredContents

        def tmpDir = dir.file(".tmp")
        if (tmpDir.exists()) {
            tmpDir.assertIsEmptyDir()
        }
    }

    private void printFileTree(File dir) {
        def list = []
        if (dir.exists()) {
            dir.eachFileRecurse { file ->
                list << file
            }
        }

        println "Contents of $dir.absolutePath:"
        list.each {
            println it.path
        }
    }

    def "fails when user home directory is used and Gradle has not been run before"() {
        when:
        // the default, if running from user home dir
        def gradleUserHomeDir = file(".gradle")
        executer.withGradleUserHomeDir(gradleUserHomeDir)
        fails("tasks")

        then:
        failure.assertHasDescription("Directory '$testDirectory' does not contain a Gradle build.")

        def createdDirectories = testDirectory.list() as List
        createdDirectories.empty || createdDirectories == [".gradle"]
        assertNoProjectCaches(gradleUserHomeDir)
    }

    def "does not delete an existing .gradle directory"() {
        given:
        def textFile = file(".gradle/thing.txt")
        textFile << "content"

        when:
        fails("tasks")

        then:
        testDirectory.assertHasDescendants(".gradle/thing.txt")
        textFile.assertIsFile()
        textFile.text == "content"
    }

    def "does not treat build as undefined when root #fileName is present but settings file is not"() {
        when:
        file(fileName) << """
            tasks.register("build")
        """
        succeeds("build")

        then:
        noExceptionThrown()

        where:
        fileName << ["build.gradle", "build.gradle.kts"]
    }

    def "does not treat build as undefined when root build file is not present but #fileName is"() {
        when:
        settingsFile << """
            include("child")
        """
        file("child/build.gradle") << """
            task build
        """
        succeeds("tasks")

        then:
        noExceptionThrown()

        where:
        fileName << ["settings.gradle", "settings.gradle.kts"]
    }

    def "does not treat buildSrc with no settings file as undefined build"() {
        given:
        settingsFile.touch()
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }
        """
        file("buildSrc/src/main/groovy/Dummy.groovy") << "class Dummy {}"

        when:
        succeeds("tasks") // without deprecation warning
        then:
        result.assertTaskExecuted(":buildSrc:jar")
        file("buildSrc/.gradle").assertIsDir()

        when:
        executer.usingProjectDirectory(file("buildSrc"))
        then:
        succeeds("jar")
        file("buildSrc/.gradle").assertIsDir()
        executer.gradleUserHomeDir.file(BuildScopeCacheDir.UNDEFINED_BUILD).assertDoesNotExist()
    }

    def "treats empty buildSrc as undefined build"() {
        given:
        file("buildSrc").createDir()

        expect:
        executer.usingProjectDirectory(file("buildSrc"))
        fails("tasks")

        file("buildSrc").assertIsEmptyDir()
        assertNoProjectCaches(executer.gradleUserHomeDir)
    }

    def "treats empty buildSrc inside a build as undefined build"() {
        given:
        settingsFile.touch()
        file("buildSrc").createDir()

        expect:
        succeeds("tasks")

        executer.usingProjectDirectory(file("buildSrc"))
        fails("tasks")

        file("buildSrc").assertIsEmptyDir()
        executer.gradleUserHomeDir.file(BuildScopeCacheDir.UNDEFINED_BUILD).assertDoesNotExist()
    }

    def "does not fail when executing #flag in undefined build"() {
        when:
        executer.requireDaemon().requireIsolatedDaemons()
        succeeds(flag)

        then:
        testDirectory.assertIsEmptyDir()

        where:
        flag << ["--version", "--help", "-h", "-?", "--help"]
    }
}
