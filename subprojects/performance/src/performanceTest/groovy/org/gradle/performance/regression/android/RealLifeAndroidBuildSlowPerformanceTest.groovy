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


import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import org.junit.experimental.categories.Category
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.performance.regression.android.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

@Category(SlowPerformanceRegressionTest)
class RealLifeAndroidBuildSlowPerformanceTest extends AbstractRealLifeAndroidBuildPerformanceTest {

    @Unroll
    @Ignore('https://github.com/gradle/gradle-private/issues/3113')
    def "clean #tasks on #testProject with clean transforms cache"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.warmUpRuns = warmUpRuns
        runner.cleanTasks = ["clean"]
        runner.runs = runs
        runner.addBuildMutator { invocationSettings ->
            new ClearArtifactTransformCacheMutator(invocationSettings.getGradleUserHome(), AbstractCleanupMutator.CleanupSchedule.BUILD)
        }
        applyEnterprisePlugin()

        and:
        if (testProject == SANTA_TRACKER_KOTLIN) {
            (testProject as IncrementalAndroidTestProject).configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject          | warmUpRuns | runs | tasks
        LARGE_ANDROID_BUILD  | 2          | 8    | 'phthalic:assembleDebug'
        LARGE_ANDROID_BUILD  | 2          | 8    | 'assembleDebug'
        SANTA_TRACKER_KOTLIN | null       | null | 'assembleDebug'
    }
}
