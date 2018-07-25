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

import com.google.common.io.Files
import com.google.common.io.Resources
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.internal.concurrent.Stoppable
import org.gradle.performance.util.JCmd
import org.gradle.util.CollectionUtils

/**
 * Profiles performance test scenarios using the Java Flight Recorder.
 *
 * TODO support pause/resume so we can exclude clean tasks from measurement
 * TODO move flamegraph generation to buildSrc and offer it as a task so it can be used when people send us .jfr files
 */
@CompileStatic
@PackageScope
class JfrProfiler extends Profiler implements Stoppable {

    private final File logDirectory
    private final File config
    private final JCmd jCmd
    private final PidInstrumentation pid
    private final JfrFlameGraphGenerator flameGraphGenerator

    JfrProfiler(File targetDir) {
        logDirectory = targetDir
        config = createConfig()
        jCmd = new JCmd()
        flameGraphGenerator = new JfrFlameGraphGenerator()
        pid = new PidInstrumentation()
    }

    private static File createConfig() {
        URL jfcResource = JfrProfiler.getResource("gradle.jfc")
        File jfcFile = File.createTempFile("gradle", ".jfc")
        Resources.asByteSource(jfcResource).copyTo(Files.asByteSink(jfcFile))
        jfcFile.deleteOnExit()
        jfcFile
    }

    @Override
    List<String> getAdditionalJvmOpts(BuildExperimentSpec spec) {
        String flightRecordOptions = "stackdepth=1024"
        def jfrFile = getJfrFile(spec)
        jfrFile.parentFile.mkdirs()
        if (!useDaemon(spec)) {
            flightRecordOptions += ",defaultrecording=true,dumponexit=true,dumponexitpath=${jfrFile},settings=$config"
        }
        CollectionUtils.stringize(["-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:FlightRecorderOptions=$flightRecordOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"])
    }

    @Override
    List<String> getAdditionalGradleArgs(BuildExperimentSpec spec) {
        pid.gradleArgs
    }

    private File getJfrFile(BuildExperimentSpec spec) {
        def fileSafeName = spec.displayName.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        def baseDir = new File(logDirectory, fileSafeName)
        new File(baseDir, "profile.jfr")
    }

    void start(BuildExperimentSpec spec) {
        if (useDaemon(spec)) {
            jCmd.execute(pid.pid, "JFR.start", "name=profile", "settings=$config")
        }
    }

    void stop(BuildExperimentSpec spec) {
        def jfrFile = getJfrFile(spec)
        if (useDaemon(spec)) {
            jCmd.execute(pid.pid, "JFR.stop", "name=profile", "filename=${jfrFile}")
        }
        flameGraphGenerator.generateGraphs(jfrFile)
    }

    @Override
    void stop() {
        flameGraphGenerator.generateDifferentialGraphs(logDirectory)
    }

    private boolean useDaemon(BuildExperimentSpec spec) {
        spec.displayInfo.daemon
    }
}
