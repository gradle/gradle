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
import org.gradle.performance.measure.Duration
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestPrecondition
import org.junit.Rule

class GradleInternalCrossVersionPerformanceTestRunnerTest extends ResultSpecification {
    private static interface ReporterAndStore extends DataReporter, ResultsStore {}

    private static final String MOST_RECENT_RELEASE = "2.10"

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties(
        'org.gradle.performance.db.url': "jdbc:h2:${tmpDir.testDirectory}"
    )

    final buildContext = IntegrationTestBuildContext.INSTANCE
    final experimentRunner = Mock(BuildExperimentRunner)
    final reporter = Mock(ReporterAndStore)
    final currentGradle = Stub(GradleDistribution)
    final releases = Stub(ReleasedVersionDistributions)
    final currentBaseVersion = GradleVersion.current().baseVersion.version

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
        releases.mostRecentRelease >> buildContext.distribution(MOST_RECENT_RELEASE)
    }

    def "runs tests against version under test plus requested baseline versions and most recent released version and builds results"() {
        given:
        def runner = runner()
        runner.testId = 'some-test'
        runner.previousTestIds = ['prev 1']
        runner.testProject = 'test1'
        runner.targetVersions = ['1.0', '1.1']
        runner.tasksToRun = ['build']
        runner.cleanTasks = ['clean']
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
        results.tasks == ['build']
        results.cleanTasks == ['clean']
        results.args == ['--arg1', '--arg2']
        runner.gradleOpts.find { it == '-Xmx24' }
        runner.gradleOpts.sort(false) == (runner.gradleOpts as Set).sort(false)
        results.daemon
        results.versionUnderTest
        results.jvm
        results.operatingSystem
        results.host
        results.current.size() == 4
        results.current.totalTime.average == Duration.seconds(10)
        results.baselineVersions*.version == ['1.0', '1.1', MOST_RECENT_RELEASE]
        results.baseline('1.0').results.size() == 4
        results.baseline('1.1').results.size() == 4
        results.baseline(MOST_RECENT_RELEASE).results.size() == 4

        and:
        4 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
            result.add(operation(totalTime: Duration.seconds(10)))
            result.add(operation(totalTime: Duration.seconds(10)))
            result.add(operation(totalTime: Duration.seconds(10)))
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
            result.add(operation(totalTime: Duration.seconds(10)))
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
            result.add(operation(totalTime: Duration.seconds(10)))
        }
        1 * reporter.report(_)
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
            result.add(operation(totalTime: Duration.seconds(10)))
        }
        1 * reporter.report(_)
        0 * _._
    }

    def "shouldn't endlessly resolve 'defaults'"() {
        given:
        def runner = runner()
        runner.targetVersions = ['2.11', 'defaults']

        when:
        System.setProperty('org.gradle.performance.baselines', 'defaults')
        runner.run()

        then:
        thrown(IllegalArgumentException)
    }

    @Requires(TestPrecondition.ONLINE)
    def "can use 'nightly' baseline version to refer to most recently snapshot version and exclude most recent release"() {
        given:
        def runner = runner()
        runner.targetVersions = ['nightly']

        when:
        def results = runner.run()

        then:
        results.baselineVersions*.version == [LatestNightlyBuildDeterminer.latestNightlyVersion]

        and:
        2 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
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
            result.add(operation(totalTime: Duration.seconds(10)))
        }
    }

    @Requires(TestPrecondition.ONLINE)
    def "can override baseline versions using a system property"() {
        given:
        def runner = runner()
        runner.targetVersions = versions

        when:
        System.setProperty('org.gradle.performance.baselines', override.join(','))
        experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
        }
        def results = runner.run()

        then:
        results.baselineVersions*.version == expected

        where:
        versions         | override                        | expected
        ['2.13']         | ['2.12']                        | ['2.12']
        ['2.13']         | ['last']                        | [MOST_RECENT_RELEASE]
        ['2.13']         | ['nightly']                     | [LatestNightlyBuildDeterminer.latestNightlyVersion]
        ['2.13']         | ['last', 'nightly']             | [MOST_RECENT_RELEASE, LatestNightlyBuildDeterminer.latestNightlyVersion]
        ['2.13', '2.12'] | ['last', 'defaults', 'nightly'] | [MOST_RECENT_RELEASE, '2.13', '2.12', LatestNightlyBuildDeterminer.latestNightlyVersion]
        ['2.13', 'last'] | ['last', 'defaults', 'nightly'] | [MOST_RECENT_RELEASE, '2.13', LatestNightlyBuildDeterminer.latestNightlyVersion]
    }

    def "can use a snapshot version in baselines"() {
        def runner = runner()
        runner.targetVersions = [MOST_RECENT_RELEASE, '3.1-20160801000011+0000']

        when:
        experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
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
            10.times {
                result.add(operation(totalTime: Duration.seconds(6)))
            }
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            10.times {
                result.add(operation(totalTime: Duration.seconds(5)))
            }
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "without overrides, test fails"
        thrown(AssertionError)
    }

    def "insignificant regressions are ignored"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5.0)))
            result.add(operation(totalTime: Duration.seconds(5.1)))
            result.add(operation(totalTime: Duration.seconds(5.2)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5.0)))
            result.add(operation(totalTime: Duration.seconds(5.1)))
            result.add(operation(totalTime: Duration.seconds(5.1)))

        }
        def results = runner.run()

        then:
        results.assertCurrentVersionHasNotRegressed()
    }

    def "outliers are ignored"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            9.times {
                result.add(operation(totalTime: Duration.seconds(5)))
            }
            result.add(operation(totalTime: Duration.seconds(100)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            10.times {
                result.add(operation(totalTime: Duration.seconds(5)))
            }
        }
        def results = runner.run()

        then:
        results.assertCurrentVersionHasNotRegressed()
    }

    def "a performance regression is identified in speed but we check for nothing"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        System.setProperty('org.gradle.performance.regression.checks', 'none')
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
            result.add(operation(totalTime: Duration.seconds(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(5)))
            result.add(operation(totalTime: Duration.seconds(5)))
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then: "test passes"
        noExceptionThrown()
    }

    def "fails when build under test fails"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            throw new RuntimeException()
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then:
        thrown(RuntimeException)
    }

    def "fails when baseline fails"() {
        given:
        def runner = runner()
        runner.targetVersions = ['last']

        when:
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            result.add(operation(totalTime: Duration.seconds(10)))
        }
        1 * experimentRunner.run(_, _) >> { BuildExperimentSpec spec, MeasuredOperationList result ->
            throw new RuntimeException()
        }
        def results = runner.run()
        results.assertCurrentVersionHasNotRegressed()

        then:
        thrown(RuntimeException)
    }

    def runner() {
        def runner = new GradleInternalCrossVersionPerformanceTestRunner(experimentRunner, reporter, reporter, releases, new IntegrationTestBuildContext())
        runner.testId = 'some-test'
        runner.testProject = 'some-project'
        runner.workingDir = tmpDir.testDirectory
        runner.current = currentGradle
        runner.runs = 1
        return runner
    }
}
