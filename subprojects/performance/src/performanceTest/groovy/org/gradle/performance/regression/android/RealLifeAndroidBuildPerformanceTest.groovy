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

package org.gradle.performance.regression.android

import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.TEST
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["santaTrackerAndroidBuild"])
)
class RealLifeAndroidBuildPerformanceTest extends AbstractRealLifeAndroidBuildPerformanceTest {

    @Unroll
    @RunFor([
        @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["santaTrackerAndroidBuild", "largeAndroidBuild"], iterationMatcher = "run help"),
        @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"], iterationMatcher = "run assembleDebug"),
        @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["largeAndroidBuild"], iterationMatcher = ".*phthalic.*")
    ])
    def "run #tasks"() {
        given:
        AndroidTestProject testProject = androidTestProject
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        applyEnterprisePlugin()

        and:
        if (testProject instanceof IncrementalAndroidTestProject) {
            IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks                          | warmUpRuns | runs
        'help'                         | null       | null
        'assembleDebug'                | null       | null
        'clean phthalic:assembleDebug' | 2          | 8
    }

    def "abi change"() {
        given:
        def testProject = androidTestProject as IncrementalAndroidTestProject
        testProject.configureForAbiChange(runner)
        IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        runner.args.add('-Dorg.gradle.parallel=true')
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "non-abi change"() {
        given:
        def testProject = androidTestProject as IncrementalAndroidTestProject
        testProject.configureForNonAbiChange(runner)
        IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        runner.args.add('-Dorg.gradle.parallel=true')
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
