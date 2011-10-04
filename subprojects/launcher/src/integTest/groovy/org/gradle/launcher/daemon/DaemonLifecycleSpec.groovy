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

import org.gradle.launcher.daemon.server.DaemonIdleTimeout
import org.gradle.integtests.fixtures.GradleHandles

import org.junit.Rule
import spock.lang.*

/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 */
class DaemonLifecycleSpec extends Specification {

    @Rule public final GradleHandles handles = new GradleHandles()

    def buildCounter = 0
    def buildSleepsFor = 5
    def daemonIdleTimeout = 5
    def pollWaitsFor = 10

    def daemons // have to set this in setup, after we have changed the user home location

    def buildDir(buildScript) {
        def buildDir = handles.distribution.file("builds/${buildCounter++}")
        buildDir.file("build.gradle") << buildScript
        buildDir
    }


    def sleepyBuild(sleepFor = buildSleepsFor) {
        handles.createHandle {
            withTasks("sleep")
            withEnvironmentVars(GRADLE_OPTS: new DaemonIdleTimeout(daemonIdleTimeout * 1000).toSysArg())
            withArguments("--daemon", "--info")
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
        }.passthroughOutput()
    }

    def foregroundDaemon() {
        handles.createHandle {
            withArguments("--foreground")
        }.passthroughOutput()
    }

    def setup() {
        handles.distribution.requireOwnUserHomeDir()
        daemons = handles.daemonRegistry
    }

    //simplistic polling assertion. attempts asserting every x millis up to some max timeout
    void poll(int timeout = pollWaitsFor, Closure assertion) {
        int x = 0;
        timeout = timeout * 1000 // convert to m
        while(true) {
            try {
                assertion()
                return
            } catch (Throwable t) {
                Thread.sleep(100);
                x += 100;
                if (x > timeout) {
                    throw t;
                }
            }
        }
    }

    def getNumDaemons() { daemons.all.size() }
    def getNumBusyDaemons() { daemons.busy.size() }
    def getNumIdleDaemons() { daemons.idle.size() }

    def expectedDaemons = 3

    boolean isDaemonsStopped() {
        isDaemonsRunning(0)
    }

    boolean isDaemonsRunning(num = expectedDaemons) {
        poll { assert numDaemons == num; }
        true
    }

    boolean isDaemonsIdle(num = expectedDaemons) {
        poll { assert numIdleDaemons == num; }
        true
    }

    boolean isDaemonsBusy(num = expectedDaemons) {
        poll { assert numBusyDaemons == num; }
        true
    }

    def "daemons do some work - sit idle - then timeout and die"() {
        when:
        expectedDaemons.times { sleepyBuild().start() }

        then:
        daemonsBusy

        and:
        daemonsIdle

        and:
        daemonsStopped
    }

    def "sending stop to idle daemons causing them to terminate immediately"() {
        given:
        daemonIdleTimeout = 10 // long timeout so we know they don't stop from idle timeout

        when:
        expectedDaemons.times { sleepyBuild(1).start() }

        then:
        daemonsIdle

        when:
        stopBuild().start()

        then:
        daemonsStopped
    }

    def "sending stop to busy daemons causes them to disappear from the registry"() {
        given:
        daemonIdleTimeout = 10 // long timeout so we know they don't stop from idle timeout

        when:
        expectedDaemons.times { sleepyBuild(5).start() }

        then:
        daemonsBusy

        when:
        stopBuild().start().waitForFinish()

        then:
        daemonsStopped // daemon processes will still be running, but they have dropped their connection to the client, and disappeared from registry.
    }

    def "sending stop to buys daemons cause them to disappear from the registry and disconnect from the client, and terminates the daemon process"() {
        given:
        expectedDaemons = 1

        when:
        def daemon = foregroundDaemon().start()

        then:
        daemonsIdle

        when:
        def build = sleepyBuild(10).start()

        then:
        daemonsBusy

        when:
        stopBuild().start().waitForFinish()

        then:
        daemonsStopped // just means the daemon has disappeared from the registry

        then:
        poll { assert build.waitForFailure() }
        // should check we get a nice error message here

        and:
        /*
            This is not quite right. What happens here is that because we have started the daemon and used --foreground
            we have gone through Main.main() which explicitly calls exit(0) when the started action wraps up.
            
            When the daemon receives the stop command, it will allow the call to awaitStop in DaemonMain to progress while
            the build thread is still running. This means we end up getting back to Main.main() and it calls System.exit(0)
            which tears down the JVM. This is not the right behaviour and will be fixed soon.
        */
        poll { assert daemon.waitForFinish() }
    }

    def "tearing down client while daemon is building tears down daemon"() {
        given:
        expectedDaemons = 1

        when:
        def build = sleepyBuild(10).start()

        then:
        daemonsBusy

        when:
        build.abort()

        then:
        daemonsStopped
    }

    def "tearing down client while daemon is building tears down daemon _process_"() {
        given:
        expectedDaemons = 1

        when:
        def daemon = foregroundDaemon().start()

        then:
        daemonsIdle

        when:
        def build = sleepyBuild(10).start()

        then:
        daemonsBusy

        when:
        build.abort()

        then:
        daemonsStopped // just means the daemon has disappeared from the registry

        and:
        poll(2) { assert daemon.waitForFailure() }
    }

}
