/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.performance.ResultSpecification
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CrossVersionPerformanceTestRunnerTest extends ResultSpecification {
    final experimentRunner = Mock(BuildExperimentRunner)
    final reporter = Mock(DataReporter)
    final testProjectLocator = Stub(TestProjectLocator)
    final currentGradle = Stub(GradleDistribution)
    final mostRecentRelease = new ReleasedVersionDistributions().mostRecentFinalRelease.version.version
    final currentVersionBase = GradleVersion.current().baseVersion.version

    @Requires(TestPrecondition.NOT_PULL_REQUEST_BUILD)
    def "runs test and builds results"() {
        given:
        def runner = runner()
        runner.testId = 'some-test'
        runner.testProject = 'test1'
        runner.targetVersions = ['1.0', '1.1']
        runner.tasksToRun = ['clean', 'build']
        runner.args = ['--arg1', '--arg2']
        runner.warmUpRuns = 1
        runner.runs = 4
        runner.maxExecutionTimeRegression = Duration.millis(100)
        runner.maxMemoryRegression = DataAmount.bytes(10)

        when:
        def results = runner.run()

        then:
        results.testId == 'some-test'
        results.testProject == 'test1'
        results.tasks == ['clean', 'build']
        results.args == ['--arg1', '--arg2']
        results.versionUnderTest
        results.jvm
        results.operatingSystem
        results.current.size() == 4
        results.current.totalTime.average == Duration.seconds(10)
        results.current.totalMemoryUsed.average == DataAmount.kbytes(10)
        results.baselineVersions*.version == ['1.0', '1.1']
        results.baseline('1.0').results.size() == 4
        results.baseline('1.1').results.size() == 4
        results.baselineVersions.every { it.maxExecutionTimeRegression == runner.maxExecutionTimeRegression }
        results.baselineVersions.every { it.maxMemoryRegression == runner.maxMemoryRegression }

        and:
        3 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * reporter.report(_)
        0 * reporter._
    }

    @Requires(TestPrecondition.NOT_PULL_REQUEST_BUILD)
    def "can use 'last' baseline version to refer to most recently released version"() {
        given:
        def runner = runner()
        runner.targetVersions = ['1.0', 'last']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ['1.0', mostRecentRelease]
    }

    @Requires(TestPrecondition.NOT_PULL_REQUEST_BUILD)
    def "ignores baseline version if it has the same base as the version under test"() {
        given:
        def runner = runner()
        runner.targetVersions = ['1.0', currentVersionBase, mostRecentRelease, 'last']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ['1.0', mostRecentRelease]
    }

    def runner() {
        def runner = new CrossVersionPerformanceTestRunner(experimentRunner, reporter)
        runner.testId = 'some-test'
        runner.testProjectLocator = testProjectLocator
        runner.current = currentGradle
        runner.runs = 1
        return runner
    }
}
