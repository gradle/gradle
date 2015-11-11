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
import org.gradle.performance.fixture.*
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
        runner.targetVersions = [] // TODO: Add '2.10', 'last' once 2.10 is released.
        runner.useDaemon = true
        runner.gradleOpts = ["-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]

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

    @Unroll('Project #buildSize native build #changeType change')
    def "build with changes"() {
        given:
        runner.testId = "native build ${buildSize} ${changeType} change"
        runner.testProject = "${buildSize}NativeMonolithic"
        runner.tasksToRun = ['build']
        runner.args = ["--parallel", "--max-workers=4"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        runner.warmUpRuns = 2
        runner.runs = 10
        String fileName = changedFile
        Closure fileChanger = changeClosure
        boolean compilerOptionChange = (changeType == 'compiler options')
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            File file
            String originalContent

            @Override
            GradleInvocationCustomizer createInvocationCustomizer(BuildExperimentInvocationInfo invocationInfo) {
                if (compilerOptionChange) {
                    return new GradleInvocationCustomizer() {
                        @Override
                        GradleInvocationSpec customize(GradleInvocationSpec invocationSpec) {
                            if (invocationInfo.iterationNumber % 2 == 0) {
                                println "Adding -PaddMoreDefines to arguments"
                                return invocationSpec.withAdditionalArgs(["-PaddMoreDefines"])
                            } else {
                                return invocationSpec
                            }
                        }
                    }
                } else {
                    null
                }
            }

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (fileChanger != null) {
                    if (file == null) {
                        file = new File(invocationInfo.projectDir, fileName)
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
                        fileChanger(file, originalContent)
                    } else if (invocationInfo.iterationNumber > 2) {
                        println "Reverting $file"
                        file.text = originalContent
                    }
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
        buildSize | changeType         | maxExecutionTimeRegression | changedFile                       | changeClosure
        "small"   | '1'                | millis(1000)               | 'modules/project1/src/src45_c.c'  | this.&changeCSource
        "medium"  | '1'                | millis(5000)               | 'modules/project5/src/src100_c.c' | this.&changeCSource
        "small"   | 'few files'        | millis(1000)               | 'common/common/include/header8.h' | this.&changeHeader
        "medium"  | 'few files'        | millis(5000)               | 'common/common/include/header8.h' | this.&changeHeader
        "small"   | 'compiler options' | millis(1000)               | null                              | null
        "medium"  | 'compiler options' | millis(5000)               | null                              | null
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
}
