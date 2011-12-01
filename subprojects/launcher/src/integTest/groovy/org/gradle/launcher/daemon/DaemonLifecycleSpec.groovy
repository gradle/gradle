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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleHandles
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.server.DaemonIdleTimeout
import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder
import org.gradle.os.OperatingSystem
import org.gradle.testing.AvailableJavaHomes
import org.gradle.util.Jvm
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification
import static org.gradle.integtests.fixtures.GradleDistributionExecuter.Executer.daemon
import static org.gradle.util.ConcurrentSpecification.poll

/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 *
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
@IgnoreIf({OperatingSystem.current().windows})
class DaemonLifecycleSpec extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter(daemon)
    @Rule public final GradleHandles handles = new GradleHandles()

    def daemonIdleTimeout = 5

    def builds = []
    def foregroundDaemons = []

    // set this to change the java home used to launch any gradle, set back to null to use current JVM
    def javaHome = null

    @Delegate DaemonEventSequenceBuilder sequenceBuilder = new DaemonEventSequenceBuilder()

    def buildDir(buildNum) {
        distribution.file("builds/$buildNum")
    }

    def buildDirWithScript(buildNum, buildScript) {
        def dir = buildDir(buildNum)
        dir.file("build.gradle") << buildScript
        dir
    }

    void startBuild() {
        run {
            builds << handles.createHandle {
                withTasks("watch")
                withArguments(DaemonIdleTimeout.toCliArg(daemonIdleTimeout * 1000), "--info")
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
            }.start()
        }
    }

    void completeBuild(buildNum = 0) {
        run { buildDir(buildNum).file("stop") << "stop" }
    }

    void waitForBuildToWait(buildNum = 0) {
        run { poll { assert builds[buildNum].standardOutput.contains("waiting for stop file"); } }
    }

    void stopDaemons() {
        run { stopDaemonsNow() }
    }

    void stopDaemonsNow() {
        handles.createHandle {
            withArguments("--stop", "--info")
            if (javaHome) {
                withJavaHome(javaHome)
            }
        }.start().waitForFinish()
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

    void startForegroundDaemonNow() {
        foregroundDaemons << handles.createHandle {
            if (javaHome) {
                withJavaHome(javaHome)
            }
            withArguments("--foreground", "--info")
        }.start()
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

    void failed(handle) {
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
        DefaultDaemonContext.parseFrom(gradleHandle.standardOutput).with(assertions)
    }

    def setup() {
        distribution.requireOwnUserHomeDir()
    }

    def "daemons do some work - sit idle - then timeout and die"() {
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

    def "existing idle daemons are used"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()

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

    def "tearing down client while daemon is building tears down daemon"() {
        when:
        startBuild()

        then:
        busy()

        when:
        killBuild()

        then:
        stopped()
    }

    def "tearing down client while daemon is building tears down daemon _process_"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()

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
        killForegroundDaemon()

        then:
        buildFailedWithDaemonDisappearedMessage()

        and:
        // The daemon crashed so didn't remove itself from the registry.
        // This doesn't produce a registry state change, so we have to test
        // That we are still in the same state this way
        run { assert handles.daemonRegistry.busy.size() == 1; }
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
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

    def cleanup() {
        try {
            sequenceBuilder.build(handles.daemonRegistry).run()
        } finally {
            new DaemonEventSequenceBuilder().with {
                stopDaemons()
                build(handles.daemonRegistry)
            }.run()
        }
    }

}
