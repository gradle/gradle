/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.fixture.AbstractBuildExperimentRunner
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.DefaultOutputDirSelector
import org.gradle.performance.results.GradleProfilerReporter
import org.gradle.performance.results.OutputDirSelector
import org.gradle.performance.results.PerformanceTestResult
import org.gradle.profiler.flamegraph.DifferentialStacksGenerator
import org.gradle.profiler.flamegraph.FlameGraphGenerator
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
abstract class AbstractPerformanceTest extends Specification {

    OutputDirSelector outputDirSelector
    GradleProfilerReporter gradleProfilerReporter
    DataReporter<PerformanceTestResult> dataReporter

    @Rule
    PerformanceTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider(getClass())

    protected DifferentialStacksGenerator differentialStacksGenerator
    protected FlameGraphGenerator flameGraphGenerator

    def setup() {
        this.outputDirSelector = new DefaultOutputDirSelector(temporaryFolder.testDirectory)
        this.gradleProfilerReporter = new GradleProfilerReporter(outputDirSelector)
        this.differentialStacksGenerator = new DifferentialStacksGenerator()
        this.flameGraphGenerator = new FlameGraphGenerator()
        this.dataReporter = gradleProfilerReporter.reportAlso(new DataReporter<PerformanceTestResult>() {
            @Override
            void report(PerformanceTestResult results) {
                if (AbstractBuildExperimentRunner.isProfilingActive()) {
                    def stacks = differentialStacksGenerator.generateDifferentialStacks(outputDirSelector.outputDirFor(results.testId))
                    flameGraphGenerator.generateDifferentialGraphs(stacks)
                }
            }

            @Override
            void close() throws IOException {
            }
        })
    }
}
