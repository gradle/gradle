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

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.BuildScanPerformanceTestRunner
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.BuildScanResultsStore
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Category(PerformanceRegressionTest)
class BuildScanPluginPerformanceTest extends Specification {

    private static final int MEDIAN_PERCENTAGES_HIFT = 15

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @AutoCleanup
    @Shared
    def resultStore = new BuildScanResultsStore()

    private static final String WITHOUT_PLUGIN_LABEL = "1) without plugin"
    private static final String WITH_PLUGIN_LABEL = "2) with plugin"

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    CrossBuildPerformanceTestRunner runner

    private int warmupBuilds = 2
    private int measuredBuilds = 7

    void setup() {
        def incomingDir = "../../incoming" // System.getProperty('incomingArtifactDir')
        assert incomingDir: "'incomingArtifactDir' system property is not set"
        def buildStampJsonFile = new File(incomingDir, "buildStamp.json")
        assert buildStampJsonFile.exists()

        def versionJsonData = new JsonSlurper().parse(buildStampJsonFile) as Map<String, ?>
        assert versionJsonData.commitId
        def pluginCommitId = versionJsonData.commitId as String
        runner = new BuildScanPerformanceTestRunner(new BuildExperimentRunner(new GradleSessionProvider(buildContext)), resultStore, pluginCommitId, buildContext) {
            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = tmpDir.testDirectory

            }
        }
    }

    @Unroll
    def "large java project with and without plugin application (#scenario)"() {
        given:
        def sourceProject = "largeJavaProjectWithBuildScanPlugin"
        def jobArgs = ['--continue', '--parallel', '--max-workers=2'] + scenarioArgs
        def opts = ['-Xms2048m', '-Xmx2048m']

        runner.testGroup = "build scan plugin"
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

        then:
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)

        and:
        def shiftedResults = buildShiftedResults(results, WITHOUT_PLUGIN_LABEL)
        if (shiftedResults.significantlyFasterThan(withResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        scenario                       | tasks              | withFailure | scenarioArgs      | buildExperimentListener
        "help"                         | ['help']           | false       | []                | null
        "clean build partially cached" | ['clean', 'build'] | true        | ['--build-cache'] | partiallyBuildCacheClean()
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

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }


    private static BaselineVersion buildShiftedResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        def rawResults = results.buildResult(name)
        def shift = rawResults.totalTime.median.value * MEDIAN_PERCENTAGES_HIFT / 100
        baselineResults.results.addAll(rawResults.collect {
            new MeasuredOperation([start: it.start, end: it.end, totalTime: Amount.valueOf(it.totalTime.value + shift, it.totalTime.units), exception: it.exception])
        })
        return baselineResults
    }

}
