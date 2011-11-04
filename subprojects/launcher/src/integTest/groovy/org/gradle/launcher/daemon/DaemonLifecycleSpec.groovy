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

import org.gradle.util.GFileUtils
import org.gradle.integtests.fixtures.GradleHandles
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.server.DaemonIdleTimeout
import org.gradle.testing.AvailableJavaHomes
import org.gradle.util.Jvm

import org.junit.Rule
import spock.lang.*

import org.gradle.launcher.daemon.testing.DaemonEventSequenceBuilder


/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 * 
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
class DaemonLifecycleSpec extends Specification {

    @Rule public final GradleHandles handles = new GradleHandles()

    def buildCounter = 0
    def buildSleepsFor = 5
    def daemonIdleTimeout = 5

    // individual tests can set this to a list of string args that will be added to the client invocation
    def buildGradleOpts = []
    def foregroundDaemonGradleOpts = []

    // set this to change the java home used to launch any gradle, set back to null to use current JVM
    def javaHome = null
    
    @Delegate DaemonEventSequenceBuilder sequenceBuilder = new DaemonEventSequenceBuilder()

    def buildDir(buildScript) {
        def buildDir = handles.distribution.file("builds/${buildCounter++}")
        buildDir.file("build.gradle") << buildScript
        buildDir
    }

    def sleepyBuild(sleepFor = buildSleepsFor) {
        handles.createHandle {
            withTasks("sleep")
            addGradleOpts(new DaemonIdleTimeout(daemonIdleTimeout * 1000).toSysArg(), *buildGradleOpts)
            withArguments("--daemon", "--info")
            if (javaHome) {
                withJavaHome(javaHome)
            }
            usingProjectDirectory buildDir("""
                task('sleep') << {
                    println "about to sleep"
                    sleep ${sleepFor * 1000}
                }
            """)
        }.passthroughOutput()
    }

    def stopBuild() {
        handles.createHandle {
            withArguments("--stop", "--info")
            if (javaHome) {
                withJavaHome(javaHome)
            }
        }.passthroughOutput()
    }

    def foregroundDaemon() {
        handles.createHandle {
            addGradleOpts(*foregroundDaemonGradleOpts)
            if (javaHome) {
                withJavaHome(javaHome)
            }
            withArguments("--foreground")
        }.passthroughOutput()
    }

    boolean failed(handle) {
        assert handle.waitForFailure()
        true
    }

    boolean failedWithDaemonDisappearedMessage(build) {
        failed build
        assert build.errorOutput.contains(DaemonDisappearedException.MESSAGE)
        true
    }
    
    def setup() {
        handles.distribution.requireOwnUserHomeDir()
    }

    def
    "daemons do some work - sit idle - then timeout and die"() {
        when:
        run { sleepyBuild().start() }

        then:
        busy()

        and:
        idle()

        and:
        stopped()
    }

    def "existing idle daemons are used"() {
        when:
        run { foregroundDaemon().start() }

        then:
        idle()

        when:
        run { sleepyBuild(2).start() }

        then:
        busy()
    }

    def "a new daemon is started if all existing are busy"() {
        when:
        run { sleepyBuild().start() }

        then:
        busy()

        when:
        run { sleepyBuild().start() }

        then:
        busy(2)
    }

    def "sending stop to idle daemons causes them to terminate immediately"() {
        given:
        numDaemons 2

        when:
        run { 2.times { sleepyBuild(1).start() } }

        then:
        busy()

        and:
        idle()

        when:
        run { stopBuild().start() }

        then:
        stopped()
    }

    def "sending stop to busy daemons causes them to disappear from the registry"() {
        given:
        numDaemons 3

        when:
        run { 3.times { sleepyBuild(5).start() } }

        then:
        busy()

        when:
        run { stopBuild().start().waitForFinish() }

        then:
        stopped()
    }

    def "sending stop to busy daemons cause them to disappear from the registry and disconnect from the client, and terminates the daemon process"() {
        when:
        def daemon
        run { daemon = foregroundDaemon().start() }

        then:
        idle()

        when:
        def build
        run { build = sleepyBuild(10).start() }

        then:
        busy()

        when:
        run { stopBuild().start().waitForFinish() }

        then:
        stopped() // just means the daemon has disappeared from the registry

        then:
        run { failedWithDaemonDisappearedMessage build }
        // should check we get a nice error message here

        and:
        run { failed daemon }
    }

    def "tearing down client while daemon is building tears down daemon"() {
        when:
        def build
        run { build = sleepyBuild(20).start() }

        then:
        busy()

        when:
        run { build.abort().waitForFailure() }

        then:
        stopped()
    }

    def "tearing down client while daemon is building tears down daemon _process_"() {
        when:
        def daemon
        run { daemon = foregroundDaemon().start() }

        then:
        idle()

        when:
        def build
        run { build = sleepyBuild(20).start() }

        then:
        busy()

        when:
        run { build.abort().waitForFailure() }


        then:
        stopped() // just means the daemon has disappeared from the registry

        and:
        run { failed daemon }
    }

    def "tearing down daemon process produces nice error message for client"() {
        when:
        def daemon
        run { daemon = foregroundDaemon().start() }

        then:
        idle()

        when:
        def build
        run { build = sleepyBuild(10).start() }

        then:
        busy()

        when:
        run { daemon.abort().waitForFailure() }

        then:
        run { failedWithDaemonDisappearedMessage build }

        and:
        // The daemon crashed so didn't remove itself from the registry.
        // This doesn't produce a registry state change, so we have to test
        // That we are still in the same state this way
        run { assert handles.daemonRegistry.busy.size() == 1 }
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "if a daemon exists but is using a different java home, a new compatible daemon will be created and used"() {
        when:
        run {
            javaHome = AvailableJavaHomes.bestAlternative
            foregroundDaemon().start() 
            javaHome = null
        }

        then:
        idle()

        when:
        run { sleepyBuild().start() }
        numDaemons 2

        then:
        busy 1
    }

    def cleanup() {
        sequenceBuilder.build(handles.daemonRegistry).run()
        stopBuild().start().waitForFinish()
    }

}
