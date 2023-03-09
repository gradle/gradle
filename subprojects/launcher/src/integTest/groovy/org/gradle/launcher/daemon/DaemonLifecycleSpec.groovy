/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.server.DaemonStateCoordinator
import org.gradle.launcher.daemon.server.api.HandleStop
import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Ignore
import spock.lang.IgnoreIf

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll
/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 *
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
class DaemonLifecycleSpec extends DaemonIntegrationSpec {

    public static final int BUILD_EXECUTION_TIMEOUT = 40
    int daemonIdleTimeout = 200
    int periodicCheckInterval = 5
    //normally, state transition timeout must be lower than the daemon timeout
    //so that the daemon does not timeout in the middle of the state verification
    //effectively hiding some bugs or making tests fail
    int stateTransitionTimeout = daemonIdleTimeout/2

    final List<GradleHandle> builds = []
    final List<GradleHandle> foregroundDaemons = []

    // set this to change the java home used to launch any gradle, set back to null to use current JVM
    def javaHome = null

    // set this to change the desired default encoding for the build request
    def buildEncoding = null

    @Delegate DaemonEventSequenceBuilder sequenceBuilder =
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
                task('watch') {
                    doLast {
                        println "waiting for stop file"
                        long sanityCheck = System.currentTimeMillis() + 120000L
                        while(!file("stop").exists()) {
                            sleep 100
                            if (file("exit").exists()) {
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

    private static deleteFile(File file) {
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

    void startForegroundDaemonWithDefaultCharacterEncoding(String encoding) {
        run {
            executer.withDefaultCharacterEncoding(encoding)
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

    //this is a windows-safe way of killing the process
    void disappearDaemon(int num = 0) {
        run {
            buildDir(num).file("exit") << "exit"
        }
    }

    void killForegroundDaemon(int num = 0) {
        run { foregroundDaemons[num].abort().waitForFailure() }
    }

    void killBuild(int num = 0) {
        run { builds[num].abort().waitForFailure() }
    }

    void buildFailed(int num = 0) {
        run { failed builds[num] }
    }

    void foregroundDaemonCompleted(int num = 0) {
        run { foregroundDaemons[num].waitForFinish() }
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

    def "daemons do some work - sit idle - then timeout and die"() {
        //in this particular test we need to make the daemon timeout
        //shorter than the state transition timeout so that
        //we can detect the daemon idling out within state verification window
        daemonIdleTimeout = stateTransitionTimeout/2

        when:
        startBuild()

        then:
        busy()

        when:
        completeBuild()

        then:
        idle()

        and:
        stopped()
    }

    //Java 9 and above needs --add-opens to make environment variable mutation work
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "existing foreground idle daemons are used"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
    }

    def "a new daemon is started if all existing are busy"() {
        when:
        startBuild()

        then:
        busy()

        when:
        startBuild()

        then:
        busy 2
    }

    def "sending stop to idle daemons causes them to terminate immediately"() {
        when:
        startBuild()

        then:
        busy()

        when:
        completeBuild()

        then:
        idle()

        when:
        stopDaemons()

        then:
        stopped()
    }

    def "stopping daemon that is building shows message"() {
        when:
        startBuild()

        then:
        waitForBuildToWait()

        when:
        stopDaemons()

        then:
        waitForLifecycleLogToContain(HandleStop.EXPIRATION_REASON)
    }

    def "daemon stops after current build if registry is deleted"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        when:
        waitForDaemonExpiration()

        then:
        completeBuild()

        and:
        stopped()
    }

    def "idle daemon stops immediately if registry is deleted"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()

        then:
        completeBuild()

        and:
        idle()

        when:
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        then:
        stopped()
    }

    def "daemon stops after current build if registry is deleted and recreated"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }
        startBuild()
        waitForBuildToWait(1)

        then:
        daemonContext(0) {
            assert(new DaemonDir(executer.daemonBaseDir).registry.exists())
        }

        when:
        waitForDaemonExpiration(0)

        then:
        completeBuild(0)
        completeBuild(1)

        and:
        idle 1
    }

    def "starting new build recreates registry and succeeds"() {
        File registry

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        when:
        waitForDaemonExpiration()

        then:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            assert(registry.exists())
        }

        and:
        completeBuild()
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null})
    def "if a daemon exists but is using a different java home, a new compatible daemon will be created and used"() {
        when:
        startForegroundDaemonWithAlternateJavaHome()

        then:
        idle()

        and:
        foregroundDaemonContext {
            assert javaHome.canonicalPath == AvailableJavaHomes.differentJdk.javaHome.canonicalPath
        }

        when:
        startBuild()

        then:
        numDaemons 2
        busy 1

        when:
        waitForBuildToWait()
        completeBuild()

        then:
        daemonContext {
            assert javaHome == Jvm.current().javaHome
        }
    }

    def "if a daemon exists but is using a file encoding, a new compatible daemon will be created and used"() {
        when:
        startBuild(null, "US-ASCII")
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            assert daemonOpts.contains("-Dfile.encoding=US-ASCII")
        }

        then:
        completeBuild()

        then:
        idle()

        when:
        startBuild(null, "UTF-8")
        waitForLifecycleLogToContain(1, "1 incompatible")
        waitForBuildToWait()

        then:
        state 1, 1

        then:
        completeBuild(1)

        then:
        idle 2
        daemonContext(1) {
            assert daemonOpts.contains("-Dfile.encoding=UTF-8")
        }
    }

    def "duplicate daemons expire quickly"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy 1

        when:
        startBuild()
        waitForBuildToWait(1)

        then:
        busy 2

        when:
        startBuild()
        waitForBuildToWait(2)

        then:
        busy 3

        when:
        completeBuild()

        then:
        state 2, 1

        when:
        completeBuild(1)
        completeBuild(2)

        then:
        state 0, 1
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(UnitTestPreconditions.NotWindows)
    @Ignore("TODO: Fix GradleHandle.abort() so that it doesn't hang")
    def "daemon stops immediately if stop is requested and then client disconnects"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        // Cause the daemon to want to stop
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        and:
        waitForDaemonExpiration()

        when:
        killBuild()

        then:
        stopped()
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
