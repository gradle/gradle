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

import org.gradle.internal.time.Time
import org.gradle.util.GradleVersion

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State

class LegacyDaemon extends AbstractDaemonFixture {
    private final DaemonLogFileStateProbe logFileProbe

    LegacyDaemon(DaemonLogFile daemonLog, GradleVersion version) {
        super(daemonLog, version)
        if (version.baseVersion >= GradleVersion.version("2.2")) {
            logFileProbe = new DaemonLogFileStateProbe(daemonLog, context)
        } else {
            logFileProbe = new DaemonLogFileStateProbe(daemonLog, context, "Daemon is busy, sleeping until state changes", "Daemon is idle, sleeping until state change")
        }
    }

    protected void waitForState(State state) {
        def timer = Time.startCountdownTimer(STATE_CHANGE_TIMEOUT)
        def lastLogState = logFileProbe.currentState
        while (!timer.hasExpired() && lastLogState != state) {
            Thread.sleep(200)
            lastLogState = logFileProbe.currentState
        }
        if (lastLogState == state) {
            return
        }
        throw new AssertionError("""Timeout waiting for daemon with pid ${context.pid} to reach state ${state}.
Current state is ${lastLogState}.""")
    }

    @Override
    protected void assertHasState(State state) {
        assert logFileProbe.currentState == state
    }

    @Override
    void changeTokenVisibleToClient() {
        throw new UnsupportedOperationException()
    }

    @Override
    void assertRegistryNotWorldReadable() {
        throw new UnsupportedOperationException()
    }

    @Override
    int getPort() {
        throw new UnsupportedOperationException()
    }
}
