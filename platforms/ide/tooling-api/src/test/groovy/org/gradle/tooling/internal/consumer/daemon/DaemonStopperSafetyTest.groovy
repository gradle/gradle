/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon

import org.gradle.tooling.daemon.StopResult
import spock.lang.Specification

import java.net.InetAddress

/**
 * Direct tests for the safety checks {@link DaemonStopper} performs before
 * calling {@code ProcessHandle.destroy(pid)}. All checks go through
 * {@link DaemonAlivenessProbe} in STRICT mode.
 */
class DaemonStopperSafetyTest extends Specification {

    def "stop returns NOT_FOUND when context has no pid"() {
        expect:
        new DaemonStopper().stop(makeInfo(null)) == StopResult.NOT_FOUND
    }

    def "stop returns NOT_FOUND for a non-existent pid"() {
        expect:
        new DaemonStopper().stop(makeInfo(999_999_999L)) == StopResult.NOT_FOUND
    }

    def "stop returns NOT_FOUND when pid is alive but is not a Gradle daemon"() {
        given:
        // Use the current JVM's pid: alive, has command-line info (we're a JVM
        // running tests, not a daemon). The main-class identity check must reject
        // it and NOT signal this JVM.
        def myPid = ProcessHandle.current().pid()

        when:
        def result = new DaemonStopper().stop(makeInfo(myPid))

        then:
        result == StopResult.NOT_FOUND

        and: "this JVM is still alive after the stop attempt"
        ProcessHandle.current().isAlive()
    }

    def "DaemonAlivenessProbe in STRICT mode returns null for non-daemon PID"() {
        given:
        def myPid = ProcessHandle.current().pid()

        expect:
        DaemonAlivenessProbe.verifiedHandle(makeInfo(myPid), DaemonAlivenessProbe.Mode.STRICT) == null
        !DaemonAlivenessProbe.isAlive(makeInfo(myPid), DaemonAlivenessProbe.Mode.STRICT)
    }

    def "DaemonAlivenessProbe returns null for dead pid in both modes"() {
        given:
        def info = makeInfo(999_999_999L)

        expect:
        DaemonAlivenessProbe.verifiedHandle(info, DaemonAlivenessProbe.Mode.STRICT) == null
        DaemonAlivenessProbe.verifiedHandle(info, DaemonAlivenessProbe.Mode.LENIENT) == null
    }

    private static DaemonInfoView makeInfo(Long pid) {
        def ctx = new DaemonContextView(
            "test-uid",
            new File("/jdk"),
            21,
            "Vendor",
            pid,
            120_000,
            ["-Xmx512m"]
        )
        def address = new AddressView(InetAddress.getByName("127.0.0.1"), 1)
        return new DaemonInfoView(address, [0] as byte[], DaemonStateView.IDLE, 0L, ctx)
    }
}
