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

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.android.GetModel
import org.gradle.performance.android.SyncAction
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"])
)
class RealLifeAndroidStudioMockupPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "get IDE model for Android Studio"() {
        given:
        def testProject = AndroidTestProject.projectFor(runner.testProject)
        testProject.configure(runner)
        runner.warmUpRuns = 40
        runner.runs = 40
        runner.minimumBaseVersion = "6.5"
        runner.targetVersions = ["7.0-20210127230053+0000"]

        runner.toolingApi("Android Studio Sync") {
            it.action(new GetModel())
        }.run { modelBuilder ->
            SyncAction.withModelBuilder(modelBuilder)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
