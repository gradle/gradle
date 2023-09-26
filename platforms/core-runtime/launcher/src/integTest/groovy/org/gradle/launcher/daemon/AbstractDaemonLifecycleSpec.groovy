/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonContextParser
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.server.DaemonStateCoordinator
import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class AbstractDaemonLifecycleSpec extends DaemonIntegrationSpec {
    public static final int BUILD_EXECUTION_TIMEOUT = 40
    int daemonIdleTimeout = 200
    int periodicCheckInterval = 5
    //normally, state transition timeout must be lower than the daemon timeout
    //so that the daemon does not timeout in the middle of the state verification
    //effectively hiding some bugs or making tests fail
    int stateTransitionTimeout = daemonIdleTimeout / 2

    final List<GradleHandle> builds = []
    final List<GradleHandle> foregroundDaemons = []

    // set this to change the java home used to launch any gradle, set back to null to use current JVM
    def javaHome = null

    @Delegate
    DaemonEventSequenceBuilder sequenceBuilder =
        new DaemonEventSequenceBuilder(stateTransitionTimeout * 1000)

    def buildDir(buildNum) {
        file("builds/$buildNum")
    }

    def buildDirWithScript(buildNum, buildScript) {
        def dir = buildDir(buildNum)
        dir.file("settings.gradle").touch()
        dir.file("build.gradle") << buildScript
        dir
    }

    void startBuild(String javaHome = null, String buildEncoding = null) {
        run {
            executer.withTasks("watch")
            executer.withDaemonIdleTimeoutSecs(daemonIdleTimeout)
            executer.withArguments(
                "-Dorg.gradle.daemon.healthcheckinterval=${periodicCheckInterval * 1000}",
                "--debug" // Need debug logging so we can extract the `DefaultDaemonContext`
            )
            if (javaHome) {
                executer.withJavaHome(javaHome)
            }
            if (buildEncoding) {
                executer.withDefaultCharacterEncoding(buildEncoding)
            }
            executer.usingProjectDirectory buildDirWithScript(builds.size(), """
                def stopFile = file("stop")
                def existsFile = file("exists")
                task('watch') {
                    doLast {
                        println "waiting for stop file"
                        long sanityCheck = System.currentTimeMillis() + 120000L
                        while (!stopFile.exists()) {
                            sleep 100
                            if (existsFile.exists()) {
                                println "found exit file, exiting"
                                System.exit(1)
                            }
                            if (System.currentTimeMillis() > sanityCheck) {
                                println "timed out waiting for stop file, failing"
                                throw new RuntimeException("It seems the stop file was never created")
                            }
                        }
                        println 'noticed stop file, finishing'
                    }
                }
            """)
            builds << executer.start()
        }
        //TODO - rewrite the lifecycle spec so that it uses the TestableDaemon
    }

    void completeBuild(buildNum = 0) {
        run {
            buildDir(buildNum).file("stop") << "stop"
        }
    }

    protected static deleteFile(File file) {
        // Repeat the attempt to delete in case it was temporarily locked
        poll(10) {
            if (file.exists()) {
                file.delete()
            }
            assert !file.exists()
        }
    }

    void waitForBuildToWait(buildNum = 0) {
        run {
            poll(BUILD_EXECUTION_TIMEOUT) { assert builds[buildNum].standardOutput.contains("waiting for stop file") }
        }
    }

    void waitForDaemonExpiration(buildNum = 0) {
        run {
            poll(BUILD_EXECUTION_TIMEOUT) { assert builds[buildNum].standardOutput.contains(DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE) }
        }
    }

    void waitForLifecycleLogToContain(buildNum = 0, String expected) {
        run {
            poll(BUILD_EXECUTION_TIMEOUT) { assert builds[buildNum].standardOutput.contains(expected) }
        }
    }

    void stopDaemons() {
        run { stopDaemonsNow() }
    }

    void stopDaemonsNow() {
        executer.withArguments("--stop", "--info")
        if (javaHome) {
            executer.withJavaHome(javaHome)
        }
        executer.run()
    }

    void startForegroundDaemon() {
        run { startForegroundDaemonNow() }
    }

    void startForegroundDaemonWithAlternateJavaHome() {
        run {
            javaHome = AvailableJavaHomes.differentJdk.javaHome
            startForegroundDaemonNow()
            javaHome = null
        }
    }

    void startForegroundDaemonNow() {
        if (javaHome) {
            executer.withJavaHome(javaHome)
        }
        executer.withArguments("--foreground", "--info", "-Dorg.gradle.daemon.idletimeout=${daemonIdleTimeout * 1000}", "-Dorg.gradle.daemon.healthcheckinterval=${periodicCheckInterval * 1000}",)
        foregroundDaemons << executer.start()
    }

    void killBuild(int num = 0) {
        run { builds[num].abort().waitForFailure() }
    }

    void failed(GradleHandle handle) {
        assert handle.waitForFailure()
    }

    void daemonContext(num = 0, Closure assertions) {
        run { doDaemonContext(builds[num], assertions) }
    }

    void foregroundDaemonContext(num = 0, Closure assertions) {
        run { doDaemonContext(foregroundDaemons[num], assertions) }
    }

    void doDaemonContext(gradleHandle, Closure assertions) {
        // poll here since even though the daemon has been marked as busy in the registry, the context may not have been
        // flushed to the log yet.
        DaemonContext context
        poll(5) {
            context = DaemonContextParser.parseFromString(gradleHandle.standardOutput)
        }
        context.with(assertions)
    }

    def cleanup() {
        try {
            def registry = new DaemonLogsAnalyzer(executer.daemonBaseDir).registry
            sequenceBuilder.build(registry).run()
        } finally {
            stopDaemonsNow()
        }
    }
}
