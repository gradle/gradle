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
package org.gradle.performance

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleVsMavenBuildExperimentRunner
import org.gradle.performance.fixture.GradleVsMavenPerformanceTestRunner
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.GradleProfilerReporter
import org.gradle.performance.results.GradleVsMavenBuildPerformanceResults
import org.gradle.performance.results.GradleVsMavenBuildResultsStore
import org.gradle.performance.results.WritableResultsStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.performance.results.ResultsStoreHelper.createResultsStoreWhenDatabaseAvailable

@CompileStatic
@CleanupTestDirectory
class AbstractGradleVsMavenPerformanceTest extends Specification {
    private static final WritableResultsStore<GradleVsMavenBuildPerformanceResults> RESULT_STORE = createResultsStoreWhenDatabaseAvailable { new GradleVsMavenBuildResultsStore() }

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider(getClass())

    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    GradleProfilerReporter gradleProfilerReporter = new GradleProfilerReporter(temporaryFolder.testDirectory)
    GradleVsMavenPerformanceTestRunner runner = new GradleVsMavenPerformanceTestRunner(
            temporaryFolder,
            new GradleVsMavenBuildExperimentRunner(gradleProfilerReporter),
            RESULT_STORE.reportAlso(gradleProfilerReporter),
            buildContext
    ) {
        @Override
        protected void defaultSpec(BuildExperimentSpec.Builder builder) {
            super.defaultSpec(builder)
            builder.workingDirectory = temporaryFolder.testDirectory
        }
    }

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider(runner)

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) RESULT_STORE).close()
        }
    }
}
