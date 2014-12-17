/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.GradleVersion

class CrossBuildPerformanceTestRunner {
    TestDirectoryProvider testDirectoryProvider
    GradleDistribution gradleDistribution = new UnderDevelopmentGradleDistribution()
    TestProjectLocator testProjectLocator = new TestProjectLocator()
    GradleExecuterProvider executerProvider = new GradleExecuterProvider()

    OperationTimer timer = new OperationTimer()

    String testId
    int runs = 5
    int warmUpRuns = 1
    int subRuns = 1
    List<BuildSpecification> buildSpecifications = []

    private GCLoggingCollector gcCollector = new GCLoggingCollector()
    DataCollector dataCollector = new CompositeDataCollector(
            new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt"),
            gcCollector)

    CrossBuildPerformanceResults results
    CrossBuildDataReporter reporter

    CrossBuildPerformanceResults run() {
        assert !buildSpecifications.empty
        assert testId

        results = new CrossBuildPerformanceResults(
                testId: testId,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommit: Git.current().commitId,
                testTime: System.currentTimeMillis()
        )

        warmUpRuns.times {
            runNow(1) //warm-up will not do any sub-runs
        }
        results.clear()
        runs.times {
            runNow(subRuns)
        }
        reporter.report(results)
        results
    }

    void runNow(int subRuns) {
        buildSpecifications.each {
            File projectDir = testProjectLocator.findProjectDir(it.projectName)
            runNow(it, projectDir, results.buildResult(it), subRuns)
        }
    }

    void runNow(BuildSpecification buildSpecification, File projectDir, MeasuredOperationList results, int subRuns) {
        def operation = timer.measure { MeasuredOperation operation ->
            subRuns.times {
                //creation of executer is included in measuer operation
                //this is not ideal but it does not prevent us from finding performance regressions
                //because extra time is equally added to all executions
                def executer = executerProvider.executer(buildSpecification, gradleDistribution, projectDir, testDirectoryProvider)
                dataCollector.beforeExecute(projectDir, executer)
                executer.run()
            }
        }
        if (operation.exception == null) {
            dataCollector.collect(projectDir, operation)
        }
        results.add(operation)
    }
}
