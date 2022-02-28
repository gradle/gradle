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

import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"])
)
class RealLifeAndroidStudioPerformanceTest extends AbstractCrossVersionPerformanceTest {

    /**
     * To run this test locally you should have Android Studio installed in /Applications/Android Studio.*.app folder
     * or you should set "studioHome" system property with the Android Studio installation path.
     */
    def "run Android Studio sync"() {
        given:
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        def testProject = AndroidTestProject.projectFor(runner.testProject)
        testProject.configure(runner)
        AndroidTestProject.useStableAgpVersion(runner)
        runner.warmUpRuns = 40
        runner.runs = 40
        runner.minimumBaseVersion = "6.5"
        runner.targetVersions = ["7.5-20220215231205+0000"]
        runner.setupAndroidStudioSync()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
