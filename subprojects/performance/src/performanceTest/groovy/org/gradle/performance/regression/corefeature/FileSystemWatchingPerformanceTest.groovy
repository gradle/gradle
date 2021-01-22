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

package org.gradle.performance.regression.corefeature

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.fixture.IncrementalTestProject
import org.gradle.performance.fixture.TestProjects
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@Unroll
@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS, MAC_OS], testProjects = ["santaTrackerAndroidBuild", "largeJavaMultiProject"])
)
@LeaksFileHandles("The TAPI keeps handles to the distribution it starts open in the test JVM")
class FileSystemWatchingPerformanceTest extends AbstractCrossVersionPerformanceTest {
    private static final String AGP_TARGET_VERSION = "4.2"
    private static final String KOTLIN_TARGET_VERSION = new KotlinGradlePluginVersions().latests.last()

    def setup() {
        runner.minimumBaseVersion = "6.7"
        runner.targetVersions = ["7.0-20210122131800+0000"]
        runner.useToolingApi = true
        runner.args = ["--no-build-cache", "--no-scan"]
        if (OperatingSystem.current().windows) {
            // Reduce the number of iterations on Windows, since the test takes 3 times as long (10s vs 3s).
            runner.warmUpRuns = 5
            runner.runs = 20
        } else {
            runner.warmUpRuns = 10
            runner.runs = 40
        }
    }

    def "assemble for non-abi change with file system watching#configurationCaching"() {
        IncrementalTestProject testProject = findTestProjectAndSetupRunnerForFsWatching(configurationCachingEnabled)
        testProject.configureForNonAbiChange(runner)

        when:
        def result = runner.run()
        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingEnabled ? " and configuration caching" : ""
    }

    def "assemble for abi change with file system watching#configurationCaching"() {
        IncrementalTestProject testProject = findTestProjectAndSetupRunnerForFsWatching(configurationCachingEnabled)

        testProject.configureForAbiChange(runner)

        when:
        def result = runner.run()
        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingEnabled ? " and configuration caching" : ""
    }

    IncrementalTestProject findTestProjectAndSetupRunnerForFsWatching(boolean enableConfigurationCaching) {
        IncrementalTestProject testProject = TestProjects.projectFor(runner.testProject)
        if (testProject instanceof IncrementalAndroidTestProject) {
            IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(runner, AGP_TARGET_VERSION)
            runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheProblemsOption.PROPERTY_NAME}=warn")
            runner.args.add("-DkotlinVersion=${KOTLIN_TARGET_VERSION}")
        }
        runner.args.add("--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}")
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME}=${enableConfigurationCaching}")
        testProject
    }
}
