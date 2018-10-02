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

package org.gradle.performance

import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class BuildScanPluginInputFileHashesPerformance extends AbstractBuildScanPluginPerformanceTest {

    private static final int MEDIAN_PERCENTAGES_SHIFT = 10
    public static final String DISABLED_PER_FILE_SNAPSHOTS = "per-file input snapshots disabled"
    public static final String ENABLED_PER_FILE_SNAPSHOTS = "per-file input snapshots enabled"

    @Unroll
    def "large java project with per-file input snapshots capturing (#scenario)"() {
        given:
        def sourceProject = "manyInputFilesProject"
        def jobArgs = ['--parallel', '--max-workers=2', '--scan', '-DenableScan=true', '-Dscan.dump']
        def opts = ['-Xms4096m', '-Xms4096m']
        def tasks = ['clean', 'assemble']
        runner.testId = "large java project with per-file input snapshots capturing ($scenario)"
        runner.baseline {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(DISABLED_PER_FILE_SNAPSHOTS)
            invocation {
                args(*jobArgs)
                args()
                tasksToRun(*tasks)
                useDaemon()
                gradleOpts(*opts)
            }
        }

        runner.buildSpec {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(ENABLED_PER_FILE_SNAPSHOTS)
            invocation {
                args(*jobArgs)
                args("-Dcom.gradle.scan.input-file-hashes=true")
                tasksToRun(*tasks)
                useDaemon()
                gradleOpts(*opts)
            }
        }

        when:
        def results = runner.run()

        then:
        def withoutResults = buildBaselineResults(results, DISABLED_PER_FILE_SNAPSHOTS)
        def withResults = results.buildResult(ENABLED_PER_FILE_SNAPSHOTS)
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)

        def shiftedResults = buildShiftedResults(results, DISABLED_PER_FILE_SNAPSHOTS, MEDIAN_PERCENTAGES_SHIFT)
        if (shiftedResults.significantlyFasterThan(withResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        scenario         | expectedMedianPercentageShift
        "clean assemble" | MEDIAN_PERCENTAGES_SHIFT
        "assemble"       | MEDIAN_PERCENTAGES_SHIFT
    }

}
