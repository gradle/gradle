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
import java.net.ServerSocket

/**
 * Direct tests for the safety checks {@link DaemonStopper} performs before
 * calling {@code ProcessHandle.destroy(pid)}.
 */
class DaemonStopperSafetyTest extends Specification {

    def "isPortBound returns true when a listener is on the port"() {
        given:
        def server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

        expect:
        DaemonStopper.isPortBound("127.0.0.1", server.localPort)

        cleanup:
        server?.close()
    }

    def "isPortBound returns false when the port is unbound"() {
        given:
        // Bind+release to get a port number that is reliably free right now.
        def probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        def freePort = probe.localPort
        probe.close()

        expect:
        !DaemonStopper.isPortBound("127.0.0.1", freePort)
    }

    def "stop returns NOT_FOUND when context has no pid"() {
        given:
        def daemon = makeInfo(null, "127.0.0.1", 1)

        expect:
        new DaemonStopper().stop(daemon) == StopResult.NOT_FOUND
    }

    def "stop returns NOT_FOUND for a non-existent pid"() {
        given:
        def daemon = makeInfo(999_999_999L, "127.0.0.1", 1)

        expect:
        new DaemonStopper().stop(daemon) == StopResult.NOT_FOUND
    }

    def "stop returns NOT_FOUND when the registered port is unbound — even if pid is alive"() {
        given:
        // Use the current JVM's pid: alive, but does NOT look like a Gradle daemon
        // and its registered (fictional) port is unbound. We expect the port-bound
        // check (or the process-info check) to abort the kill.
        def myPid = ProcessHandle.current().pid()
        def probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        def freePort = probe.localPort
        probe.close()
        def daemon = makeInfo(myPid, "127.0.0.1", freePort)

        expect:
        new DaemonStopper().stop(daemon) == StopResult.NOT_FOUND

        and: "this JVM is still alive"
        ProcessHandle.current().isAlive()
    }

    def "stop returns NOT_FOUND when the process does not look like a Gradle daemon"() {
        given:
        // Run a server on a real port, but the JVM at this pid is the test runner,
        // not a Gradle daemon. The process-info check should reject it.
        def server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        def myPid = ProcessHandle.current().pid()
        def daemon = makeInfo(myPid, "127.0.0.1", server.localPort)

        expect:
        new DaemonStopper().stop(daemon) == StopResult.NOT_FOUND

        and: "this JVM is still alive"
        ProcessHandle.current().isAlive()

        cleanup:
        server?.close()
    }

    private static DaemonInfoView makeInfo(Long pid, String host, int port) {
        def ctx = new DaemonContextView(
            "test-uid",
            new File("/jdk"),
            21,
            "Vendor",
            pid,
            120_000,
            ["-Xmx512m"]
        )
        def address = new AddressView(InetAddress.getByName(host), port)
        return new DaemonInfoView(address, [0] as byte[], DaemonStateView.IDLE, 0L, ctx)
    }
}
