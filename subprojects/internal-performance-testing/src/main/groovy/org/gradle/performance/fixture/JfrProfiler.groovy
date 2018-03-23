/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.util.JCmd

import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.MEASUREMENT
import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.WARMUP

/**
 * Profiles performance test scenarios using the Java Flight Recorder.
 *
 * TODO support pause/resume so we can exclude clean tasks from measurement
 * TODO remove setters for useDaemon/versionUnderTest/scenarioUnderTest, this should all be available from BuildExperimentInvocationInfo
 * TODO move flamegraph generation to buildSrc and offer it as a task so it can be used when people send us .jfr files
 */
@CompileStatic
@PackageScope
class JfrProfiler extends Profiler {

    private final File logDirectory
    private final JCmd jCmd
    private final PidInstrumentation pid
    private final JfrFlameGraphGenerator flameGraphGenerator

    boolean useDaemon
    String versionUnderTest
    String scenarioUnderTest

    JfrProfiler(File targetDir) {
        logDirectory = targetDir
        jCmd = new JCmd()
        flameGraphGenerator = new JfrFlameGraphGenerator()
        pid = new PidInstrumentation()
    }

    @Override
    List<String> getAdditionalJvmOpts(File workingDir) {
        String flightRecordOptions = "stackdepth=1024"
        if (!useDaemon) {
            flightRecordOptions += ",defaultrecording=true,dumponexit=true,dumponexitpath=$jfrFile,settings=profile"
        }
        ["-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:FlightRecorderOptions=$flightRecordOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"] as List<String>
    }


    @Override
    List<String> getAdditionalArgs(File workingDir) {
        pid.gradleArgs
    }

    @Override
    void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (isEndOf(invocationInfo, WARMUP) && useDaemon) {
            start()
        }
        if (isEndOf(invocationInfo, MEASUREMENT)) {
            scenarioBaseDir.mkdirs()
            if (useDaemon) {
                stop()
            }
            flameGraphGenerator.generateGraphs(jfrFile)
        }
    }

    private boolean isEndOf(BuildExperimentInvocationInfo invocationInfo, BuildExperimentRunner.Phase phase) {
        invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == phase
    }

    private File getScenarioBaseDir() {
        def fileSafeScenarioName = scenarioUnderTest.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        new File(logDirectory, fileSafeScenarioName + "/" + versionUnderTest)
    }

    private File getJfrFile() {
        new File(scenarioBaseDir, "profile.jfr")
    }

    private void start() {
        jCmd.execute(pid.pid, "JFR.start", "name=profile", "settings=profile")
    }

    private void stop() {
        jCmd.execute(pid.pid, "JFR.stop", "name=profile", "filename=${jfrFile}")
    }
}
