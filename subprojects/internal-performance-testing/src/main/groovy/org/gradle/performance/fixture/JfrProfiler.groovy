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
import org.apache.commons.io.FileUtils
import org.gradle.api.JavaVersion
import org.gradle.internal.concurrent.Stoppable
import org.gradle.performance.util.JCmd

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
        URL jfcResource = JfrProfiler.getResource(JavaVersion.current().isJava9Compatible() ? "gradle-java9.jfc" : "gradle.jfc")
        File jfcFile = File.createTempFile("gradle", ".jfc")
        Resources.asByteSource(jfcResource).copyTo(Files.asByteSink(jfcFile))
        jfcFile.deleteOnExit()
        jfcFile
    }

    @Override
    List<String> getAdditionalJvmOpts(BuildExperimentSpec spec) {
        def jfrOutputDir = getJfrOutputDirectory(spec)
        getJvmOpts(!useDaemon(spec), jfrOutputDir)
    }

    private List<String> getJvmOpts(boolean startRecordingImmediately, File jfrOutputLocation) {
        String flightRecordOptions = "stackdepth=1024"
        if (startRecordingImmediately) {
            flightRecordOptions += ",defaultrecording=true,dumponexit=true,dumponexitpath=${jfrOutputLocation},settings=$config"
        }
        def opts = []
        if (!JavaVersion.current().isJava11Compatible()) {
            opts << "-XX:+UnlockCommercialFeatures"
        }
        opts += ["-XX:+FlightRecorder", "-XX:FlightRecorderOptions=" + flightRecordOptions, "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"]
        opts
    }

    @Override
    String getJvmOptsForUseInBuild(String recordingsDirectoryRelativePath) {
        // Don't use the flames directory, since we shouldn't generate flame graphs for the additional JFR files.
        def recordingsLocation = new File(new File(logDirectory.parentFile, "jfr-recordings"), recordingsDirectoryRelativePath)
        recordingsLocation.mkdirs()
        return getJvmOpts(true, recordingsLocation).join(";")
    }

    @Override
    List<String> getAdditionalGradleArgs(BuildExperimentSpec spec) {
        pid.gradleArgs
    }

    private File getJfrOutputDirectory(BuildExperimentSpec spec) {
        def fileSafeName = spec.displayName.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        def baseDir = new File(logDirectory, fileSafeName)
        def outputDir = new File(baseDir, "jfr-recordings")
        outputDir.mkdirs()
        return outputDir
    }

    void start(BuildExperimentSpec spec) {
        // Remove any profiles created during warmup
        // TODO Should not run warmup runs with the profiler enabled for no daemon cases – https://github.com/gradle/gradle/issues/9458
        FileUtils.cleanDirectory(getJfrOutputDirectory(spec))
        if (useDaemon(spec)) {
            jCmd.execute(pid.pid, "JFR.start", "name=profile", "settings=$config")
        }
    }

    void stop(BuildExperimentSpec spec) {
        def jfrOutputDir = getJfrOutputDirectory(spec)
        if (useDaemon(spec)) {
            def jfrFile = new File(jfrOutputDir, "profile.jfr")
            jCmd.execute(pid.pid, "JFR.stop", "name=profile", "filename=${jfrFile}")
        }
        flameGraphGenerator.generateGraphs(jfrOutputDir)
    }

    @Override
    void stop() {
        flameGraphGenerator.generateDifferentialGraphs(logDirectory)
    }

    private static boolean useDaemon(BuildExperimentSpec spec) {
        spec.displayInfo.daemon
    }
}
