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
import org.gradle.integtests.fixtures.GradleHandle
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.testing.DaemonContextParser
import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder
import spock.lang.IgnoreIf

import static org.gradle.tests.fixtures.ConcurrentTestUtil.poll

/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 *
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
class DaemonLifecycleSpec extends DaemonIntegrationSpec {

    int daemonIdleTimeout = 100
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
        distribution.file("builds/$buildNum")
    }

    def buildDirWithScript(buildNum, buildScript) {
        def dir = buildDir(buildNum)
        dir.file("build.gradle") << buildScript
        dir
    }

    void startBuild(String javaHome = null, String buildEncoding = null) {
        run {
            executer.withTasks("watch")
            executer.withArguments(
                    "-Dorg.gradle.daemon.idletimeout=${daemonIdleTimeout * 1000}",
                    "--info",
                    "-Dorg.gradle.jvmargs=-ea")
            if (javaHome) {
                executer.withJavaHome(javaHome)
            }
            if (buildEncoding) {
                executer.withDefaultCharacterEncoding(buildEncoding)
            }
            executer.usingProjectDirectory buildDirWithScript(builds.size(), """
                task('watch') << {
                    println "waiting for stop file"
                    long sanityCheck = System.currentTimeMillis() + 120000L
                    while(!file("stop").exists()) {
                        sleep 100
                        if (file("exit").exists()) {
                            System.exit(1)
                        }
                        if (System.currentTimeMillis() > sanityCheck) {
                            throw new RuntimeException("It seems the stop file was never created")
                        }
                    }
                    println 'noticed stop file, finishing'
                }
            """)
            builds << executer.start()
        }
        //TODO SF - figure out how to add waitForBuildToWait somewhere here
    }

    void completeBuild(buildNum = 0) {
        run {
            buildDir(buildNum).file("stop") << "stop"
        }
    }

    void waitForBuildToWait(buildNum = 0) {
        run {
            poll { assert builds[buildNum].standardOutput.contains("waiting for stop file"); }
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
            javaHome = AvailableJavaHomes.bestAlternative
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
        executer.withArguments("--foreground", "--info", "-Dorg.gradle.daemon.idletimeout=${daemonIdleTimeout * 1000}")
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

    void foregroundDaemonFailed(int num = 0) {
        run { failed foregroundDaemons[num] }
    }

    void failed(GradleHandle handle) {
        assert handle.waitForFailure()
    }

    void buildFailedWithDaemonDisappearedMessage(num = 0) {
        run {
            def build = builds[num]
            failed build
            assert build.errorOutput.contains(DaemonDisappearedException.MESSAGE)
        }
    }

    void daemonContext(num = 0, Closure assertions) {
        run { doDaemonContext(builds[num], assertions) }
    }

    void foregroundDaemonContext(num = 0, Closure assertions) {
        run { doDaemonContext(foregroundDaemons[num], assertions) }
    }

    void doDaemonContext(gradleHandle, Closure assertions) {
        DaemonContextParser.parseFrom(gradleHandle.standardOutput).with(assertions)
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

    def "existing idle background daemons are used"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        
        when:
        completeBuild()
        
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

    def "sending stop to busy daemons causes them to disappear from the registry"() {
        when:
        startBuild()

        then:
        busy()

        when:
        stopDaemons()

        then:
        stopped()
    }

    def "sending stop to busy daemons cause them to disappear from the registry and disconnect from the client, and terminates the daemon process"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()

        when:
        stopDaemons()

        then:
        stopped() // just means the daemon has disappeared from the registry

        then:
        buildFailedWithDaemonDisappearedMessage()

        and:
        foregroundDaemonFailed()
    }

    @IgnoreIf({OperatingSystem.current().windows})
    //(SF) On windows at the moment, we cannot reliably kill the client without waiting for the daemon to complete
    //It's because of the way windows handles pipes for child processes.
    //basically, process.waitFor() completes and you can get hold of the exit value,
    //however, the process still sits there blocked on reading the child process' outputs.
    //Next steps:
    // 1. We can revisit this problem once we solve the daemon feedback story and we have a jna process starter that is able to consume the inputs
    // 2. We can make this test working on java7 (because processbuilder in jre7 is more powerful)
    def "tearing down client while daemon is building tears down daemon"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()

        when:
        killBuild()

        then:
        stopped()
    }

    @IgnoreIf({OperatingSystem.current().windows})
    //See the comment in the previous test
    def "tearing down client while daemon is building tears down daemon _process_"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()

        when:
        killBuild()

        then:
        stopped() // just means the daemon has disappeared from the registry

        and:
        foregroundDaemonFailed()
    }

    def "tearing down daemon process produces nice error message for client"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()

        then:
        busy()

        when:
        disappearDaemon()

        then:
        buildFailedWithDaemonDisappearedMessage()

        and:
        // The daemon attempts to remove its address on shutdown
        run { assert executer.daemonRegistry.all.empty }
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null})
    def "if a daemon exists but is using a different java home, a new compatible daemon will be created and used"() {
        when:
        startForegroundDaemonWithAlternateJavaHome()

        then:
        idle()

        and:
        foregroundDaemonContext {
            assert javaHome == AvailableJavaHomes.bestAlternative
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

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null})
    def "can stop a daemon that is using a different java home"() {
        when:
        startForegroundDaemonWithAlternateJavaHome()

        then:
        idle()

        when:
        stopDaemons()

        then:
        stopped()
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
    
    def cleanup() {
        try {
            sequenceBuilder.build(executer.daemonRegistry).run()
        } finally {
            stopDaemonsNow()
        }
    }
}
