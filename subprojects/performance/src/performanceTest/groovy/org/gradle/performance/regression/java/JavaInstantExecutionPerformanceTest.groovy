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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestFile
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import java.nio.file.Files

import static org.junit.Assert.assertTrue
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC
import static org.gradle.performance.generator.JavaTestProject.SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC


@Category(PerformanceRegressionTest)
class JavaInstantExecutionPerformanceTest extends AbstractCrossVersionPerformanceTest {

    private TestFile instantExecutionStateDir

    def setup() {
        instantExecutionStateDir = temporaryFolder.file(".instant-execution-state")
    }

    @Unroll
    def "assemble on #testProject #action instant execution state with #daemon daemon"() {

        given:
        runner.targetVersions = ["5.6-20190524021549+0000"]
        runner.testProject = testProject.projectName
        runner.tasksToRun = ["assemble"]
        runner.args = ["-Dorg.gradle.unsafe.instant-execution"]

        and:
        runner.useDaemon = daemon == hot
        runner.addBuildExperimentListener(listenerFor(action))
        runner.warmUpRuns = daemon == hot ? 15 : 1
        runner.runs = daemon == hot ? 50 : 25

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                           | daemon | action
        LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC | hot    | loading
        LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC | hot    | storing
        LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC | cold   | loading
        LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC | cold   | storing
        SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC | hot    | loading
        SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC | hot    | storing
        SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC | cold   | loading
        SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC | cold   | storing
    }

    private BuildExperimentListener listenerFor(String action) {
        return instantInvocationListenerFor(action, instantExecutionStateDir)
    }

    static String loading = "loading"
    static String storing = "storing"
    static String hot = "hot"
    static String cold = "cold"

    static BuildExperimentListener instantInvocationListenerFor(String action, File stateDir) {
        return new BuildExperimentListenerAdapter() {

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (action == storing) {
                    stateDir.deleteDir()
                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if (invocationInfo.iterationNumber > 1) {
                    def tag = action == storing
                        ? "Calculating task graph as no instant execution cache is available"
                        : "Reusing instant execution cache"
                    def found = Files.lines(invocationInfo.buildLog.toPath()).withCloseable { lines ->
                        lines.anyMatch { line -> line.contains(tag) }
                    }
                    if (!found) {
                        assertTrue("Instant Execution log '$tag' not found in '$invocationInfo.buildLog'\n\n$invocationInfo.buildLog.text", found)
                    }
                }
            }
        }
    }
}
