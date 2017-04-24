/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.nativeplatform

import org.apache.commons.io.FileUtils
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.LogFiles
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Unroll

class RealWorldNativePluginPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll
    def "build on #testProject with #parallelWorkers parallel workers"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.gradleOpts = ["-Xms1500m", "-Xmx1500m"]
        runner.targetVersions = ["4.0-20170412191037+0000"]
        runner.warmUpRuns = 5
        runner.runs = 10

        if (parallelWorkers) {
            runner.args += ["--parallel", "--max-workers=$parallelWorkers".toString()]
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | parallelWorkers
        "nativeMonolithic"            | 0
        "nativeMonolithic"            | 12
        "nativeMonolithicOverlapping" | 0
        "nativeMonolithicOverlapping" | 12
    }

    @Unroll
    def "build with #changeType change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.args = ["--parallel", "--max-workers=12"]
        runner.gradleOpts = ["-Xms512m", "-Xmx512m"]
        runner.warmUpRuns = iterations - 1
        runner.runs = iterations
        runner.targetVersions = ["4.0-20170412191037+0000"]
        if (runner.honestProfiler.enabled) {
            runner.honestProfiler.autoStartStop = false
        }

        def changedFile = fileToChange
        def changeClosure = change
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            String originalContent
            File originalContentFor

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                File file = new File(invocationInfo.projectDir, changedFile)
                if (originalContentFor != file) {
                    assert file.exists()
                    def backupFile = new File(file.parentFile, file.name + "~")
                    if (backupFile.exists()) {
                        originalContent = backupFile.text
                        file.text = originalContent
                    } else {
                        originalContent = file.text
                        FileUtils.copyFile(file, backupFile)
                    }
                    originalContentFor = file
                }
                if (invocationInfo.iterationNumber % 2 == 0) {
                    println "Changing $file"
                    // do change
                    changeClosure(file, originalContent)
                    if (runner.honestProfiler.enabled && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
                        println "Starting honestprofiler"
                        runner.honestProfiler.start()
                    }
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
                } else {
                    if (runner.honestProfiler.enabled && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
                        println "Stopping honestprofiler"
                        runner.honestProfiler.stop()
                        if (invocationInfo.iterationNumber == invocationInfo.iterationMax || (invocationInfo.iterationMax % 2 == 1 && invocationInfo.iterationNumber == invocationInfo.iterationMax - 1)) {
                            // last invocation, copy log file
                            def tmpDir = new File(System.getProperty("java.io.tmpdir"))
                            def destFile = new File(tmpDir, LogFiles.createFileNameForBuildInvocation(invocationInfo, "honestprofiler_", ".hpl"))
                            println "Copying honestprofiler log to $destFile"
                            FileUtils.copyFile(runner.honestProfiler.logFile, destFile)
                        }
                    }
                }
            }
        })

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        // source file change causes a single project, single source set, single file to be recompiled.
        // header file change causes a single project, two source sets, some files to be recompiled.
        // recompile all sources causes all projects, all source sets, all files to be recompiled.
        testProject               | changeType       | fileToChange                      | change                | iterations
        "mediumNativeMonolithic"  | 'source file'    | 'modules/project5/src/src100_c.c' | this.&changeCSource   | 40
        "mediumNativeMonolithic"  | 'header file'    | 'modules/project1/src/src50_h.h'  | this.&changeHeader    | 40
        "smallNativeMonolithic"   | 'build file'     | 'common.gradle'                   | this.&changeArgs      | 40
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
