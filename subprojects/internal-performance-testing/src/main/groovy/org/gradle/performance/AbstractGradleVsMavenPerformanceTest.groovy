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
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.GradleVsMavenBuildPerformanceResults
import org.gradle.performance.results.GradleVsMavenBuildResultsStore
import org.gradle.performance.results.WritableResultsStore
import org.junit.Rule

import static org.gradle.performance.results.ResultsStoreHelper.createResultsStoreWhenDatabaseAvailable

@CompileStatic
class AbstractGradleVsMavenPerformanceTest extends AbstractPerformanceTest {
    private static final WritableResultsStore<GradleVsMavenBuildPerformanceResults> RESULT_STORE = createResultsStoreWhenDatabaseAvailable { new GradleVsMavenBuildResultsStore() }

    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    GradleVsMavenPerformanceTestRunner runner

    def setup() {
        runner = new GradleVsMavenPerformanceTestRunner(
            temporaryFolder,
            new GradleVsMavenBuildExperimentRunner(gradleProfilerReporter, outputDirSelector),
            RESULT_STORE.reportAlso(dataReporter),
            buildContext
        ) {
            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = temporaryFolder.testDirectory
            }
        }
        performanceTestIdProvider.setTestSpec(runner)
    }

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider()

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) RESULT_STORE).close()
        }
    }
}
