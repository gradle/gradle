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
package org.gradle.performance.regression.kotlindsl

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.fixture.PerformanceTestRetryRule
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.RetryRule
import org.gradle.testing.internal.util.Specification
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.TestName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL

@Category(PerformanceRegressionTest)
class KotlinDslVsGroovyDslPerformanceTest extends Specification {

    @Rule
    RetryRule retry = new PerformanceTestRetryRule()

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    TestName testName = new TestName()

    def buildContext = new IntegrationTestBuildContext()

    @AutoCleanup
    @Shared
    def resultStore = new CrossBuildResultsStore()

    CrossBuildPerformanceTestRunner runner

    def warmupBuilds = 20
    def measuredBuilds = 40

    def setup() {
        runner = new CrossBuildPerformanceTestRunner(
            new BuildExperimentRunner(new GradleSessionProvider(buildContext)),
            resultStore,
            buildContext) {

            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = tmpDir.testDirectory
            }
        }
        runner.testGroup = 'Kotlin DSL vs Groovy DSL'
    }

    @Unroll
    def "help on #kotlinProject vs. help on #groovyProject"() {

        given:
        runner.testId = testName.methodName

        and:
        def baselineBuildName = 'Groovy DSL build'
        def currentBuildName = 'Kotlin DSL build'

        and:
        runner.baseline {
            displayName baselineBuildName
            projectName groovyProject.projectName
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                gradleOptions = ["-Xms${groovyProject.daemonMemory}", "-Xmx${groovyProject.daemonMemory}"]
                tasksToRun("help")
                useDaemon()
            }
        }

        and:
        runner.buildSpec {
            displayName currentBuildName
            projectName kotlinProject.projectName
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                gradleOptions = ["-Xms${kotlinProject.daemonMemory}", "-Xmx${kotlinProject.daemonMemory}"]
                tasksToRun("help")
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
        if (baselineResults.significantlyFasterThan(currentResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        kotlinProject                       | groovyProject
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | LARGE_JAVA_MULTI_PROJECT
    }

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }
}
