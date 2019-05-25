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

import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.TestName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.performance.regression.inception.GradleInceptionPerformanceTest.extraGradleBuildArguments

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
@CleanupTestDirectory
class GradleBuildPerformanceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    @Rule
    TestName testName = new TestName()

    def buildContext = new IntegrationTestBuildContext()

    @AutoCleanup
    @Shared
    def resultStore = new CrossBuildResultsStore()

    CrossBuildPerformanceTestRunner runner

    def warmupBuilds = 20
    def measuredBuilds = 20

    def setup() {
        runner = new CrossBuildPerformanceTestRunner(
            new BuildExperimentRunner(new GradleSessionProvider(buildContext)),
            resultStore,
            buildContext) {

            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = temporaryFolder.testDirectory
                if (builder instanceof GradleBuildExperimentSpec.GradleBuilder) {
                    builder.invocation.args(extraGradleBuildArguments() as String[])
                }
            }
        }
        runner.testGroup = 'gradle build'
    }

    def "help on the gradle build comparing the build"() {
        given:
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
                tasksToRun("help")
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
                tasksToRun("help")
                useDaemon()
            }
        }

        when:
        def results = runner.run()

        then:
        def baselineResults = buildBaselineResults(results, baselineBuildName)
        def currentResults = results.buildResult(currentBuildName)

        then:
        def speedStats = baselineResults.getSpeedStatsAgainst(currentResults.name, currentResults)
        println(speedStats)
        if (baselineResults.significantlyFasterThan(currentResults)) {
            throw new AssertionError(speedStats)
        }
    }

    def "eager vs lazy on the gradle build"() {
        given:
        runner.testId = testName.methodName

        and:
        def eagerBuildName = 'eager build'
        def lazyBuildName = 'lazy build'

        and:
        runner.baseline {
            displayName eagerBuildName
            projectName 'gradleBuildCurrent'
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                // Force tasks to be realized even if they were created with the lazy API.
                args("-D" + DefaultTaskContainer.EAGERLY_CREATE_LAZY_TASKS_PROPERTY + "=true")
                tasksToRun("help")
                useDaemon()
            }
        }

        and:
        runner.buildSpec {
            displayName lazyBuildName
            projectName 'gradleBuildCurrent'
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                tasksToRun("help")
                useDaemon()
            }
        }

        when:
        def results = runner.run()

        then:
        def baselineResults = buildBaselineResults(results, eagerBuildName)
        def currentResults = results.buildResult(lazyBuildName)

        then:
        def speedStats = baselineResults.getSpeedStatsAgainst(currentResults.name, currentResults)
        println(speedStats)
        if (baselineResults.significantlyFasterThan(currentResults)) {
            throw new AssertionError(speedStats)
        }
    }

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }
}
