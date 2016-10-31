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
import org.gradle.integtests.fixtures.executer.PerformanceTestBuildContext
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

    private static final String MOST_RECENT_SNAPSHOT = "2.11-20160101120000+0000"
    private static final String MOST_RECENT_RELEASE = "2.10"

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties(
        'org.gradle.performance.db.url': "jdbc:h2:${tmpDir.testDirectory}"
    )

    final buildContext = IntegrationTestBuildContext.INSTANCE;
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
            buildContext.distribution("2.10"),
            // "2.11" is missing intentionally to test that RC gets used when baseline version has not been released
            buildContext.distribution("2.11-rc-4"),
            buildContext.distribution("2.11-rc-2"),
            buildContext.distribution("2.12"),
            buildContext.distribution("2.13"),
            buildContext.distribution(MOST_RECENT_RELEASE)]
        releases.mostRecentFinalRelease >> buildContext.distribution(MOST_RECENT_RELEASE)
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
        results.baselineVersions*.version == ['1.0', '1.1', MOST_RECENT_RELEASE]
        results.baseline('1.0').results.size() == 4
        results.baseline('1.1').results.size() == 4
        results.baseline(MOST_RECENT_RELEASE).results.size() == 4

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
        results.baselineVersions*.version == ['1.0', MOST_RECENT_RELEASE]

        and:
        3 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "can use 'none' in target versions to leave the baseline unspecified"() {
        given:
        def runner = runner()
        runner.targetVersions = ['none']

        when:
        def results = runner.run()

        then:
        !results.baselineVersions
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            assert result.name == 'Current Gradle'
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * reporter.report(_)
        1 * experimentRunner.getHonestProfiler()
        0 * _._
    }

    def "can override baselines with no baselines by using 'none' in the system property"() {
        given:
        def runner = runner()
        runner.targetVersions = ['2.11', '2.12']

        when:
        System.setProperty('org.gradle.performance.baselines', 'none')
        def results = runner.run()

        then:
        !results.baselineVersions
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            assert result.name == 'Current Gradle'
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * reporter.report(_)
        1 * experimentRunner.getHonestProfiler()
        0 * _._
    }

    def "shouldn't endlessly resolve 'defaults'"() {
        given:
        def runner = runner()
        runner.targetVersions = ['2.11', 'defaults']

        when:
        System.setProperty('org.gradle.performance.baselines', 'defaults')
        def results = runner.run()

        then:
        thrown(IllegalArgumentException)
    }

    def "can use 'nightly' baseline version to refer to most recently snapshot version and exclude most recent release"() {
        given:
        releases.mostRecentSnapshot >> buildContext.distribution(MOST_RECENT_SNAPSHOT)

        and:
        def runner = runner()
        runner.targetVersions = ['nightly']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == [MOST_RECENT_SNAPSHOT]

        and:
        2 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
    }

    def "ignores baseline version if it has the same base as the version under test"() {
        given:
        def runner = runner()
        runner.targetVersions = ['1.0', currentVersionBase, MOST_RECENT_RELEASE, 'last']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == ['1.0', MOST_RECENT_RELEASE]

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
        releases.mostRecentSnapshot >> buildContext.distribution(MOST_RECENT_SNAPSHOT)

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
        ['2.13']         | ['2.12']                        | ['2.12']
        ['2.13']         | ['last']                        | [MOST_RECENT_RELEASE]
        ['2.13']         | ['nightly']                     | [MOST_RECENT_SNAPSHOT]
        ['2.13']         | ['last', 'nightly']             | [MOST_RECENT_RELEASE, MOST_RECENT_SNAPSHOT]
        ['2.13', '2.12'] | ['last', 'defaults', 'nightly'] | [MOST_RECENT_RELEASE, '2.13', '2.12', MOST_RECENT_SNAPSHOT]
        ['2.13', 'last'] | ['last', 'defaults', 'nightly'] | [MOST_RECENT_RELEASE, '2.13', MOST_RECENT_SNAPSHOT]
    }

    def "can use a snapshot version in baselines"() {
        def runner = runner()
        runner.targetVersions = [MOST_RECENT_RELEASE, '3.1-20160801000011+0000']

        when:
        experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        def results = runner.run()

        then:
        results.baselineVersions*.version == [MOST_RECENT_RELEASE, '3.1-20160801000011+0000']
    }

    def "a performance regression is identified in speed"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "without overrides, test fails"
        thrown(AssertionError)
    }

    def "a performance regression is identified in speed but we only check for memory"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'memory')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test passes"
        noExceptionThrown()
    }

    def "a performance regression is identified in speed but we check for nothing"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'none')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test passes"
        noExceptionThrown()
    }

    def "a performance regression is identified in memory but we only check for speed"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'speed')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(5)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test passes"
        noExceptionThrown()
    }

    def "a performance regression is identified in memory but we only check for nothing"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'none')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(5)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test passes"
        noExceptionThrown()
    }

    def "a performance regression is identified in memory and we explicitly check for all"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'all')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(5)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test fails"
        thrown(AssertionError)
    }

    def "a performance regression is identified in speed and memory and we explicitly check for all"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.execution.checks', 'all')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10), totalMemoryUsed: DataAmount.kbytes(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5), totalMemoryUsed: DataAmount.kbytes(5)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then:
        thrown(AssertionError)
    }

    def runner() {
        def runner = new CrossVersionPerformanceTestRunner(experimentRunner, reporter, releases, new PerformanceTestBuildContext())
        runner.testId = 'some-test'
        runner.testProject = 'some-project'
        runner.workingDir = tmpDir.testDirectory
        runner.testProjectLocator = testProjectLocator
        runner.current = currentGradle
        runner.runs = 1
        return runner
    }
}
