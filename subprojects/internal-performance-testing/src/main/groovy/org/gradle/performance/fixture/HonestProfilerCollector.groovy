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
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
class HonestProfilerCollector implements DataCollector {
    private static final boolean HONEST_PROFILER_ENABLED = System.getProperty("org.gradle.performance.honestprofiler") != null

    int honestProfilerPort = 18080
    String honestProfilerHost = '127.0.0.1'
    boolean profilerJvmOptionAdded
    File honestProfilerLogFile

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        List<String> jvmOpts = []
        if (HONEST_PROFILER_ENABLED) {
            def honestProfilerDir = new File(System.getenv("HP_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/honest-profiler".toString())
            def honestProfilerLibFile = new File(honestProfilerDir, OperatingSystem.current().getSharedLibraryName('lagent'))
            if (honestProfilerLibFile.exists()) {
                honestProfilerLogFile = new File(workingDir, "honestprofiler.log").absoluteFile
                def hpProperties = [
                    interval: 7,
                    maxFrames: 1024,
                    logPath: honestProfilerLogFile.path,
                    port: honestProfilerPort,
                    host: honestProfilerHost,
                    start: 0
                ]
                def propertiesString = hpProperties.collect { k, v -> "$k=$v".toString() }.join(',')
                def hpJvmOption = "-agentpath:${honestProfilerLibFile.absolutePath}=${propertiesString}".toString()
                jvmOpts << hpJvmOption
                profilerJvmOptionAdded = true
            } else {
                System.err.println("Could not find Honest Profiler agent library at ${honestProfilerLibFile.absolutePath}")
            }
        }
        return jvmOpts;
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (profilerJvmOptionAdded) {
            if (invocationInfo.iterationNumber == invocationInfo.iterationMax) {
                switch (invocationInfo.phase) {
                    case BuildExperimentRunner.Phase.WARMUP:
                        // enable honest-profiler after warmup
                        startHonestProfiler()
                        break
                    case BuildExperimentRunner.Phase.MEASUREMENT:
                        sendCommand('stop')
                        // copy file after last measurement
                        if (honestProfilerLogFile.exists()) {
                            LogFiles.copyLogFile(honestProfilerLogFile, invocationInfo, "honestprofiler_", ".log");
                        }
                        break
                }
            }
        }
    }

    void startHonestProfiler() {
        // start profiling by sending "start" to controlling socket
        sendCommand('start')
    }

    void sendCommand(String command) {
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
