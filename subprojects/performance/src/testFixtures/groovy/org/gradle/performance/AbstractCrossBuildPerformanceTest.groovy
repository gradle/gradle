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

import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.results.CrossBuildResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.Specification

@Category(PerformanceTest)
class AbstractCrossBuildPerformanceTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    static def resultStore = new CrossBuildResultsStore()

    final def runner = new CrossBuildPerformanceTestRunner(new BuildExperimentRunner(new GradleSessionProvider(tmpDir)), resultStore) {
        @Override
        protected void defaultSpec(BuildExperimentSpec.Builder builder) {
            builder.invocationCount(5).warmUpCount(1)
            super.defaultSpec(builder)
            AbstractCrossBuildPerformanceTest.this.defaultSpec(builder)
        }

        @Override
        protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
            super.finalizeSpec(builder)
            AbstractCrossBuildPerformanceTest.this.finalizeSpec(builder)
        }
    }

    protected void defaultSpec(BuildExperimentSpec.Builder builder) {

    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {

    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            resultStore.close()
        }
    }
}
