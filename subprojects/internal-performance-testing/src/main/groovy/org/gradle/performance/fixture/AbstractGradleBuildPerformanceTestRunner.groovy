/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.time.TrueTimeProvider
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.PerformanceTestResult
import org.gradle.performance.results.ResultsStore
import org.gradle.performance.results.ResultsStoreHelper
import org.junit.Assume

abstract class AbstractGradleBuildPerformanceTestRunner<R extends PerformanceTestResult> {
    final IntegrationTestBuildContext buildContext
    final GradleDistribution gradleDistribution
    final BuildExperimentRunner experimentRunner
    final TestProjectLocator testProjectLocator = new TestProjectLocator()
    final TimeProvider timeProvider = new TrueTimeProvider()

    String testId
    String testGroup
    List<BuildExperimentSpec> specs = []

    final DataReporter<R> reporter

    BuildExperimentListener buildExperimentListener
    InvocationCustomizer invocationCustomizer

    public AbstractGradleBuildPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<R> dataReporter, IntegrationTestBuildContext buildContext) {
        this.reporter = dataReporter
        this.experimentRunner = experimentRunner
        this.buildContext = buildContext
        this.gradleDistribution = new UnderDevelopmentGradleDistribution(buildContext)
    }

    public void baseline(@DelegatesTo(GradleBuildExperimentSpec.GradleBuilder) Closure<?> configureAction) {
        buildSpec(configureAction)
    }

    public void buildSpec(@DelegatesTo(GradleBuildExperimentSpec.GradleBuilder) Closure<?> configureAction) {
        def builder = GradleBuildExperimentSpec.builder()
        configureAndAddSpec(builder, configureAction)
    }

    protected void configureAndAddSpec(BuildExperimentSpec.Builder builder, Closure<?> configureAction) {
        defaultSpec(builder)
        builder.with(configureAction)
        finalizeSpec(builder)
        def specification = builder.build()

        if (specs.any { it.displayName == specification.displayName }) {
            throw new IllegalStateException("Multiple specifications with display name '${specification.displayName}.")
        }
        specs << specification
    }

    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        builder.setListener(buildExperimentListener)
        builder.setInvocationCustomizer(invocationCustomizer)
    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        assert builder.projectName
        assert builder.workingDirectory
        builder.invocation.workingDirectory = builder.workingDirectory
    }

    protected List<String> customizeJvmOptions(List<String> jvmOptions) {
        PerformanceTestJvmOptions.customizeJvmOptions(jvmOptions)
    }

    abstract R newResult()

    abstract MeasuredOperationList operations(R result, BuildExperimentSpec spec)

    R run() {
        assert !specs.empty
        assert testId

        def scenarioSelector = new TestScenarioSelector()
        Assume.assumeTrue(scenarioSelector.shouldRun(testId, specs.projectName.toSet(), (ResultsStore) reporter))

        def results = newResult()

        runAllSpecifications(results)

        results.endTime = timeProvider.getCurrentTime()

        results.assertEveryBuildSucceeds()
        reporter.report(results)

        return results
    }

    void runAllSpecifications(R results) {
        specs.each {
            def operations = operations(results, it)
            def invocation = it.invocation
            if (experimentRunner.honestProfiler && invocation instanceof GradleInvocationSpec) {
                experimentRunner.honestProfiler.sessionId = "${testId}-${it.projectName}-${invocation.gradleDistribution.version.version}".replaceAll('[^a-zA-Z0-9.-]', '_').replaceAll('[_]+', '_')
            }
            experimentRunner.run(it, operations)
        }
    }

    protected static String determineChannel() {
        ResultsStoreHelper.determineChannel()
    }

    HonestProfilerCollector getHonestProfiler() {
        return experimentRunner.honestProfiler
    }
}
