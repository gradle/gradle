/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@SuppressWarnings('IntegrationTestFixtures')
class GradleRunnerFileSystemWatchingIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id('java')
            }
        """
    }

    @Requires(TestPrecondition.WINDOWS)
    def "disables file system watching on Windows"() {
        when:
        def result = runner("assemble", "-Dorg.gradle.vfs.verbose=true").build()
        then:
        !fileSystemWatchingEnabled(result)
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "file system watching is enabled on non-Windows OSes"() {
        when:
        def result = runner("assemble", "-Dorg.gradle.vfs.verbose=true").build()
        then:
        fileSystemWatchingEnabled(result)
    }

    def "can enable file system watching on Windows via '#enableFlag'"() {
        when:
        def result = runner("assemble", "-Dorg.gradle.vfs.verbose=true", enableFlag).build()
        then:
        fileSystemWatchingEnabled(result)

        where:
        enableFlag << ["--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}", "-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=true"]
    }

    private static boolean fileSystemWatchingEnabled(BuildResult result) {
        result.output.contains("Virtual file system retains information")
    }
}
