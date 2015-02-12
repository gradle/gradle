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
import org.gradle.util.GradleVersion

class CrossBuildPerformanceTestRunner extends PerformanceTestSpec {
    final GradleDistribution gradleDistribution = new UnderDevelopmentGradleDistribution()
    final TestProjectLocator testProjectLocator = new TestProjectLocator()
    final GradleExecuterProvider executerProvider

    final OperationTimer timer = new OperationTimer()

    String testGroup
    List<BuildSpecification> buildSpecifications = []

    final GCLoggingCollector gcCollector = new GCLoggingCollector()
    final DataCollector dataCollector = new CompositeDataCollector(
            new MemoryInfoCollector(outputFileName: "build/totalMemoryUsed.txt"),
            gcCollector)

    CrossBuildPerformanceResults results
    final DataReporter<CrossBuildPerformanceResults> reporter

    public CrossBuildPerformanceTestRunner(GradleExecuterProvider executerProvider, DataReporter<CrossBuildPerformanceResults> dataReporter) {
        this.reporter = dataReporter
        this.executerProvider = executerProvider
    }

    public void buildSpec(@DelegatesTo(BuildSpecification.Builder) Closure<?> configureAction) {
        def builder = new BuildSpecification.Builder(null)
        configureAction.delegate = builder
        configureAction.call(builder)
        def specification = builder.build()

        if (buildSpecifications.find { it.displayName == specification.displayName }) {
            throw new IllegalStateException("Multiple specifications with display name '${spec.displayName}.")
        }
        buildSpecifications << specification
    }

    public void baseline(@DelegatesTo(BuildSpecification.Builder) Closure<?> configureAction) {
        buildSpec(configureAction)
    }

    public CrossBuildPerformanceResults run() {
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
        return results
    }

    void runAllSpecifications() {
        buildSpecifications.each { buildSpecification ->
            println "${buildSpecification.displayName} ..."
            gcCollector.useDaemon(buildSpecification.useDaemon);
            File projectDir = testProjectLocator.findProjectDir(buildSpecification.projectName)
            def buildParametersSpec = new BuildSpecificationBackedParametersSpecification(buildSpecification, gradleDistribution, projectDir)
            warmUpRuns.times {
                println "Executing warm-up run #${it + 1}"
                executerProvider.executer(buildParametersSpec).run()
            }
            def operations = results.buildResult(buildSpecification)
            runs.times {
                println "Executing test run #${it + 1}"
                runOnce(buildParametersSpec, operations)
            }
            if (buildSpecification.useDaemon) {
                executerProvider.executer(buildParametersSpec).withTasks().withArgument('--stop').run()
            }
        }
    }

    void runOnce(BuildParametersSpecification buildParametersSpecification, MeasuredOperationList results) {
        def executer = executerProvider.executer(buildParametersSpecification)
        dataCollector.beforeExecute(buildParametersSpecification.workingDirectory, executer)

        def operation = timer.measure { MeasuredOperation operation ->
            executer.run()
        }

        if (operation.exception == null) {
            dataCollector.collect(buildParametersSpecification.workingDirectory, operation)
        }

        results.add(operation)
    }

    static class BuildSpecificationBackedParametersSpecification implements BuildParametersSpecification {
        final BuildSpecification buildSpecification
        final GradleDistribution gradleDistribution
        final File workingDirectory

        BuildSpecificationBackedParametersSpecification(BuildSpecification buildSpecification, GradleDistribution gradleDistribution, File workingDir) {
            this.buildSpecification = buildSpecification
            this.gradleDistribution = gradleDistribution
            this.workingDirectory = workingDir
        }

        @Override
        String[] getArgs() {
            return buildSpecification.args
        }

        @Override
        String[] getGradleOpts() {
            return buildSpecification.gradleOpts
        }

        @Override
        String[] getTasksToRun() {
            return buildSpecification.tasksToRun
        }

        @Override
        boolean getUseDaemon() {
            return buildSpecification.useDaemon
        }
    }
}
