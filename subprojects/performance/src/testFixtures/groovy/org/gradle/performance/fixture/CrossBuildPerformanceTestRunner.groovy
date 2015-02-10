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

class CrossBuildPerformanceTestRunner extends PerformanceTestSpec {
    TestDirectoryProvider testDirectoryProvider
    GradleDistribution gradleDistribution = new UnderDevelopmentGradleDistribution()
    TestProjectLocator testProjectLocator = new TestProjectLocator()
    GradleExecuterProvider executerProvider = new GradleExecuterProvider()

    OperationTimer timer = new OperationTimer()

    String testGroup
    List<BuildSpecification> buildSpecifications = []

    private GCLoggingCollector gcCollector = new GCLoggingCollector()
    DataCollector dataCollector = new CompositeDataCollector(
            new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt"),
            gcCollector)

    CrossBuildPerformanceResults results
    DataReporter<CrossBuildPerformanceResults> reporter

    CrossBuildPerformanceTestRunner() {
        runs = 5
        warmUpRuns = 3
    }

    CrossBuildPerformanceResults run() {
        assert !buildSpecifications.empty
        assert testId

        results = new CrossBuildPerformanceResults(
                testId: testId,
                testGroup: testGroup,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommit: Git.current().commitId,
                testTime: System.currentTimeMillis()
        )

        runAllSpecifications()

        reporter.report(results)
        results
    }

    void runAllSpecifications() {
        buildSpecifications.each { buildSpecification ->
            gcCollector.useDaemon(buildSpecification.useDaemon);
            File projectDir = testProjectLocator.findProjectDir(buildSpecification.projectName)
            warmUpRuns.times {
                executerProvider.executer(buildSpecification, gradleDistribution, projectDir, testDirectoryProvider).run()
            }
            def operations = results.buildResult(buildSpecification)
            runs.times {
                runNow(buildSpecification, projectDir, operations)
            }
            if (buildSpecification.useDaemon) {
                executerProvider.executer(buildSpecification, gradleDistribution, projectDir, testDirectoryProvider).withTasks().withArgument('--stop').run()
            }
        }
    }

    void runNow(BuildSpecification buildSpecification, File projectDir, MeasuredOperationList results) {
        def executer = executerProvider.executer(buildSpecification, gradleDistribution, projectDir, testDirectoryProvider)
        dataCollector.beforeExecute(projectDir, executer)

        def operation = timer.measure { MeasuredOperation operation ->
            executer.run()
        }

        if (operation.exception == null) {
            dataCollector.collect(projectDir, operation)
        }
        results.add(operation)
    }
}
