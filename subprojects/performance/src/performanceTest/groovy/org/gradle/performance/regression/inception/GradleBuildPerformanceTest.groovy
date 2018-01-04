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
package org.gradle.performance.regression.inception

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.TestName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test Gradle's build performance against current Gradle.
 *
 * Assert changes to the build of Gradle do not introduce regressions.
 *
 * By using the currently built Gradle version to run the Gradle build at:
 * - the last commit of the current working copy
 * - the baseline commit defined for the `gradleBuildBaseline` template in `subprojects/performance/templates.gradle`
 *
 * When accepting a regression or settling an improvement:
 * - update the baseline commit in `subprojects/performance/templates.gradle`
 * - be careful when rebasing/squashing/merging
 */
@Category(PerformanceRegressionTest)
class GradleBuildPerformanceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    TestName testName = new TestName()

    def buildContext = new IntegrationTestBuildContext()

    @AutoCleanup
    @Shared
    def resultStore = new CrossBuildResultsStore()

    def runner = new CrossBuildPerformanceTestRunner(
        new BuildExperimentRunner(new GradleSessionProvider(buildContext)),
        resultStore,
        buildContext) {

        @Override
        protected void defaultSpec(BuildExperimentSpec.Builder builder) {
            super.defaultSpec(builder)
            builder.workingDirectory = tmpDir.testDirectory
        }
    }

    def warmupBuilds = 10
    def measuredBuilds = 20

    @Unroll
    def "#tasks on the gradle build comparing the build"() {

        given:
        runner.testGroup = 'gradle build'
        runner.testId = testName.methodName

        and:
        def baselineBuildName = 'baseline build'
        def currentBuildName = 'current build'

        and:
        runner.baseline {
            displayName baselineBuildName
            projectName 'gradleBuildBaseline'
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                tasksToRun(tasks)
                useDaemon()
            }
        }

        and:
        runner.buildSpec {
            displayName currentBuildName
            projectName 'gradleBuildCurrent'
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                tasksToRun(tasks)
                useDaemon()
            }
        }

        when:
        def results = runner.run()

        then:
        results.assertEveryBuildSucceeds()

        and:
        def baselineResults = buildBaselineResults(results, baselineBuildName)
        def currentResults = results.buildResult(currentBuildName)

        then:
        def speedStats = baselineResults.getSpeedStatsAgainst(currentResults.name, currentResults)
        println(speedStats)
        if (baselineResults.fasterThan(currentResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        tasks << ['help']
    }

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }
}
