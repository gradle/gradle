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
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.fixture.IncrementalTestProject
import org.gradle.performance.fixture.TestProjects

class FileSystemWatchingPerformanceTest extends AbstractCrossVersionPerformanceTest {
    private static final String AGP_TARGET_VERSION = "4.0"

    def setup() {
        runner.minimumBaseVersion = "6.7"
        runner.targetVersions = ["6.8-20201002220441+0000"]
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
