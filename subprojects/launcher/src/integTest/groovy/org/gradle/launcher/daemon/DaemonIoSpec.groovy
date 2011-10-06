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
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.integtests.fixtures.GradleHandles

import org.junit.Rule
import spock.lang.*

/**
 * Tests the daemons IO handling WRT the client.
 *
 * Needs more tests.
 */
class DaemonIoSpec extends Specification {

    @Rule public final GradleHandles handles = new GradleHandles()

    def buildCounter = 0
    def daemonIdleTimeout = 5
    def pollWaitsFor = 10

    def daemons // have to set this in setup, after we have changed the user home location

    def source = new PipedOutputStream()
    def inputStream = new PipedInputStream(source)

    def buildDir(buildScript) {
        def buildDir = handles.distribution.file("builds/${buildCounter++}")
        buildDir.file("build.gradle") << buildScript
        buildDir
    }

    def echoBuild() {
        handles.createHandle {
            withTasks("echo")
            withEnvironmentVars(GRADLE_OPTS: new DaemonIdleTimeout(daemonIdleTimeout * 1000).toSysArg())
            withArguments("--daemon", "--info")
            usingProjectDirectory buildDir("""
                task echo << {
                    System.in.withReader {
                        def line
                        while (line != "close") {
                            line = it.readLine() // readline will chomp the newline off the end
                            println "\$line"
                        }
                    }
                }
            """)
        }.setStandardInput(inputStream).passthroughOutput()
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

    def expectedDaemons = 1

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

    boolean failed(handle) {
        poll { assert handle.waitForFailure(); }
        true
    }

    def "clients input is forwarded to daemon"() {
        given:
        def inputText = "abc\n123\n"
        def echoBuild = echoBuild().start()
        def daemon = foregroundDaemon().start()

        expect:
        daemonsBusy

        and:
        !daemon.standardOutput.contains(inputText)

        when:
        source << inputText

        then:
        poll { assert daemon.standardOutput.contains(inputText) }
    }
}
