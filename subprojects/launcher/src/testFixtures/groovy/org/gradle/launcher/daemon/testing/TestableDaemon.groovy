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
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.process.internal.ExecHandleBuilder

import java.util.regex.Matcher
import java.util.regex.Pattern

class TestableDaemon implements DaemonFixture {
    private final DaemonContext context
    private final DaemonRegistry registry
    private final File log

    TestableDaemon(File daemonLog, DaemonRegistry registry) {
        this.log = daemonLog
        this.context = DaemonContextParser.parseFrom(log.text)
        this.registry = registry
    }

    void becomesIdle() {
        def expiry = System.currentTimeMillis() + 20000
        while (expiry > System.currentTimeMillis()) {
            if (registry.idle.find { it.context.pid == context.pid } != null) {
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
            if (registry.all.find { it.context.pid == context.pid } == null) {
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
        if (OperatingSystem.current().isUnix()) {
            return ["kill", "-9", pid]
        } else if (OperatingSystem.current().isWindows()) {
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
        assert getCurrentState() == State.idle
    }

    State getCurrentState() {
        getStates().last()
    }

    void assertBusy() {
        assert getCurrentState() == State.busy
    }

    void assertStopped() {
        assert getCurrentState() == State.stopped
    }

    List<State> getStates() {
        def states = new LinkedList<State>()
        states << State.idle
        log.eachLine {
            if (it.contains(DaemonMessages.STARTED_BUILD)) {
                states << State.busy
            } else if (it.contains(DaemonMessages.FINISHED_BUILD)) {
                states << State.idle
            } else if (it.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)) {
                states << State.stopped
            }
        }
        states
    }

    String getLog() {
        return log.text
    }

    int getPort() {
        Pattern pattern = Pattern.compile("^.*" + DaemonMessages.ADVERTISING_DAEMON + ".*port:(\\d+).*",
                Pattern.MULTILINE + Pattern.DOTALL);

        Matcher matcher = pattern.matcher(log.text);
        assert matcher.matches(): "Unable to find daemon address in the daemon log. Daemon: $context"

        try {
            return Integer.parseInt(matcher.group(1))
        } catch (NumberFormatException e) {
            throw new RuntimeException("Unexpected format of the port number found in the daemon log. Daemon: $context")
        }
    }
}