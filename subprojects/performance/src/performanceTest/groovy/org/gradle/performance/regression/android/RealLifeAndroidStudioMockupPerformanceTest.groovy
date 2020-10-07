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
import org.gradle.performance.fixture.AndroidTestProject

class RealLifeAndroidStudioMockupPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "get IDE model for Android Studio"() {
        given:
        def testProject = AndroidTestProject.projectFor(runner.testProject)
        testProject.configure(runner)
        int iterations = (testProject == AndroidTestProject.K9_ANDROID) ? 200 : 40
        runner.warmUpRuns = iterations
        runner.runs = iterations
        runner.minimumBaseVersion = "5.4.1"
        runner.targetVersions = ["6.8-20201005220043+0000"]

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
