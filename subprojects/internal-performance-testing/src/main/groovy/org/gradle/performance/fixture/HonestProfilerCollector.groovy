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
import groovy.util.logging.Log
import org.apache.commons.io.FileUtils
import org.apache.mina.util.AvailablePortFinder
import org.gradle.internal.UncheckedException
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
@Log
class HonestProfilerCollector implements DataCollector {
    // if set, this system property must point to the directory where log files will be copied
    // and flame graphs generated
    public static final String HONESTPROFILER_KEY = "org.gradle.performance.honestprofiler"

    boolean enabled = System.getProperty(HONESTPROFILER_KEY) != null
    int honestProfilerPort = AvailablePortFinder.getNextAvailable(18080)
    String honestProfilerHost = '127.0.0.1'
    int maxFrames = 1024
    int interval = 7
    boolean initiallyStopped = true
    boolean autoStartStop = true
    private File logFile
    private boolean profilerJvmOptionAdded
    File logDirectory
    FlameGraphSanitizer flameGraphSanitizer
    String sessionId

    HonestProfilerCollector() {
        logDirectory = enabled ? new File(System.getProperty(HONESTPROFILER_KEY)) : null
    }

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
                File flameGraphHomeDir = locateFlameGraphInstallation()
                if (flameGraphHomeDir.exists()) {
                    flameGraphSanitizer = new FlameGraphSanitizer(new FlameGraphSanitizer.RegexBasedSanitizerFunction(
                        (~'build_([a-z0-9]+)'): 'build_',
                        (~'settings_([a-z0-9]+)'): 'settings_',
                        (~'org[.]gradle[.]'): '',
                        (~'sun[.]reflect[.]GeneratedMethodAccessor[0-9]+'): 'GeneratedMethodAccessor'
                    ))
                }
                return [buildJvmOption(logFile, honestProfilerLibFile)]
            } else {
                System.err.println("Could not find Honest Profiler agent library at ${honestProfilerLibFile.absolutePath}")
            }
        }
        return Collections.emptyList()
    }

    private static File locateHonestProfilerInstallation() {
        new File(System.getenv("HP_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/honest-profiler".toString())
    }

    private static File locateFlameGraphInstallation() {
        new File(System.getenv("FG_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/FlameGraph".toString())
    }

    private String buildJvmOption(File logFile, File honestProfilerLibFile) {

        def hpProperties = [
            interval: interval,
            logPath: logFile.path,
            port: honestProfilerPort,
            host: honestProfilerHost,
            start: initiallyStopped?0:1,
            maxFrames: maxFrames, // keep maxFrames last because older versions of HP didn't have this
        ]
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
                        if (initiallyStopped) {
                            start()
                        }
                        break
                    case BuildExperimentRunner.Phase.MEASUREMENT:
                        stop()
                        // copy file after last measurement
                        if (logFile.exists() && logDirectory!=null) {
                            logDirectory.mkdirs()
                            def destFile = new File(logDirectory, "honestprofiler_${sessionId}.hpl")
                            def fgDestFile = new File(logDirectory, "honestprofiler_${sessionId}.txt")
                            def svgDestFile = new File(logDirectory, "honestprofiler_${sessionId}.svg")
                            FileUtils.copyFile(logFile, destFile)
                            if (flameGraphSanitizer) {
                                def sanitizedOutput = new File(destFile.parentFile, "${fgDestFile.name}.sanitized")
                                try {
                                    invokeHonestProfilerConverter(destFile, fgDestFile)
                                    flameGraphSanitizer.sanitize(fgDestFile, sanitizedOutput)
                                    invokeFlameGraphGenerator(sanitizedOutput, svgDestFile)
                                } catch (e) {
                                    // make errors non fatal at this point
                                    UncheckedException.throwAsUncheckedException(e)
                                }
                            }
                        }
                        break
                }
            }
        }
    }

    private static void invokeFlameGraphGenerator(File sanitizedOutput, File svgDestFile) {
        File flameGraphHomeDir = locateFlameGraphInstallation()
        def process = ["$flameGraphHomeDir/flamegraph.pl", sanitizedOutput].execute()
        def fos = svgDestFile.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }

    private static void invokeHonestProfilerConverter(File hpLogFile, File fgLogFile) {
        File hpHome = locateHonestProfilerInstallation()
        [Jvm.current().getExecutable('java').absolutePath,
         '-cp', "${Jvm.current().getToolsJar().absolutePath}:${hpHome}/honest-profiler.jar",
         'com.insightfullogic.honest_profiler.ports.console.FlameGraphDumperApplication',
         hpLogFile.absolutePath,
         fgLogFile.absolutePath].execute().waitFor()
    }

    void start() {
        sendCommand('start')
    }

    void stop() {
        try {
            sendCommand('stop')
        } catch (ConnectException ex) {
            log.warning("Unable to stop Honest Profiler : $ex")
        }
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
