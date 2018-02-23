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

package org.gradle.performance

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.performance.results.DataReporter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CompileStatic
@CleanupTestDirectory
class AbstractCrossBuildPerformanceTest extends Specification {
    private static final DataReporter<CrossBuildPerformanceResults> RESULT_STORE = new CrossBuildResultsStore()

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider()

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    CrossBuildPerformanceTestRunner runner = new CrossBuildPerformanceTestRunner(new BuildExperimentRunner(new GradleSessionProvider(buildContext)), RESULT_STORE, buildContext) {
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

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider(runner)

    protected void defaultSpec(BuildExperimentSpec.Builder builder) {

    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {

    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable)RESULT_STORE).close()
        }
    }
}
