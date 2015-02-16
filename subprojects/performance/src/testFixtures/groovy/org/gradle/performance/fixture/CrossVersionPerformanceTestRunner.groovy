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
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.util.GradleVersion

public class CrossVersionPerformanceTestRunner extends PerformanceTestSpec {
    GradleDistribution current
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    final DataReporter<CrossVersionPerformanceResults> reporter
    OperationTimer timer = new OperationTimer()
    TestProjectLocator testProjectLocator = new TestProjectLocator()

    String testProject
    boolean useDaemon

    List<String> tasksToRun = []
    final GCLoggingCollector gcCollector = new GCLoggingCollector()
    DataCollector dataCollector = new CompositeDataCollector(
            new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt"),
            gcCollector)
    List<String> args = []
    List<String> gradleOpts = []

    List<String> targetVersions = []
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    CrossVersionPerformanceResults results
    final GradleExecuterProvider executerProvider

    CrossVersionPerformanceTestRunner(GradleExecuterProvider executerProvider, DataReporter<CrossVersionPerformanceResults> reporter) {
        this.reporter = reporter
        this.executerProvider = executerProvider
    }

    CrossVersionPerformanceResults run() {
        assert !targetVersions.empty
        assert testId

        results = new CrossVersionPerformanceResults(
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

        assert !allVersions.isEmpty()

        File projectDir = testProjectLocator.findProjectDir(testProject)

        println "Running performance tests for test project '$testProject', no. of runs: $runs"

        allVersions.each { it ->
            def baselineVersion = results.baseline(it)
            baselineVersion.maxExecutionTimeRegression = maxExecutionTimeRegression
            baselineVersion.maxMemoryRegression = maxMemoryRegression

            println "Gradle ${baselineVersion.version}..."
            runVersion(buildContext.distribution(baselineVersion.version), projectDir, baselineVersion.results)
        }

        println "Current Gradle..."
        runVersion(current, projectDir, results.current)

        reporter.report(results)
        results
    }

    private void runVersion(GradleDistribution dist, File projectDir, MeasuredOperationList results) {
        warmUpRuns.times {
            println "Executing warm-up run #${it + 1}"
            runOnce(dist, projectDir, new MeasuredOperationList())
        }
        runs.times {
            println "Executing test run #${it + 1}"
            runOnce(dist, projectDir, results)
        }
        if (useDaemon) {
            executerProvider.executer(new RunnerBackedBuildParametersSpecification(this, dist, projectDir)).withTasks("--stop").run()
        }
    }

    private void runOnce(GradleDistribution dist, File projectDir, MeasuredOperationList results) {
        gcCollector.useDaemon(useDaemon)
        def executer = executerProvider.executer(new RunnerBackedBuildParametersSpecification(this, dist, projectDir))
        dataCollector.beforeExecute(projectDir, executer)

        def operation = timer.measure { MeasuredOperation operation ->
            executer.run()
        }
        if (operation.exception == null) {
            dataCollector.collect(projectDir, operation)
        }
        results.add(operation)
    }

    private static class RunnerBackedBuildParametersSpecification implements GradleInvocationSpec {
        final GradleDistribution distribution
        final CrossVersionPerformanceTestRunner runner
        final File workingDir

        RunnerBackedBuildParametersSpecification(CrossVersionPerformanceTestRunner runner, GradleDistribution distribution, File workingDir) {
            this.runner = runner
            this.distribution = distribution
            this.workingDir = workingDir
        }

        @Override
        GradleDistribution getGradleDistribution() {
            return distribution
        }

        @Override
        File getWorkingDirectory() {
            return workingDir
        }

        @Override
        String[] getTasksToRun() {
            runner.tasksToRun as String[]
        }

        @Override
        String[] getArgs() {
            runner.args as String[]
        }

        @Override
        String[] getGradleOpts() {
            runner.gradleOpts as String[]
        }

        @Override
        boolean getUseDaemon() {
            runner.useDaemon
        }
    }
}
