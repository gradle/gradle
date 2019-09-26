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

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import org.junit.experimental.categories.Category
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.performance.regression.android.AndroidTestProject.K9_ANDROID
import static org.gradle.performance.regression.android.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

class RealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    def setup() {
        runner.args = ['-Dcom.android.build.gradle.overrideVersionCheck=true']
    }

    @Ignore("Re-enable after rebaselining")
    @Unroll
    def "#tasks on #testProject"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args = parallel ? ['-Dorg.gradle.parallel=true'] : []
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.minimumVersion = "5.6.1" // AGP 3.6 requires 5.6.1+
        runner.targetVersions = ["6.0-20190823180744+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject          | parallel | warmUpRuns | runs | tasks
        K9_ANDROID           | false    | null       | null | 'help'
        K9_ANDROID           | false    | null       | null | 'assembleDebug'
//        K9_ANDROID    | false    | null       | null | 'clean k9mail:assembleDebug'
        LARGE_ANDROID_BUILD  | true     | null       | null | 'help'
        LARGE_ANDROID_BUILD  | true     | null       | null | 'assembleDebug'
        LARGE_ANDROID_BUILD  | true     | 2          | 8    | 'clean phthalic:assembleDebug'
        SANTA_TRACKER_KOTLIN | true     | null       | null | 'assembleDebug'
    }

    @Category(SlowPerformanceRegressionTest)
    @Unroll
    def "clean #tasks on #testProject with clean transforms cache"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.warmUpRuns = warmUpRuns
        runner.cleanTasks = ["clean"]
        runner.runs = runs
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["5.7-20190807220120+0000"]
        runner.addBuildMutator { invocationSettings ->
            new ClearArtifactTransformCacheMutator(invocationSettings.getGradleUserHome(), AbstractCleanupMutator.CleanupSchedule.BUILD)
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

    @Unroll
    def "abi change on #testProject"() {
        given:
        testProject.configureForAbiChange(runner)
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["6.0-20190823180744+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER_KOTLIN]
    }

    @Unroll
    def "non-abi change on #testProject"() {
        given:
        testProject.configureForNonAbiChange(runner)
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["6.0-20190823180744+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER_KOTLIN]
    }
}
