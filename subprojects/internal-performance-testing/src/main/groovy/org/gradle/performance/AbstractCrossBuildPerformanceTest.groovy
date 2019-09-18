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

package org.gradle.performance

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildGradleProfilerPerformanceTestRunner
import org.gradle.performance.fixture.GradleProfilerBuildExperimentRunner
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.performance.results.DataReporter
import org.gradle.profiler.BenchmarkResultCollector
import org.gradle.profiler.report.CsvGenerator
import org.gradle.profiler.report.HtmlGenerator
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class AbstractCrossBuildPerformanceTest extends Specification {
    private static final CrossBuildResultsStore RESULT_STORE = new CrossBuildResultsStore()
    private static final String DEBUG_ARTIFACTS_DIRECTORY_PROPERTY_NAME = "org.gradle.performance.debugArtifactsDirectory"

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider()

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider()

    CrossBuildGradleProfilerPerformanceTestRunner runner

    def setup() {
        def debugArtifactsDirectoryPath = System.getProperty(DEBUG_ARTIFACTS_DIRECTORY_PROPERTY_NAME)
        def debugArtifactsDirectory = debugArtifactsDirectoryPath ? new File(debugArtifactsDirectoryPath) : temporaryFolder.testDirectory
        println debugArtifactsDirectory.absolutePath
        def resultCollector = new BenchmarkResultCollector(
            new CsvGenerator(new File(debugArtifactsDirectory, "benchmark.csv")),
            new HtmlGenerator(new File(debugArtifactsDirectory, "benchmark.html"))
        )
        def compositeReporter = new DataReporter<CrossBuildPerformanceResults>() {
            @Override
            void report(CrossBuildPerformanceResults results) {
                RESULT_STORE.report(results)
                resultCollector.summarizeResults { line ->
                    System.out.println("  " + line)
                }
                resultCollector.write()
            }

            @Override
            void close() throws IOException {
                RESULT_STORE.close()
            }
        }
        runner = new CrossBuildGradleProfilerPerformanceTestRunner(new GradleProfilerBuildExperimentRunner(resultCollector), RESULT_STORE, compositeReporter, buildContext) {
            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = temporaryFolder.testDirectory
                AbstractCrossBuildPerformanceTest.this.defaultSpec(builder)
            }

            @Override
            protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
                super.finalizeSpec(builder)
                AbstractCrossBuildPerformanceTest.this.finalizeSpec(builder)
            }
        }
        performanceTestIdProvider.setTestSpec(runner)
    }

    protected void defaultSpec(BuildExperimentSpec.Builder builder) {

    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {

    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) RESULT_STORE).close()
        }
    }
}
