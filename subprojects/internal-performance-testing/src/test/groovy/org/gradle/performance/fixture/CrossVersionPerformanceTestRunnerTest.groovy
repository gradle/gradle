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
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.performance.ResultSpecification
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class CrossVersionPerformanceTestRunnerTest extends ResultSpecification {
    private static interface ReporterAndStore extends DataReporter, ResultsStore {}

    private static final String mostRecentSnapshot = "2.11-20160101120000+0000"
    private static final String mostRecentRelease = "2.10"

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties(
        'org.gradle.performance.db.url': "jdbc:h2:${tmpDir.testDirectory}"
    )

    final buildContext = new IntegrationTestBuildContext();
    final experimentRunner = Mock(BuildExperimentRunner)
    final reporter = Mock(ReporterAndStore)
    final testProjectLocator = Stub(TestProjectLocator)
    final currentGradle = Stub(GradleDistribution)
    final releases = Stub(ReleasedVersionDistributions)
    final currentVersionBase = GradleVersion.current().baseVersion.version

    def setup() {
        releases.all >> [
            buildContext.distribution("1.0"),
            buildContext.distribution("1.1"),
            buildContext.distribution("2.11-rc-4"),
            buildContext.distribution("2.11-rc-2"),
            buildContext.distribution(mostRecentRelease)]
        releases.mostRecentFinalRelease >> buildContext.distribution(mostRecentRelease)
    }

    def "runs tests against version under test plus requested baseline versions and most recent released version and builds results"() {
        given:
        def runner = runner()
        runner.testId = 'some-test'
        runner.previousTestIds = ['prev 1']
        runner.testProject = 'test1'
        runner.targetVersions = ['1.0', '1.1']
        runner.tasksToRun = ['clean', 'build']
        runner.args = ['--arg1', '--arg2']
        runner.gradleOpts = ['-Xmx24']
        runner.useDaemon = true
        runner.warmUpRuns = 1
        runner.runs = 4
        runner.maxExecutionTimeRegression = Duration.millis(100)
        runner.maxMemoryRegression = DataAmount.bytes(10)

        when:
        def results = runner.run()

        then:
        results.testId == 'some-test'
        results.previousTestIds == ['prev 1']
        results.testProject == 'test1'
        results.tasks == ['clean', 'build']
        results.args == ['--arg1', '--arg2']
        runner.gradleOpts.findAll { !it.startsWith('-XX:MaxPermSize=') } == ['-Xmx24']
        results.daemon
        results.versionUnderTest
        results.jvm
        results.operatingSystem
        results.current.size() == 4
        results.current.totalTime.average == Duration.seconds(10)
        results.current.totalMemoryUsed.average == DataAmount.kbytes(10)
        results.baselineVersions*.version == ['1.0', '1.1', mostRecentRelease]
        results.baseline('1.0').results.size() == 4
        results.baseline('1.1').results.size() == 4
        results.baseline(mostRecentRelease).results.size() == 4
        results.baselineVersions.every { it.maxExecutionTimeRegression == runner.maxExecutionTimeRegression }
        results.baselineVersions.every { it.maxMemoryRegression == runner.maxMemoryRegression }

        and:
        4 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * reporter.report(_)
        0 * reporter._
    }

    def "can use 'last' baseline version to refer to most recently released version"() {
        given:
        def runner = runner()
        runner.targetVersions = ['1.0', 'last']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ['1.0', mostRecentRelease]

        and:
        3 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "can use 'nightly' baseline version to refer to most recently snapshot version and exclude most recent release"() {
        given:
        releases.mostRecentSnapshot >> buildContext.distribution(mostRecentSnapshot)

        and:
        def runner = runner()
        runner.targetVersions = ['nightly']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == [mostRecentSnapshot]

        and:
        2 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "ignores baseline version if it has the same base as the version under test"() {
        given:
        def runner = runner()
        runner.targetVersions = ['1.0', currentVersionBase, mostRecentRelease, 'last']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ['1.0', mostRecentRelease]

        and:
        3 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "uses RC when requested baseline version has not been released"() {
        given:
        def runner = runner()
        runner.targetVersions = ['2.11']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ["2.11-rc-4", "2.10"]

        and:
        3 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "can override baseline versions using a system property"() {
        given:
        def runner = runner()
        runner.targetVersions = versions
        releases.mostRecentSnapshot >> buildContext.distribution(mostRecentSnapshot)

        when:
        System.setProperty('org.gradle.performance.baselines', override.join(','))
        experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        def results = runner.run()

        then:
        results.baselineVersions*.version == expected

        where:
        versions         | override                        | expected
        ['2.11']         | ['2.12']                        | ['2.12']
        ['2.11']         | ['last']                        | [mostRecentRelease]
        ['2.11']         | ['nightly']                     | [mostRecentSnapshot]
        ['2.11']         | ['last', 'nightly']             | [mostRecentRelease, mostRecentSnapshot]
        ['2.11', '2.12'] | ['last', 'defaults', 'nightly'] | [mostRecentRelease, '2.11', '2.12', mostRecentSnapshot]
        ['2.11', 'last'] | ['last', 'defaults', 'nightly'] | [mostRecentRelease, '2.11', mostRecentSnapshot]
    }

    def runner() {
        def runner = new CrossVersionPerformanceTestRunner(experimentRunner, reporter, releases)
        runner.testId = 'some-test'
        runner.testProject = 'some-project'
        runner.workingDir = tmpDir.testDirectory
        runner.testProjectLocator = testProjectLocator
        runner.current = currentGradle
        runner.runs = 1
        return runner
    }
}
