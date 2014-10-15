/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.testing

import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.process.internal.ExecHandleBuilder

class TestableDaemon implements DaemonFixture {
    private final DaemonContext context
    private final DaemonLogFileStateProbe logFileProbe
    private final DaemonRegistryStateProbe registryProbe

    TestableDaemon(File daemonLog, DaemonRegistry registry) {
        this.logFileProbe = new DaemonLogFileStateProbe(daemonLog)
        this.context = logFileProbe.context
        this.registryProbe = new DaemonRegistryStateProbe(registry, context)
    }

    void becomesIdle() {
        def expiry = System.currentTimeMillis() + 20000
        while (expiry > System.currentTimeMillis()) {
            if (registryProbe.currentState == State.idle) {
                assertIdle()
                return
            }
            Thread.sleep(200)
        }
        throw new AssertionError("Timeout waiting for daemon with pid ${context.pid} to become idle.")
    }

    void stops() {
        def expiry = System.currentTimeMillis() + 20000
        while (expiry > System.currentTimeMillis()) {
            if (registryProbe.currentState == State.stopped) {
                assertStopped()
                return
            }
            Thread.sleep(200)
        }
        throw new AssertionError("Timeout waiting for daemon with pid ${context.pid} to stop.")
    }

    /**
     * Forcefully kills this daemon.
     */
    void kill() {
        println "Killing daemon with pid: $context.pid"
        def output = new ByteArrayOutputStream()
        def e = new ExecHandleBuilder()
                .commandLine(killArgs(context.pid))
                .redirectErrorStream()
                .setStandardOutput(output)
                .workingDir(new File(".").absoluteFile) //does not matter
                .build()
        e.start()
        def result = e.waitForFinish()
        result.rethrowFailure()
    }

    private static Object[] killArgs(Long pid) {
        if (pid == null) {
            throw new RuntimeException("Unable to force kill the daemon because provided pid is null!")
        }
        if (OperatingSystem.current().unix) {
            return ["kill", "-9", pid]
        } else if (OperatingSystem.current().windows) {
            return ["taskkill.exe", "/F", "/T", "/PID", pid]
        } else {
            throw new RuntimeException("This implementation does not know how to forcefully kill the daemon on os: " + OperatingSystem.current())
        }
    }

    @SuppressWarnings("FieldName")
    enum State {
        busy, idle, stopped
    }

    void assertIdle() {
        assert logFileProbe.currentState == State.idle
        assert registryProbe.currentState == State.idle
    }

    void assertBusy() {
        assert logFileProbe.currentState == State.busy
        assert registryProbe.currentState == State.busy
    }

    void assertStopped() {
        assert logFileProbe.currentState == State.stopped
        assert registryProbe.currentState == State.stopped
    }

    String getLog() {
        return logFileProbe.log
    }

    int getPort() {
        return logFileProbe.port
    }
}