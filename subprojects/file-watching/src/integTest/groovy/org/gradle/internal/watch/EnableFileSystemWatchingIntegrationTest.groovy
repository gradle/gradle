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

class EnableFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    private static final String INCUBATING_MESSAGE = "Watching the file system is an incubating feature"

    def "incubating message is shown for watching the file system"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        withWatchFs().run("assemble")
        then:
        outputContains(INCUBATING_MESSAGE)

        when:
        withoutWatchFs().run("assemble")
        then:
        outputDoesNotContain(INCUBATING_MESSAGE)
    }

    def "can be enabled via gradle.properties"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        file("gradle.properties") << "${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=true"
        run("assemble")
        then:
        outputContains(INCUBATING_MESSAGE)
    }

    def "can be enabled via #commandLineOption"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", commandLineOption)
        then:
        outputContains(INCUBATING_MESSAGE)

        where:
        commandLineOption << ["-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=true", "--watch-fs"]
    }

    def "deprecation message is shown when using the old property to enable watching the file system"() {
        buildFile << """
            apply plugin: "java"
        """
        executer.expectDocumentedDeprecationWarning(
                "Using the system property org.gradle.unsafe.vfs.retention to enable watching the file system has been deprecated. " +
                        "This is scheduled to be removed in Gradle 7.0. " +
                        "Use the gradle property org.gradle.unsafe.watch-fs instead. " +
                        "See https://docs.gradle.org/current/userguide/gradle_daemon.html for more details."
        )

        expect:
        succeeds("assemble", "-D${VirtualFileSystemServices.DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY}=true")
    }
}
