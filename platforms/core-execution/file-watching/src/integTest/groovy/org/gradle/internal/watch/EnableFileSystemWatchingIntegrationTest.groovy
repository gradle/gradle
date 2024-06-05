/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch

import com.gradle.develocity.testing.annotations.LocalOnly
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.test.fixtures.file.TempFileSystemProvider
import org.junit.Rule

@LocalOnly
class EnableFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    private static final String ENABLED_MESSAGE = "Watching the file system is configured to be enabled"
    private static final String ENABLED_IF_AVAILABLE_MESSAGE = "Watching the file system is configured to be enabled if available"
    private static final String DISABLED_MESSAGE = "Watching the file system is configured to be disabled"

    private static final String ACTIVE_MESSAGE = "File system watching is active"
    private static final String INACTIVE_MESSAGE = "File system watching is inactive"

    @Rule
    TempFileSystemProvider testFileSystemProvider = new TempFileSystemProvider(temporaryFolder)

    def "is enabled by default"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", "--info")
        then:
        outputContains(ENABLED_IF_AVAILABLE_MESSAGE)
        outputContains(ACTIVE_MESSAGE)
    }

    def "can be enabled via gradle.properties (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        file("gradle.properties") << "${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=${enabled}"
        run("assemble", "--info")
        then:
        outputContains(expectedEnabledMessage)
        outputContains(expectedActiveMessage)

        where:
        enabled | expectedEnabledMessage | expectedActiveMessage
        true    | ENABLED_MESSAGE        | ACTIVE_MESSAGE
        false   | DISABLED_MESSAGE       | INACTIVE_MESSAGE
    }

    def "can be enabled via system property (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", "-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=${enabled}", "--info")
        then:
        outputContains(expectedEnabledMessage)
        outputContains(expectedActiveMessage)

        where:
        enabled | expectedEnabledMessage | expectedActiveMessage
        true    | ENABLED_MESSAGE        | ACTIVE_MESSAGE
        false   | DISABLED_MESSAGE       | INACTIVE_MESSAGE
    }

    def "can be enabled via #commandLineOption"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", commandLineOption, "--info")
        then:
        outputContains(expectedEnabledMessage)
        outputContains(expectedActiveMessage)

        where:
        commandLineOption | expectedEnabledMessage | expectedActiveMessage
        "--watch-fs"      | ENABLED_MESSAGE        | ACTIVE_MESSAGE
        "--no-watch-fs"   | DISABLED_MESSAGE       | INACTIVE_MESSAGE
    }

    def "setting to #watchMode via command-line init script has no effect"() {
        buildFile << """
            apply plugin: "java"
        """

        def initScript = file("init.gradle") << """
            gradle.startParameter.setWatchFileSystemMode(${WatchMode.name}.${watchMode.name()})
        """

        when:
        run("assemble", "--info", "--init-script", initScript.absolutePath)
        then:
        outputContains(ENABLED_IF_AVAILABLE_MESSAGE)
        outputContains(ACTIVE_MESSAGE)

        where:
        watchMode << WatchMode.values()
    }

    def "setting to #watchMode via init script in user home has no effect"() {
        buildFile << """
            apply plugin: "java"
        """

        requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file("init.d/fsw.gradle") << """
            gradle.startParameter.setWatchFileSystemMode(${WatchMode.name}.${watchMode.name()})
        """

        when:
        run("assemble", "--info")
        then:
        outputContains(ENABLED_IF_AVAILABLE_MESSAGE)
        outputContains(ACTIVE_MESSAGE)

        where:
        watchMode << WatchMode.values()
    }

    def "fails when a custom build scope cache dir is defined, watching is explicitly enabled and cache dir is on another fs"() {
        given:
        def testFileSystem = testFileSystemProvider.create()
        buildFile << ""
        file("buildSrc/build.gradle") << ""

        when:
        fails("help", "--watch-fs", "--project-cache-dir=${testFileSystem.root}")

        then:
        failure.assertHasDescription("Enabling file system watching via --watch-fs (or via the org.gradle.vfs.watch property) with --project-cache-dir located on another filesystem is not supported; remove either option to fix this problem")
    }

    def "can define a custom build scope cache dir when watching is explicitly enabled if they are on the same fs"() {
        given:
        def testFileSystem = testFileSystemProvider.create()

        def project = testFileSystem.file("project").createDir()
        project.file(defaultBuildFileName) << ""
        file("$project/buildSrc/build.gradle") << ""

        when:
        executer.inDirectory(project)
        def cacheDir = file("cache-dir")
        if (cacheDirExists) {
            cacheDir.createDir()
        }

        then:
        succeeds("help", "--watch-fs", "--project-cache-dir=cache-dir")
        outputDoesNotContain("File system watching is disabled")

        where:
        cacheDirExists << [true, false]
    }
}
