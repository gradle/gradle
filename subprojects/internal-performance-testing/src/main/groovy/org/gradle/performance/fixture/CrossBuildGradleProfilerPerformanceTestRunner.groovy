/*
 * Copyright 2019 the original author or authors.
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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.ResultsStore
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import java.util.function.Function

@CompileStatic
class CrossBuildGradleProfilerPerformanceTestRunner extends AbstractCrossBuildPerformanceTestRunner {
    CrossBuildGradleProfilerPerformanceTestRunner(GradleProfilerBuildExperimentRunner experimentRunner, ResultsStore resultsStore, DataReporter<CrossBuildPerformanceResults> dataReporter, IntegrationTestBuildContext buildContext) {
        super(experimentRunner, resultsStore, dataReporter, buildContext)
    }

    private final List<Function<InvocationSettings, BuildMutator>> buildMutators = []
    private final List<String> measuredBuildOperations = []

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        super.defaultSpec(builder)
        builder.measuredBuildOperations.addAll(measuredBuildOperations)
        builder.buildMutators.addAll(buildMutators)
    }

    void addBuildMutator(Function<InvocationSettings, BuildMutator> buildMutator) {
        buildMutators.add(buildMutator)
    }

    List<String> getMeasuredBuildOperations() {
        return measuredBuildOperations
    }
}
