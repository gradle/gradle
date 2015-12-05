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
import org.gradle.performance.categories.NativePerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

@Category(NativePerformanceTest)
class MonolithicNativePluginPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' measuring incremental build speed")
    def "build monolithic native project"() {
        given:
        runner.testId = "build monolithic native project $testProject" + (parallelWorkers ? " (parallel)" : "")
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = [] // TODO: Add '2.10', 'last' once 2.10 is released.
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms4g", "-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]

        if (parallelWorkers) {
            runner.args += ["--parallel", "--max-workers=$parallelWorkers".toString()]
        }
        runner.maxMemoryRegression = DataAmount.mbytes(100)

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()
        // TODO: when 2.10 is available result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | maxExecutionTimeRegression | parallelWorkers
        "nativeMonolithic"            | millis(1000)               | 0
        "nativeMonolithic"            | millis(1000)               | 4
        "nativeMonolithicOverlapping" | millis(1000)               | 0
        "nativeMonolithicOverlapping" | millis(1000)               | 4
    }

    @Unroll('Project #buildSize native build #changeType')
    def "build with changes"(String buildSize, String changeType, Amount<Duration> maxExecutionTimeRegression, String changedFile, Closure changeClosure) {
        given:
        runner.testId = "native build ${buildSize} ${changeType}"
        runner.testProject = "${buildSize}NativeMonolithic"
        runner.tasksToRun = ['build']
        runner.args = ["--parallel", "--max-workers=4"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', '2.9', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms4g", "-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        runner.warmUpRuns = 2
        runner.runs = 10

        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            File file
            String originalContent

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (file == null) {
                    file = new File(invocationInfo.projectDir, changedFile)
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
                    changeClosure(file, originalContent)
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
        // source file change causes a single project, single source set, single file to be recompiled.
        // header file change causes a single project, two source sets, some files to be recompiled.
        // recompile all sources causes all projects, all source sets, all files to be recompiled.
        buildSize | changeType              | maxExecutionTimeRegression | changedFile                       | changeClosure
        "medium"  | 'source file change'    | millis(200)                | 'modules/project5/src/src100_c.c' | this.&changeCSource
        "medium"  | 'header file change'    | millis(5000)               | 'modules/project1/src/src50_h.h'  | this.&changeHeader
        "medium"  | 'recompile all sources' | millis(5000)               | 'common.gradle'                   | this.&changeArgs
    }

    void changeCSource(File file, String originalContent) {
        file.text = originalContent + """\nint C_function_added_in_test () {
                    |  printf("Hello world!");
                    |  return 0;
                    |}\n""".stripMargin()
    }

    void changeHeader(File file, String originalContent) {
        file.text = originalContent.replaceFirst(~/#endif/, '#define HELLO_WORLD "Hello world!"\n#endif')
    }

    void changeArgs(File file, String originalContent) {
        file.text = originalContent.
            replaceFirst(~/cCompiler.define "SOMETHING7=0"/, 'cCompiler.define "SOMETHING_NEW=0"').
            replaceFirst(~/cppCompiler.define "SOMETHING7=0"/, 'cppCompiler.define "SOMETHING_NEW=0"')
    }
}
