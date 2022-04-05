/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.profiler.mutations.ApplyChangeToNativeSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor([
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["bigCppMulti"]),
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["bigCppApp"])
])
class NativeBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    def setup() {
        runner.minimumBaseVersion = '4.1' // minimum version that contains new C++ plugins
        runner.targetVersions = ["7.4.1-20220211002507+0000"]
    }

    def "up-to-date assemble (native)"() {
        given:
        runner.tasksToRun = ["assemble"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "assemble with #changeType file change"() {
        given:
        runner.tasksToRun = ["assemble"]
        runner.addBuildMutator { settings ->
            new ApplyChangeToNativeSourceFileMutator(new File(settings.getProjectDir(), determineFileToChange(changeType, runner.testProject)))
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        changeType << ['header', 'source']
    }

    static String determineFileToChange(String changeType, String testProject) {
        if (changeType == 'header') {
            switch (testProject) {
                case 'bigCppApp':
                    return 'src/main/headers/lib250.h'
                case 'bigCppMulti':
                    return 'project101/src/main/public/project101lib4.h'
                default:
                    throw new IllegalArgumentException("Unknown test project " + testProject)
            }
        } else {
            switch (testProject) {
                case 'bigCppApp':
                    return 'src/main/cpp/lib250.cpp'
                case 'bigCppMulti':
                    return 'project101/src/main/cpp/project101lib4.cpp'
                default:
                    throw new IllegalArgumentException("Unknown test project " + testProject)
            }
        }
    }
}
