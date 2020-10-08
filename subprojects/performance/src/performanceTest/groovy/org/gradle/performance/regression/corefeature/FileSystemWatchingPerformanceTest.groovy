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
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.fixture.IncrementalTestProject
import org.gradle.performance.fixture.TestProjects
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles("The TAPI keeps handles to the distribution it starts open in the test JVM")
class FileSystemWatchingPerformanceTest extends AbstractCrossVersionPerformanceTest {
    private static final String AGP_TARGET_VERSION = "4.0"

    def setup() {
        runner.minimumBaseVersion = "6.7"
        runner.targetVersions = ["6.8-20201007220043+0000"]
        runner.useToolingApi = true
        if (OperatingSystem.current().windows) {
            // Reduce the number of iterations on Windows, since the test takes 3 times as long (10s vs 3s).
            runner.warmUpRuns = 5
            runner.runs = 20
        } else {
            runner.warmUpRuns = 10
            runner.runs = 40
        }
    }

    def "assemble for non-abi change with file system watching"() {
        IncrementalTestProject testProject = findTestProjectAndSetupRunnerForFsWatching()
        testProject.configureForNonAbiChange(runner)

        when:
        def result = runner.run()
        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "assemble for abi change with file system watching"() {
        IncrementalTestProject testProject = findTestProjectAndSetupRunnerForFsWatching()

        testProject.configureForAbiChange(runner)

        when:
        def result = runner.run()
        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    IncrementalTestProject findTestProjectAndSetupRunnerForFsWatching() {
        IncrementalTestProject testProject = TestProjects.projectFor(runner.testProject)
        if (testProject instanceof IncrementalAndroidTestProject) {
            IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(runner, AGP_TARGET_VERSION)
        }
        runner.args.add("--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}")
        testProject
    }
}
