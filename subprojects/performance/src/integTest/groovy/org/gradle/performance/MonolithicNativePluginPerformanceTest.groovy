/*
 * Copyright 2015 the original author or authors.
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

import org.apache.commons.io.FileUtils
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.MeasuredOperation
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

@Category(Experiment)
class MonolithicNativePluginPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' measuring incremental build speed")
    def "build monolithic native project"() {
        given:
        runner.testId = "build monolithic native project $testProject" + (parallelWorkers ? " (parallel)" : "")
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        // TODO: Remove this once we no longer scan directories so much
        // runner.args += [ '-PincludeHack=true' ]
        if (parallelWorkers) {
            runner.args += ["--parallel", "--max-workers=$parallelWorkers".toString()]
        }
        runner.maxMemoryRegression = DataAmount.mbytes(100)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | maxExecutionTimeRegression | parallelWorkers
        "nativeMonolithic"            | millis(1000)               | 0
        "nativeMonolithic"            | millis(1000)               | 4
        "nativeMonolithicOverlapping" | millis(1000)               | 0
        "nativeMonolithicOverlapping" | millis(1000)               | 4
    }

    @Unroll('Project #type native build 1 change')
    def "build with 1 change"() {
        given:
        runner.testId = "native build ${type} 1 change"
        runner.testProject = "${type}NativeMonolithic"
        runner.tasksToRun = ['build']
        runner.args = ["--parallel", "--max-workers=4"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        runner.warmUpRuns = 2
        runner.runs = 10
        String projectType = type
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            File file
            String originalContent

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (file == null) {
                    file = new File(invocationInfo.projectDir, projectType == "small" ? "modules/project1/src/src45_c.c" : "modules/project5/src/src100_c.c")
                    assert file.exists()
                    def backupFile = new File(file.parentFile, file.name + "~")
                    if (backupFile.exists()) {
                        originalContent = backupFile.text
                        file.text = originalContent
                    } else {
                        originalContent = file.text
                        FileUtils.copyFile(file, backupFile)
                    }
                }
                if (invocationInfo.iterationNumber % 2 == 0) {
                    println "Changing $file"
                    // do change
                    file.text = originalContent + """\nint C_function_added_in_test () {
                    |  printf("Hello world!");
                    |  return 0;
                    |}\n""".stripMargin()
                } else if (invocationInfo.iterationNumber > 2) {
                    println "Reverting $file"
                    file.text = originalContent
                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if (invocationInfo.iterationNumber % 2 == 1) {
                    println "Omitting measurement from last run."
                    measurementCallback.omitMeasurement()
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type     | maxExecutionTimeRegression
        "small"  | millis(1000)
        "medium" | millis(5000)
    }
}
