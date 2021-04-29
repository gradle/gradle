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
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.junit.Assume.assumeTrue

// There are problems loading the native libraries for FS-watching when using TestKit with debug
@NoDebug
@SuppressWarnings('IntegrationTestFixtures')
class GradleRunnerFileSystemWatchingIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id('java')
            }
        """
        assumeTrue("File system watching is enabled by default", gradleVersion >= GradleVersion.version("7.0"))
    }

    @Requires(TestPrecondition.WINDOWS)
    def "disables file system watching on Windows"() {
        when:
        def result = runAssemble()
        then:
        assertFileSystemWatchingDisabled(result)
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "file system watching is enabled on non-Windows OSes"() {
        when:
        def result = runAssemble()
        then:
        assertFileSystemWatchingEnabled(result)
    }

    def "can enable file system watching via '#enableFlag'"() {
        when:
        def result = runAssemble(enableFlag)
        then:
        assertFileSystemWatchingEnabled(result)

        where:
        enableFlag << ["--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}", "-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=true"]
    }

    private BuildResult runAssemble(String... extraArguments) {
        runner("assemble", "-D${StartParameterBuildOptions.VfsVerboseLoggingOption.GRADLE_PROPERTY}=true", *extraArguments).build()
    }

    private static boolean fileSystemWatchingEnabled(String output) {
        output.contains("Virtual file system retains information")
    }

    private static void assertFileSystemWatchingEnabled(BuildResult result) {
        assert fileSystemWatchingEnabled(result.output)
    }

    private static void assertFileSystemWatchingDisabled(BuildResult result) {
        assert !fileSystemWatchingEnabled(result.output)
    }
}
