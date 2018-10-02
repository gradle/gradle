/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance


import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestFile
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceRegressionTest)
class BuildScanPluginPerformanceTest extends AbstractBuildScanPluginPerformanceTest {

    private static final int MEDIAN_PERCENTAGES_SHIFT = 10

    private static final String WITHOUT_PLUGIN_LABEL = "1 without plugin"
    private static final String WITH_PLUGIN_LABEL = "2 with plugin"

    @Unroll
    def "large java project with and without plugin application (#scenario)"() {
        given:
        def sourceProject = "largeJavaProjectWithBuildScanPlugin"
        def jobArgs = ['--continue', '--parallel', '--max-workers=2'] + scenarioArgs
        def opts = ['-Xms4096m', '-Xms4096m']

        runner.testId = "large java project with and without plugin application ($scenario)"
        runner.baseline {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(WITHOUT_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                tasksToRun(*tasks)
                useDaemon()
                gradleOpts(*opts)
                if (withFailure) {
                    expectFailure()
                }
                listener(buildExperimentListener)
            }
        }

        runner.buildSpec {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(WITH_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                args("--scan", "-DenableScan=true", "-Dscan.dump")
                tasksToRun(*tasks)
                useDaemon()
                gradleOpts(*opts)
                if (withFailure) {
                    expectFailure()
                }
                listener(buildExperimentListener)
            }
        }

        when:
        def results = runner.run()

        then:
        def withoutResults = buildBaselineResults(results, WITHOUT_PLUGIN_LABEL)
        def withResults = results.buildResult(WITH_PLUGIN_LABEL)
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)

        def shiftedResults = buildShiftedResults(results, WITHOUT_PLUGIN_LABEL, MEDIAN_PERCENTAGES_SHIFT)
        if (shiftedResults.significantlyFasterThan(withResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        scenario                         | expectedMedianPercentageShift | tasks              | withFailure | scenarioArgs      | buildExperimentListener
        "help"                           | MEDIAN_PERCENTAGES_SHIFT      | ['help']           | false       | []                | null
        "clean build - partially cached" | MEDIAN_PERCENTAGES_SHIFT      | ['clean', 'build'] | true        | ['--build-cache'] | partiallyBuildCacheClean()
    }

    def partiallyBuildCacheClean() {
        return new BuildExperimentListenerAdapter() {
            void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {
                def projectTestDir = new TestFile(projectDir)
                def cacheDir = projectTestDir.file('local-build-cache')
                def settingsFile = projectTestDir.file('settings.gradle')
                settingsFile << """
                    buildCache {
                        local {
                            directory = '${cacheDir.absoluteFile.toURI()}'
                        }
                    }
                """.stripIndent()
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                assert !new File(invocationInfo.projectDir, 'error.log').exists()
                def buildCacheDirectory = new TestFile(invocationInfo.projectDir, 'local-build-cache')
                def cacheEntries = buildCacheDirectory.listFiles().sort()
                cacheEntries.eachWithIndex { TestFile entry, int i ->
                    if (i % 2 == 0) {
                        entry.delete()
                    }
                }
            }
        }
    }

}
