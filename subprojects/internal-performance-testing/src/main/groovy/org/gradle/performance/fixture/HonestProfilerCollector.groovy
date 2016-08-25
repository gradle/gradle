/*
 * Copyright 2016 the original author or authors.
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
import org.apache.commons.io.FileUtils
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
class HonestProfilerCollector implements DataCollector {
    boolean enabled = System.getProperty("org.gradle.performance.honestprofiler") != null
    int honestProfilerPort = 18080
    String honestProfilerHost = '127.0.0.1'
    int maxFrames = 1024
    int interval = 7
    boolean initiallyStopped = true
    boolean autoStartStop = true
    private File logFile
    private boolean profilerJvmOptionAdded
    File logDirectory

    File getLogFile() {
        logFile
    }

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        if (enabled) {
            def honestProfilerDir = locateHonestProfilerInstallation()
            def honestProfilerLibFile = new File(honestProfilerDir, OperatingSystem.current().getSharedLibraryName('lagent'))
            if (honestProfilerLibFile.exists()) {
                logFile = new File(workingDir, "honestprofiler.hpl").absoluteFile
                profilerJvmOptionAdded = true
                return [buildJvmOption(logFile, honestProfilerLibFile)]
            } else {
                System.err.println("Could not find Honest Profiler agent library at ${honestProfilerLibFile.absolutePath}")
            }
        }
        return Collections.emptyList()
    }

    private File locateHonestProfilerInstallation() {
        new File(System.getenv("HP_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/honest-profiler".toString())
    }

    private String buildJvmOption(File logFile, File honestProfilerLibFile) {
        def hpProperties = [
            interval: interval,
            maxFrames: maxFrames,
            logPath: logFile.path
        ]
        if (initiallyStopped) {
            hpProperties += [
                port: honestProfilerPort,
                host: honestProfilerHost,
                start: 0
            ]
        }
        def propertiesString = hpProperties.collect { k, v -> "$k=$v".toString() }.join(',')
        "-agentpath:${honestProfilerLibFile.absolutePath}=${propertiesString}".toString()
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (autoStartStop && profilerJvmOptionAdded) {
            if (invocationInfo.iterationNumber == invocationInfo.iterationMax) {
                switch (invocationInfo.phase) {
                    case BuildExperimentRunner.Phase.WARMUP:
                        // enable honest-profiler after warmup
                        start()
                        break
                    case BuildExperimentRunner.Phase.MEASUREMENT:
                        stop()
                        // copy file after last measurement
                        if (logFile.exists() && logDirectory) {
                            def destFile = new File(logDirectory, LogFiles.createFileNameForBuildInvocation(invocationInfo, "honestprofiler_", ".hpl"))
                            FileUtils.copyFile(logFile, destFile)
                        }
                        break
                }
            }
        }
    }

    void start() {
        sendCommand('start')
    }

    void stop() {
        sendCommand('stop')
    }

    private void sendCommand(String command) {
        def socket = new Socket(honestProfilerHost, honestProfilerPort)
        try {
            socket.outputStream.withStream { output ->
                output.write("${command}\r\n".bytes)
            }
        } finally {
            socket.close()
        }
    }
}
