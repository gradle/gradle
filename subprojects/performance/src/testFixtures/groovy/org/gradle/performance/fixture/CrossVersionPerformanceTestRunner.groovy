/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.util.GradleVersion

public class CrossVersionPerformanceTestRunner extends PerformanceTestSpec {
    GradleDistribution current
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    final DataReporter<CrossVersionPerformanceResults> reporter
    TestProjectLocator testProjectLocator = new TestProjectLocator()
    final BuildExperimentRunner experimentRunner

    String testProject
    boolean useDaemon
    boolean allowEmptyTargetVersions = false

    List<String> tasksToRun = []
    List<String> args = []
    List<String> gradleOpts = []

    List<String> targetVersions = []
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    CrossVersionPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossVersionPerformanceResults> reporter) {
        this.reporter = reporter
        this.experimentRunner = experimentRunner
    }

    CrossVersionPerformanceResults run() {
        assert allowEmptyTargetVersions || !targetVersions.empty
        assert testId

        def results = new CrossVersionPerformanceResults(
                testId: testId,
                testProject: testProject,
                tasks: tasksToRun,
                args: args,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommit: Git.current().commitId,
                testTime: System.currentTimeMillis())

        def releasedDistributions = new ReleasedVersionDistributions()
        def releasedVersions = releasedDistributions.all*.version.version
        def mostRecentFinalRelease = releasedDistributions.mostRecentFinalRelease.version.version
        def currentBaseVersion = GradleVersion.current().getBaseVersion().version
        def allVersions = targetVersions.collect { (it == 'last') ? mostRecentFinalRelease : it }.unique()
        allVersions.remove(currentBaseVersion)

        // A target version may be something that is yet unreleased, so filter that out
        allVersions.removeAll { !releasedVersions.contains(it) }

        assert allowEmptyTargetVersions || !allVersions.isEmpty()

        File projectDir = testProjectLocator.findProjectDir(testProject)

        println "Running performance tests for test project '$testProject', no. of runs: $runs"

        allVersions.each { it ->
            def baselineVersion = results.baseline(it)
            baselineVersion.maxExecutionTimeRegression = maxExecutionTimeRegression
            baselineVersion.maxMemoryRegression = maxMemoryRegression

            runVersion(buildContext.distribution(baselineVersion.version), projectDir, baselineVersion.results)
        }

        runVersion(current, projectDir, results.current)

        reporter.report(results)
        results.assertEveryBuildSucceeds()

        return results
    }

    private void runVersion(GradleDistribution dist, File projectDir, MeasuredOperationList results) {
        def builder = BuildExperimentSpec.builder()
                .projectName(testId)
                .displayName(dist.version.version)
                .warmUpCount(warmUpRuns)
                .invocationCount(runs)
                .invocation {
            workingDirectory(projectDir)
            distribution(dist)
            tasksToRun(this.tasksToRun as String[])
            args(this.args as String[])
            gradleOpts(this.gradleOpts as String[])
            useDaemon(this.useDaemon)
        }

        def spec = builder.build()

        experimentRunner.run(spec, results)
    }

}
