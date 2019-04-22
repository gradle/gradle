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
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.util.GFileUtils
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class RealLifeAndroidDexingTransformsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    private static final String DEXING_TRANSFORM = "dexing transform"
    public static final String DEXING_TASK = "dexing task"

    @Unroll
    def "dexing task vs transform - #tasksString on #testProject #withOrWithout (android/transform) cache"() {
        given:
        def invocationOptions = [tasks: tasksString, memory: memory, enableAndroidBuildCache: enableCaches]

        runner.testGroup = "Android dexing"
        runner.buildSpec {
            projectName(testProject)
            displayName(DEXING_TRANSFORM)
            warmUpCount warmUpRuns
            invocationCount runs
            if (!enableCaches) {
                // We actually want to execute the transforms.
                // So we clean the transform caches before the actual test run since we cannot disable it.
                // When we have some sources we can change, we should do that instead.
                listener(cleanTransformsCacheBeforeInvocation())
            }
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: true, delegate)
            }
        }

        runner.baseline {
            projectName(testProject)
            displayName(DEXING_TASK)
            warmUpCount warmUpRuns
            invocationCount runs
            if (!enableCaches) {
                listener(cleanTransformsCacheBeforeInvocation())
            }
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: false, delegate)
            }
        }

        when:
        def results = runner.run()

        then:
        assertDexingTransformIsFaster(results)

        where:
        testProject         | memory | warmUpRuns | runs | tasksString                    | enableCaches
        'largeAndroidBuild' | '5g'   | 2          | 8    | 'clean assembleDebug'          | true
        'largeAndroidBuild' | '5g'   | 2          | 8    | 'clean assembleDebug'          | false
        'largeAndroidBuild' | '5g'   | 2          | 8    | 'clean phthalic:assembleDebug' | true
        'largeAndroidBuild' | '5g'   | 2          | 8    | 'clean phthalic:assembleDebug' | false
        withOrWithout = enableCaches ? "with" : "without"
    }

    private static BuildExperimentListenerAdapter cleanTransformsCacheBeforeInvocation() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                def transformsCaches = new File(invocationInfo.gradleUserHome, "caches").listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(File dir, String name) {
                        return name.startsWith("transforms-")
                    }
                })
                if (transformsCaches != null) {
                    for (transformsCache in transformsCaches) {
                        GFileUtils.deleteDirectory(transformsCache)
                    }
                }
            }
        }
    }

    void defaultInvocation(Map options, GradleInvocationSpec.InvocationBuilder builder) {
        String memory = options.memory
        String[] tasks = options.tasks.toString().split(' ')
        with(builder) {
            tasksToRun(tasks)
            cleanTasks("clean")
            gradleOpts("-Xms${memory}", "-Xmx${memory}")
            useDaemon()
            args("-Dorg.gradle.parallel=true", "-Pandroid.enableBuildCache=${options.enableAndroidBuildCache ?: true}", "-Pandroid.enableDexingArtifactTransform=${options.dexingTransforms}", "-Pandroid.enableDexingArtifactTransform.desugaring=${options.dexingTransforms}", '-Dcom.android.build.gradle.overrideVersionCheck=true')
        }

    }

    private static void assertDexingTransformIsFaster(CrossBuildPerformanceResults results) {
        def transformResults = getDexingByTransformResults(results)
        def taskResults = getDexingByTaskResult(results)
        def speedStats = taskResults.getSpeedStatsAgainst(DEXING_TRANSFORM, transformResults)
        println(speedStats)
        if (taskResults.significantlyFasterThan(transformResults)) {
            throw new AssertionError(speedStats)
        }

    }

    private static MeasuredOperationList getDexingByTransformResults(CrossBuildPerformanceResults results) {
        results.buildResult(DEXING_TRANSFORM)
    }

    private static BaselineVersion getDexingByTaskResult(CrossBuildPerformanceResults result) {
        def taskResults = new BaselineVersion("")
        taskResults.with {
            results.name = DEXING_TASK
            results.addAll(result.buildResult(DEXING_TASK))
        }
        taskResults
    }
}
