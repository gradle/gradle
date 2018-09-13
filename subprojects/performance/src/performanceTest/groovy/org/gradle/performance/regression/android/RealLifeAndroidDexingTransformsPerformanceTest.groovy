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

package org.gradle.performance.regression.android

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.results.BaselineVersion
import org.gradle.util.GFileUtils
import spock.lang.Unroll

class RealLifeAndroidDexingTransformsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#tasks on #testProject without (android/transform) cache"() {
        given:
        def dexingTransform = "dexing transform"
        def dexingTask = "dexing task"

        runner.testGroup = "Android dexing"
        runner.buildSpec {
            projectName(testProject)
            displayName(dexingTransform)
            warmUpCount warmUpRuns
            invocationCount runs
            listener(cleanTransformsCache())
            invocation {
                defaultInvocation(tasks, memory, delegate)
                args("-Pandroid.enableDexingArtifactTransform=true")
            }
        }

        runner.baseline {
            projectName(testProject)
            displayName(dexingTask)
            warmUpCount warmUpRuns
            invocationCount runs
            listener(cleanTransformsCache())
            invocation {
                defaultInvocation(tasks, memory, delegate)
                args("-Pandroid.enableDexingArtifactTransform=false")
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()
        and:
        def transformResults = result.buildResult(dexingTransform)
        def taskResults = new BaselineVersion("")
        taskResults.with {
            results.name = dexingTask
            results.addAll(result.buildResult(dexingTask))
        }
        def speedStats = taskResults.getSpeedStatsAgainst(dexingTransform, transformResults)
        println(speedStats)
        if (taskResults.significantlySlowerThan(transformResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        testProject         | memory | warmUpRuns | runs | tasks
        'largeAndroidBuild' | '4g'   | 2          | 4    | 'clean phthalic:assembleDebug'
    }

    private BuildExperimentListenerAdapter cleanTransformsCache() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                GFileUtils.deleteDirectory(new File(invocationInfo.gradleUserHome, "caches/transforms-1/files-1.1"))
            }
        }
    }

    void defaultInvocation(String tasks, String memory, GradleInvocationSpec.InvocationBuilder builder) {
        with(builder) {
            tasksToRun(tasks.split(' '))
            cleanTasks("clean")
            gradleOpts("-Xms${memory}", "-Xmx${memory}")
            useDaemon()
            args("-Dorg.gradle.parallel=true", "-Pandroid.enableBuildCache=false", '-Dcom.android.build.gradle.overrideVersionCheck=true')
        }

    }
}
