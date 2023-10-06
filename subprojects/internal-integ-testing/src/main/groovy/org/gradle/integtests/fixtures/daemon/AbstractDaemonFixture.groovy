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

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.server.api.DaemonStateControl.State

import java.nio.file.Files

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Busy
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Canceled
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Stopped

abstract class AbstractDaemonFixture implements DaemonFixture {
    public static final int STATE_CHANGE_TIMEOUT = 20000
    final DaemonContext context

    AbstractDaemonFixture(File daemonLog) {
        this.context = DaemonContextParser.parseFromFile(daemonLog)
        if (!this.context) {
            println "Could not parse daemon log: \n$daemonLog.text"
            throw new IllegalStateException("unable to parse DefaultDaemonContext from source: [${daemonLog.absolutePath}].");
        }
        if (this.context?.pid == null) {
            println "PID in daemon log ($daemonLog.absolutePath) is null."
            println "daemon.log exists: ${daemonLog.exists()}"

            println "start daemon.log content: "
            println "{daemonLog.text.isEmpty()}) = ${daemonLog.text.isEmpty()})"
            println daemonLog.text
            println "end daemon.log content"

        }
    }

    DaemonContext getContext() {
        context
    }

    @Override
    boolean logContains(String searchString) {
        logContains(0, searchString)
    }

    @Override
    boolean logContains(long fromLine, String searchString) {
        Files.lines(logFile.toPath()).withCloseable { lines ->
            lines.skip(fromLine).anyMatch { it.contains(searchString) }
        }
    }

    @Override
    long getLogLineCount() {
        return Files.lines(logFile.toPath()).withCloseable { lines -> lines.count() }
    }

    void becomesIdle() {
        waitForState(Idle)
    }

    void stops() {
        waitForState(Stopped)
    }

    @Override
    void assertIdle() {
        assertHasState(Idle)
    }

    @Override
    void assertBusy() {
        assertHasState(Busy)
    }

    @Override
    void assertStopped() {
        assertHasState(Stopped)
    }

    @Override
    void assertCanceled() {
        assertHasState(Canceled)
    }

    @Override
    void becomesCanceled() {
        waitForState(Canceled)
    }

    protected abstract void waitForState(State state)

    protected abstract void assertHasState(State state)

    @Override
    void kill() {
        new ProcessFixture(context.pid).kill(true)
    }

    @Override
    void killDaemonOnly() {
        new ProcessFixture(context.pid).kill(false)
    }

    @Override
    String toString() {
        "Daemon with context $context"
    }
}
