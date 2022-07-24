/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

class NativeCleanBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    def setup() {
        runner.minimumBaseVersion = '4.1' // minimum version that contains new C++ plugins
    }

    @RunFor([
        @Scenario(type = PER_DAY, operatingSystems = [LINUX],
            testProjects =  [
                'smallNative',
                'mediumNative',
                'bigNative',
                'multiNative',
                'smallCppApp',
                'mediumCppApp',
                'mediumCppAppWithMacroIncludes',
                'bigCppApp',
                'smallCppMulti',
                'mediumCppMulti',
                'mediumCppMultiWithMacroIncludes',
                'bigCppMulti'
            ])
    ])
    def "clean assemble (native)"() {
        given:
        def iterations = runner.testProject in ['smallNative', 'smallCppApp', 'smallCppMulti'] ? 40 : null
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
        runner.runs = iterations
        runner.warmUpRuns = iterations

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor([
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects =  ['manyProjectsNative'])
    ])
    def "clean assemble (native, parallel)"() {
        given:
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
        runner.args = ["--parallel", "--max-workers=12"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
