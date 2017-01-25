/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.Specification

@Category(PerformanceRegressionTest)
class AbstractCrossVersionPerformanceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new PerformanceTestDirectoryProvider()
    static def resultStore = new CrossVersionResultsStore()

    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    final CrossVersionPerformanceTestRunner runner = new CrossVersionPerformanceTestRunner(
        new BuildExperimentRunner(new GradleSessionProvider(buildContext)), resultStore, new ReleasedVersionDistributions(buildContext), buildContext)

    def setup() {
        runner.workingDir = tmpDir.testDirectory
        runner.current = new UnderDevelopmentGradleDistribution(buildContext)
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            resultStore.close()
        }
    }
}
