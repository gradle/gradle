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

import org.gradle.testdistribution.LocalOnly
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.testkit.runner.fixtures.NoDebug

import static org.junit.Assume.assumeTrue

@SuppressWarnings('IntegrationTestFixtures')
@LocalOnly
class GradleRunnerFileSystemWatchingIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id('java')
            }
        """
        assumeTrue("File system watching is enabled by default", isCompatibleVersion("7.0"))
    }

    @NoDebug
    @Requires(UnitTestPreconditions.Windows)
    def "disables file system watching on Windows"() {
        when:
        def result = runAssemble()
        then:
        assertFileSystemWatchingDisabled(result)
    }

    @NoDebug
    @Requires(UnitTestPreconditions.NotWindows)
    def "file system watching is enabled on non-Windows OSes"() {
        when:
        def result = runAssemble()
        then:
        assertFileSystemWatchingEnabled(result)
    }

    @NoDebug
    def "can enable file system watching via '#enableFlag'"() {
        when:
        def result = runAssemble(enableFlag)
        then:
        assertFileSystemWatchingEnabled(result)

        where:
        enableFlag << ["--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}", "-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=true"]
    }

    @Debug
    def "file system watching is disabled when using --debug"() {
        assumeTrue("Do not initialize file system watching in client", isCompatibleVersion("7.2"))

        when:
        def result = runAssemble(*extraArguments)
        then:
        assertFileSystemWatchingDisabled(result)
        println(result.output)

        where:
        extraArguments << [[], ["--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}"]]
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
