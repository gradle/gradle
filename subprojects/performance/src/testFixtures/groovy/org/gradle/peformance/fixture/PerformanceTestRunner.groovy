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

package org.gradle.peformance.fixture

import org.gradle.api.logging.Logging
import org.gradle.integtests.fixtures.*

public class PerformanceTestRunner {

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    String testProject
    int runs
    int warmUpRuns
    int accuracyMs
    double maxMemoryRegression
    List<String> otherVersions = []
    List<String> tasksToRun = []
    DataCollector dataCollector = new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt")

    PerformanceResults results

    PerformanceResults run() {
        results = new PerformanceResults(accuracyMs: accuracyMs, maxMemoryRegression: maxMemoryRegression, displayName: "Results for test project '$testProject' with tasks ${tasksToRun.join(', ')}")
        results.previous.name = "Gradle ${previous.version}"
        otherVersions.each { results.others[it] = new MeasuredOperationList(name: "Gradle $it") }

        LOGGER.lifecycle("Running performance tests for test project '{}', no. # runs: {}", testProject, runs)
        warmUpRuns.times {
            LOGGER.info("Executing warm-up run #${it + 1}")
            runOnce()
        }
        results.clear()
        runs.times {
            LOGGER.info("Executing test run #${it + 1}")
            runOnce()
        }
        results
    }

    void runOnce() {
        File projectDir = new TestProjectLocator().findProjectDir(testProject)
        runOnce(previous, projectDir, results.previous)
        otherVersions.each {
            runOnce(current.previousVersion(it), projectDir, results.others[it])
        }
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
        return executer.withArguments('-u').inDirectory(projectDir).withTasks(tasksToRun)
    }
}
