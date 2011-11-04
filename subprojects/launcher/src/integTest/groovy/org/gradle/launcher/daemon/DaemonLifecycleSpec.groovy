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

import org.gradle.integtests.fixtures.GradleHandles
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.server.DaemonIdleTimeout
import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder
import org.gradle.testing.AvailableJavaHomes
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 * 
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
class DaemonLifecycleSpec extends Specification {

    @Rule public final GradleHandles handles = new GradleHandles()

    def daemonIdleTimeout = 5

    def builds = []
    def foregroundDaemons = []

    // set this to change the java home used to launch any gradle, set back to null to use current JVM
    def javaHome = null
    
    @Delegate DaemonEventSequenceBuilder sequenceBuilder = new DaemonEventSequenceBuilder()

    def buildDir(buildNum) {
        handles.distribution.file("builds/$buildNum")
    }

    def buildDirWithScript(buildNum, buildScript) {
        def dir = buildDir(buildNum)
        dir.file("build.gradle") << buildScript
        dir
    }

    void startBuild() {
        builds << handles.createHandle {
            withTasks("watch")
            addGradleOpts(new DaemonIdleTimeout(daemonIdleTimeout * 1000).toSysArg())
            withArguments("--daemon", "--info")
            if (javaHome) {
                withJavaHome(javaHome)
            }
            usingProjectDirectory buildDirWithScript(builds.size(), """
                task('watch') << {
                    println "waiting for stop file"
                    while(!file("stop").exists()) {
                        sleep 100
                    }
                    println 'noticed stop file, finishing'
                }
            """)
        }.passthroughOutput().start()
    }

    void completeBuild(buildNum = 0) {
        buildDir(buildNum).file("stop") << "stop"
    }

    void stopDaemons() {
        handles.createHandle {
            withArguments("--stop", "--info")
            if (javaHome) {
                withJavaHome(javaHome)
            }
        }.passthroughOutput().start().waitForFinish()
    }

    void startForegroundDaemon() {
        foregroundDaemons << handles.createHandle {
            if (javaHome) {
                withJavaHome(javaHome)
            }
            withArguments("--foreground")
        }.passthroughOutput().start()
    }

    void killForegroundDaemon(int num = 0) {
        foregroundDaemons[num].abort().waitForFailure()
    }

    void killBuild(int num = 0) {
        builds[num].abort().waitForFailure()
    }

    void buildFailed(int num = 0) {
        failed builds[num]
    }

    void foregroundDaemonFailed(int num = 0) {
        failed foregroundDaemons[num]
    }

    void failed(handle) {
        assert handle.waitForFailure()
    }

    boolean buildFailedWithDaemonDisappearedMessage(num = 0) {
        def build = builds[num]
        failed build
        assert build.errorOutput.contains(DaemonDisappearedException.MESSAGE)
    }

    def setup() {
        handles.distribution.requireOwnUserHomeDir()
    }

    def "daemons do some work - sit idle - then timeout and die"() {
        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { completeBuild() }

        then:
        idle()

        and:
        stopped()
    }

    def "existing idle daemons are used"() {
        when:
        run { startForegroundDaemon() }

        then:
        idle()

        when:
        run { startBuild() }

        then:
        busy()
    }

    def "a new daemon is started if all existing are busy"() {
        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { startBuild() }

        then:
        busy(2)
    }

    def "sending stop to idle daemons causes them to terminate immediately"() {
        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { completeBuild() }

        then:
        idle()

        when:
        run { stopDaemons() }

        then:
        stopped()
    }

    def "sending stop to busy daemons causes them to disappear from the registry"() {
        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { stopDaemons() }

        then:
        stopped()
    }

    def "sending stop to busy daemons cause them to disappear from the registry and disconnect from the client, and terminates the daemon process"() {
        when:
        run { startForegroundDaemon() }

        then:
        idle()

        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { stopDaemons() }

        then:
        stopped() // just means the daemon has disappeared from the registry

        then:
        run { buildFailedWithDaemonDisappearedMessage() }

        and:
        run { foregroundDaemonFailed() }
    }

    def "tearing down client while daemon is building tears down daemon"() {
        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { killBuild() }

        then:
        stopped()
    }

    def "tearing down client while daemon is building tears down daemon _process_"() {
        when:
        run { startForegroundDaemon() }

        then:
        idle()

        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { killBuild() }

        then:
        stopped() // just means the daemon has disappeared from the registry

        and:
        run { foregroundDaemonFailed() }
    }

    def "tearing down daemon process produces nice error message for client"() {
        when:
        run { startForegroundDaemon() }

        then:
        idle()

        when:
        run { startBuild() }

        then:
        busy()

        when:
        run { killForegroundDaemon() }

        then:
        run { buildFailedWithDaemonDisappearedMessage() }

        and:
        // The daemon crashed so didn't remove itself from the registry.
        // This doesn't produce a registry state change, so we have to test
        // That we are still in the same state this way
        run { assert handles.daemonRegistry.busy.size() == 1; }
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "if a daemon exists but is using a different java home, a new compatible daemon will be created and used"() {
        when:
        run {
            javaHome = AvailableJavaHomes.bestAlternative
            startForegroundDaemon()
            javaHome = null
        }

        then:
        idle()

        when:
        run { startBuild() }

        then:
        numDaemons 2
        busy 1
    }

    def cleanup() {
        sequenceBuilder.build(handles.daemonRegistry).run()
        stopDaemons()
    }

}
