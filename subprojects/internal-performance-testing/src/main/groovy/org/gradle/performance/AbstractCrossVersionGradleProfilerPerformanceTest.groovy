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
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.GradleProfilerBuildExperimentRunner
import org.gradle.performance.fixture.GradleProfilerCrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.CompositeDataReporter
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.performance.results.GradleProfilerReporter
import org.gradle.performance.results.SlackReporter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.Specification

/**
 * A base class for cross version performance tests.
 *
 * This base class uses Gradle profiler as a backend for running the performance tests.
 * This should replace {@link AbstractCrossVersionGradleInternalPerformanceTest} in the long run, so we only need to maintain the infrastructure once.
 */
@Category(PerformanceRegressionTest)
@CleanupTestDirectory
class AbstractCrossVersionGradleProfilerPerformanceTest extends Specification {

    private static def resultStore = new CrossVersionResultsStore()
    private static def reporter = SlackReporter.wrap(resultStore)

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider(getClass())

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    private GradleProfilerCrossVersionPerformanceTestRunner runner

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider()

    def setup() {
        def gradleProfilerReporter = new GradleProfilerReporter(temporaryFolder.testDirectory)
        def slackReporter = reporter
        def compositeReporter = CompositeDataReporter.of(slackReporter, gradleProfilerReporter)
        runner = new GradleProfilerCrossVersionPerformanceTestRunner(
            new GradleProfilerBuildExperimentRunner(gradleProfilerReporter.getResultCollector()),
            resultStore,
            compositeReporter,
            new ReleasedVersionDistributions(buildContext),
            buildContext
        )
        runner.workingDir = temporaryFolder.testDirectory
        runner.current = new UnderDevelopmentGradleDistribution(buildContext)
        performanceTestIdProvider.testSpec = runner
    }

    GradleProfilerCrossVersionPerformanceTestRunner getRunner() {
        runner
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            resultStore.close()
            reporter.close()
        }
    }
}
