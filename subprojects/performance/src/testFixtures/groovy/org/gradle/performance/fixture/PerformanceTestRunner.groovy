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
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion

public class PerformanceTestRunner {
    def testDirectoryProvider = new TestNameTestDirectoryProvider()
    def current = new UnderDevelopmentGradleDistribution()
    def buildContext = new IntegrationTestBuildContext()

    String testProject
    int runs
    int warmUpRuns

    List<String> tasksToRun = []
    DataCollector dataCollector = new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt")
    DataReporter  reporter = new TextFileDataReporter()
    List<String> args = []

    List<String> targetVersions = []
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    PerformanceResults results

    PerformanceResults run() {
        assert !targetVersions.empty

        def mostRecentFinalRelease = new ReleasedVersionDistributions().mostRecentFinalRelease.version.version
        def allVersions = targetVersions.collect { (it == 'last') ? mostRecentFinalRelease : it }.unique()
        def baselineVersions = []
        allVersions.each { it ->
            baselineVersions << new BaselineVersion(version: it,
                    maxExecutionTimeRegression: maxExecutionTimeRegression,
                    maxMemoryRegression: maxMemoryRegression,
                    results: new MeasuredOperationList(name: "Gradle $it")
            )
        }

        results = new PerformanceResults(
                baselineVersions: baselineVersions,
                versionUnderTest: GradleVersion.current().getVersion(),
                testTime: System.currentTimeMillis(),
                displayName: "Results for test project '$testProject' with tasks ${tasksToRun.join(', ')}")

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
        File projectDir = new TestProjectLocator().findProjectDir(testProject)
        results.baselineVersions.reverse().each {
            println "Gradle ${it.version}..."
            runOnce(buildContext.distribution(it.version), projectDir, it.results)
        }

        println "Current Gradle..."
        runOnce(current, projectDir, results.current)
    }

    void runOnce(GradleDistribution dist, File projectDir, MeasuredOperationList results) {
        def executer = this.executer(dist, projectDir)
        def operation = MeasuredOperation.measure { MeasuredOperation operation ->
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