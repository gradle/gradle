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
import org.gradle.util.GradleVersion

class CrossBuildPerformanceTestRunner {
    final GradleDistribution gradleDistribution = new UnderDevelopmentGradleDistribution()
    final BuildExperimentRunner experimentRunner
    final TestProjectLocator testProjectLocator = new TestProjectLocator()

    String testId
    String testGroup
    List<BuildExperimentSpec> specs = []

    final DataReporter<CrossBuildPerformanceResults> reporter

    public CrossBuildPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossBuildPerformanceResults> dataReporter) {
        this.reporter = dataReporter
        this.experimentRunner = experimentRunner
    }

    public void baseline(@DelegatesTo(BuildExperimentSpec.Builder) Closure<?> configureAction) {
        buildSpec(configureAction)
    }

    public void buildSpec(@DelegatesTo(BuildExperimentSpec.Builder) Closure<?> configureAction) {
        def builder = BuildExperimentSpec.builder()
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
        builder.invocation.distribution(gradleDistribution)
    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        assert builder.projectName
        builder.invocation.workingDirectory = testProjectLocator.findProjectDir(builder.projectName)
    }

    public CrossBuildPerformanceResults run() {
        assert !specs.empty
        assert testId

        def results = new CrossBuildPerformanceResults(
                testId: testId,
                testGroup: testGroup,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommit: Git.current().commitId,
                testTime: System.currentTimeMillis()
        )

        runAllSpecifications(results)

        results.assertEveryBuildSucceeds()
        reporter.report(results)

        return results
    }

    void runAllSpecifications(CrossBuildPerformanceResults results) {
        specs.each {
            def operations = results.buildResult(it.displayInfo)
            experimentRunner.run(it, operations)
        }
    }

}
