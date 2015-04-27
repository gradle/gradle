/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon

import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.process.internal.ExecHandleBuilder

abstract class AbstractDaemonFixture implements DaemonFixture {
    public static final int STATE_CHANGE_TIMEOUT = 20000
    final DaemonContext context

    AbstractDaemonFixture(File daemonLog) {
        this.context = DaemonContextParser.parseFrom(daemonLog.text)
        if (this.context.pid == null) {
            println "PID in daemon log ($daemonLog.absolutePath) is null."
            println "daemon.log exists: ${daemonLog.exists()}"

            println "start daemon.log content: "
            println "{daemonLog.text.isEmpty()}) = ${daemonLog.text.isEmpty()})"
            println daemonLog.text;
            println "end daemon.log content"

        }
    }

    void becomesIdle() {
        waitForState(State.idle)
    }

    void stops() {
        waitForState(State.stopped)
    }

    @Override
    void assertIdle() {
        assertHasState(State.idle)
    }

    @Override
    void assertBusy() {
        assertHasState(State.busy)
    }

    @Override
    void assertStopped() {
        assertHasState(State.stopped)
    }

    protected abstract void waitForState(State state)

    protected abstract void assertHasState(State state)

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
}