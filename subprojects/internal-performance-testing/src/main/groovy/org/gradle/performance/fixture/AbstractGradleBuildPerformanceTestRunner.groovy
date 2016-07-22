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
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution

abstract class AbstractGradleBuildPerformanceTestRunner<R extends PerformanceTestResult> {
    final GradleDistribution gradleDistribution = new UnderDevelopmentGradleDistribution()
    final BuildExperimentRunner experimentRunner
    final TestProjectLocator testProjectLocator = new TestProjectLocator()

    String testId
    String testGroup
    List<BuildExperimentSpec> specs = []

    final DataReporter<R> reporter

    BuildExperimentListener buildExperimentListener

    public AbstractGradleBuildPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<R> dataReporter) {
        this.reporter = dataReporter
        this.experimentRunner = experimentRunner
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
        builder.listener(buildExperimentListener)
    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        assert builder.projectName
        builder.invocation.workingDirectory = testProjectLocator.findProjectDir(builder.projectName)
    }

    abstract R newResult()

    abstract MeasuredOperationList operations(R result, BuildExperimentSpec spec)

    R run() {
        assert !specs.empty
        assert testId

        def results = newResult()

        runAllSpecifications(results)

        results.assertEveryBuildSucceeds()
        reporter.report(results)

        return results
    }

    void runAllSpecifications(R results) {
        specs.each {
            def operations = operations(results, it)
            experimentRunner.run(it, operations)
        }
    }

}
