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
import org.gradle.integtests.fixtures.executer.GradleExecuter
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

public class PerformanceTestRunner {
    TestDirectoryProvider testDirectoryProvider
    GradleDistribution current
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    DataReporter reporter
    OperationTimer timer = new OperationTimer()
    TestProjectLocator testProjectLocator = new TestProjectLocator()

    String testId
    String testProject
    int runs
    int warmUpRuns

    List<String> tasksToRun = []
    DataCollector dataCollector = new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt")
    List<String> args = []

    List<String> targetVersions = []
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    PerformanceResults results

    PerformanceResults run() {
        assert !targetVersions.empty
        assert testId

        results = new PerformanceResults(
                testId: testId,
                testProject: testProject,
                tasks: tasksToRun,
                args: args,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                testTime: System.currentTimeMillis())

        def mostRecentFinalRelease = new ReleasedVersionDistributions().mostRecentFinalRelease.version.version
        def allVersions = targetVersions.collect { (it == 'last') ? mostRecentFinalRelease : it }.unique()
        allVersions.each { it ->
            def baselineVersion = results.baseline(it)
            baselineVersion.maxExecutionTimeRegression = maxExecutionTimeRegression
            baselineVersion.maxMemoryRegression = maxMemoryRegression
        }

        println "Running performance tests for test project '$testProject', no. of runs: $runs"
        warmUpRuns.times {
            println "Executing warm-up run #${it + 1}"
            runOnce()
        }
        results.clear()
        runs.times {
            println "Executing test run #${it + 1}"
            runOnce()
        }
        reporter.report(results)
        results
    }

    void runOnce() {
        File projectDir = testProjectLocator.findProjectDir(testProject)
        results.baselineVersions.each {
            println "Gradle ${it.version}..."
            runOnce(buildContext.distribution(it.version), projectDir, it.results)
        }

        println "Current Gradle..."
        runOnce(current, projectDir, results.current)
    }

    void runOnce(GradleDistribution dist, File projectDir, MeasuredOperationList results) {
        def executer = this.executer(dist, projectDir)
        def operation = timer.measure { MeasuredOperation operation ->
            executer.run()
        }
        dataCollector.collect(projectDir, operation)
        results.add(operation)
    }

    GradleExecuter executer(GradleDistribution dist, File projectDir) {
        dist.executer(testDirectoryProvider).
                requireGradleHome().
                withDeprecationChecksDisabled().
                withStackTraceChecksDisabled().
                withArguments('-u').
                inDirectory(projectDir).
                withTasks(tasksToRun).
                withArguments(args)
    }
}