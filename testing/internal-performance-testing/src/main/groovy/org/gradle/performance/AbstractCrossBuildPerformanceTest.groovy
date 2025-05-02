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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.annotations.AllFeaturesShouldBeAnnotated
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleBuildExperimentRunner
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.performance.results.WritableResultsStore
import org.junit.Assume
import org.junit.Rule

import static org.gradle.performance.results.ResultsStoreHelper.createResultsStoreWhenDatabaseAvailable

@CompileStatic
@AllFeaturesShouldBeAnnotated
class AbstractCrossBuildPerformanceTest extends AbstractPerformanceTest {
    private static final String CROSS_VERSION_ONLY_PROPERTY_NAME = "org.gradle.performance.crossVersionOnly"
    private static final WritableResultsStore<CrossBuildPerformanceResults> RESULTS_STORE = createResultsStoreWhenDatabaseAvailable { new CrossBuildResultsStore() }

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider()

    CrossBuildPerformanceTestRunner runner

    def setup() {
        Assume.assumeFalse(Boolean.getBoolean(CROSS_VERSION_ONLY_PROPERTY_NAME))
        runner = new CrossBuildPerformanceTestRunner(
                new GradleBuildExperimentRunner(gradleProfilerReporter, outputDirSelector),
                RESULTS_STORE.reportAlso(dataReporter),
                buildContext
        ) {
            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = temporaryFolder.testDirectory
            }

            @Override
            protected void configureGradleSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
                super.configureGradleSpec(builder)
                AbstractCrossBuildPerformanceTest.this.defaultSpec(builder)
            }

            @Override
            protected void finalizeGradleSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
                super.finalizeGradleSpec(builder)
                AbstractCrossBuildPerformanceTest.this.finalizeSpec(builder)
            }
        }
        performanceTestIdProvider.setTestSpec(runner)
    }

    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
    }

    protected void finalizeSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) RESULTS_STORE).close()
        }
    }
}
