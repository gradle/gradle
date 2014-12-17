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
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.GradleVersion

public class CrossVersionPerformanceTestRunner {
    TestDirectoryProvider testDirectoryProvider
    GradleDistribution current
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    CrossVersionDataReporter reporter
    OperationTimer timer = new OperationTimer()
    TestProjectLocator testProjectLocator = new TestProjectLocator()

    String testId
    String testProject
    int runs
    int warmUpRuns
    boolean useDaemon

    //sub runs 'inside' a run. Useful for tests with the daemon
    int subRuns

    List<String> tasksToRun = []
    private GCLoggingCollector gcCollector = new GCLoggingCollector()
    DataCollector dataCollector = new CompositeDataCollector(
            new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt"),
            gcCollector)
    List<String> args = []
    List<String> gradleOpts = []

    List<String> targetVersions = []
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    CrossVersionPerformanceResults results
    GradleExecuterProvider executerProvider = new GradleExecuterProvider()

    CrossVersionPerformanceResults run() {
        assert !targetVersions.empty
        assert testId

        if (useDaemon) {
            tasksToRun.add("--daemon")
            gcCollector.useDaemon()
        }

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

        allVersions.each { it ->
            def baselineVersion = results.baseline(it)
            baselineVersion.maxExecutionTimeRegression = maxExecutionTimeRegression
            baselineVersion.maxMemoryRegression = maxMemoryRegression
        }

        println "Running performance tests for test project '$testProject', no. of runs: $runs"
        warmUpRuns.times {
            println "Executing warm-up run #${it + 1}"
            runNow(1) //warm-up will not do any sub-runs
        }
        results.clear()
        runs.times {
            println "Executing test run #${it + 1}"
            runNow(subRuns)
        }
        reporter.report(results)
        results
    }

    void runNow(int subRuns) {
        File projectDir = testProjectLocator.findProjectDir(testProject)
        results.baselineVersions.each {
            println "Gradle ${it.version}..."
            runNow(buildContext.distribution(it.version), projectDir, it.results, subRuns)
        }

        println "Current Gradle..."
        runNow(current, projectDir, results.current, subRuns)
    }

    void runNow(GradleDistribution dist, File projectDir, MeasuredOperationList results, int subRuns) {
        def operation = timer.measure { MeasuredOperation operation ->
            subRuns.times {
                println "Sub-run ${it+1}..."
                //creation of executer is included in measuer operation
                //this is not ideal but it does not prevent us from finding performance regressions
                //because extra time is equally added to all executions
                def executer = executerProvider.executer(this, dist, projectDir)
                dataCollector.beforeExecute(projectDir, executer)
                executer.run()
            }
        }
        if (useDaemon) {
            executerProvider.executer(this, dist, projectDir).withTasks("--stop").run()
        }
        if (operation.exception == null) {
            dataCollector.collect(projectDir, operation)
        }
        results.add(operation)
    }
}
