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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import spock.lang.Unroll

class EnableFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    private static final String ENABLED_MESSAGE = "Watching the file system is enabled"
    private static final String DISABLED_MESSAGE = "Watching the file system is disabled"

    @Unroll
    def "can be enabled via gradle.properties (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        file("gradle.properties") << "${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=${enabled}"
        run("assemble", "--info")
        then:
        outputContains(expectedMessage)

        where:
        enabled | expectedMessage
        true    | ENABLED_MESSAGE
        false   | DISABLED_MESSAGE
    }

    @Unroll
    def "can be enabled via system property (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", "-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=${enabled}", "--info")
        then:
        outputContains(expectedMessage)

        where:
        enabled | expectedMessage
        true    | ENABLED_MESSAGE
        false   | DISABLED_MESSAGE
    }

    @Unroll
    def "can be enabled via #commandLineOption"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", commandLineOption, "--info")
        then:
        outputContains(expectedMessage)

        where:
        commandLineOption | expectedMessage
        "--watch-fs"      | ENABLED_MESSAGE
        "--no-watch-fs"   | DISABLED_MESSAGE
    }

    @Unroll
    @SuppressWarnings('GrDeprecatedAPIUsage')
    def "deprecation message is shown when using the old property to enable watching the file system (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.unsafe.watch-fs system property has been deprecated. " +
                "This is scheduled to be removed in Gradle 7.0. " +
                "Please use the org.gradle.vfs.watch system property instead. " +
                "See https://docs.gradle.org/current/userguide/gradle_daemon.html for more details."
        )

        expect:
        succeeds("assemble", "-D${StartParameterBuildOptions.DeprecatedWatchFileSystemOption.GRADLE_PROPERTY}=${enabled}")

        where:
        enabled << [true, false]
    }

    @Unroll
    @SuppressWarnings('GrDeprecatedAPIUsage')
    def "deprecation message is shown when using old VFS retention property to enable watching the file system (enabled: #enabled)"() {
        buildFile << """
            apply plugin: "java"
        """
        executer.expectDocumentedDeprecationWarning(
            "Using the system property org.gradle.unsafe.vfs.retention to enable watching the file system has been deprecated. " +
                "This is scheduled to be removed in Gradle 7.0. " +
                "Use the gradle property org.gradle.vfs.watch instead. " +
                "See https://docs.gradle.org/current/userguide/gradle_daemon.html for more details."
        )

        expect:
        succeeds("assemble", "-D${VirtualFileSystemServices.DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY}=${enabled}")

        where:
        enabled << [true, false]
    }

    @Unroll
    @SuppressWarnings('GrDeprecatedAPIUsage')
    def "deprecation message is shown when using the old property to drop the VFS (drop: #drop)"() {
        executer.expectDeprecationWarning(
            "The org.gradle.unsafe.vfs.drop system property has been deprecated. " +
                "This is scheduled to be removed in Gradle 7.0. " +
                "Please use the org.gradle.vfs.drop system property instead."
        )

        expect:
        succeeds("help", "--watch-fs", "-D${VirtualFileSystemServices.DEPRECATED_VFS_DROP_PROPERTY}=${drop}")

        where:
        drop << [true, false]
    }
}
