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

import org.gradle.api.logging.Logging
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.integtests.fixtures.executer.BasicGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

public class PerformanceTestRunner {

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    def current = new GradleDistribution(new TestNameTestDirectoryProvider())

    String testProject
    int runs
    int warmUpRuns

    List<String> tasksToRun = []
    DataCollector dataCollector = new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt")
    List<String> args = []

    List<String> targetVersions = ['last']
    List<Amount<Duration>> maxExecutionTimeRegression = [Duration.millis(0)]
    List<Amount<DataAmount>> maxMemoryRegression = [DataAmount.bytes(0)]

    PerformanceResults results

    PerformanceResults run() {
        assert targetVersions.size() == maxExecutionTimeRegression.size()
        assert targetVersions.size() == maxMemoryRegression.size()

        def baselineVersions = []
        targetVersions.eachWithIndex { it, idx ->
            def ver = it == 'last'? new ReleasedVersions(current).last.version : it
            baselineVersions << new BaselineVersion(version: ver,
                    maxExecutionTimeRegression: maxExecutionTimeRegression[idx],
                    maxMemoryRegression: maxMemoryRegression[idx],
                    results: new MeasuredOperationList(name: "Gradle $ver")
            )
        }

        results = new PerformanceResults(
                baselineVersions: baselineVersions,
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
        results
    }

    void runOnce() {
        File projectDir = new TestProjectLocator().findProjectDir(testProject)
        results.baselineVersions.reverse().each {
            println "Gradle ${it.version}..."
            runOnce(current.previousVersion(it.version), projectDir, it.results)
        }

        println "Current Gradle..."
        runOnce(current, projectDir, results.current)
    }

    void runOnce(BasicGradleDistribution dist, File projectDir, MeasuredOperationList results) {
        def executer = this.executer(dist, projectDir)
        def operation = MeasuredOperation.measure { MeasuredOperation operation ->
            executer.run()
        }
        dataCollector.collect(projectDir, operation)
        results.add(operation)
    }

    GradleExecuter executer(BasicGradleDistribution dist, File projectDir) {
        def executer
        if (dist instanceof GradleDistribution) {
            executer = new GradleDistributionExecuter(GradleDistributionExecuter.Executer.forking, dist)
            executer.withDeprecationChecksDisabled()
            executer.withStackTraceChecksDisabled()
        } else {
            executer = dist.executer()
        }
        executer.withGradleUserHomeDir(current.userHomeDir)
        return executer.withArguments('-u').inDirectory(projectDir).withTasks(tasksToRun).withArguments(args)
    }
}