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
import org.gradle.api.internal.file.TestFiles
import org.gradle.performance.categories.GradleCorePerformanceTest
import org.gradle.performance.fixture.*
import org.gradle.performance.results.GradleVsMavenBuildResultsStore
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.Specification

@Category(GradleCorePerformanceTest)
@CompileStatic
class AbstractGradleVsMavenPerformanceTest extends Specification {
    private static final DataReporter<GradleVsMavenBuildPerformanceResults> RESULT_STORE = ResultsStoreHelper.maybeUseResultStore { new GradleVsMavenBuildResultsStore() }

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    GradleVsMavenPerformanceTestRunner runner = new GradleVsMavenPerformanceTestRunner(
        tmpDir, new GradleVsMavenBuildExperimentRunner(new GradleSessionProvider(tmpDir), TestFiles.execActionFactory()), RESULT_STORE) {
        @Override
        protected void defaultSpec(BuildExperimentSpec.Builder builder) {
            super.defaultSpec(builder)
            AbstractGradleVsMavenPerformanceTest.this.defaultSpec(builder)
        }

        @Override
        protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
            super.finalizeSpec(builder)
            AbstractGradleVsMavenPerformanceTest.this.finalizeSpec(builder)
        }
    }

    protected void defaultSpec(BuildExperimentSpec.Builder builder) {

    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        builder.listener = runner.buildExperimentListener
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable)RESULT_STORE).close()
        }
    }

}
