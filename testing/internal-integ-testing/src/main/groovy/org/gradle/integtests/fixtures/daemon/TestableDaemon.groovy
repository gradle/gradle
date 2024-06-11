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

package org.gradle.integtests.fixtures.daemon

import org.gradle.internal.time.Time
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.util.GradleVersion

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.*

class TestableDaemon extends AbstractDaemonFixture {
    private final DaemonLogFileStateProbe logFileProbe
    private final DaemonRegistryStateProbe registryProbe

    TestableDaemon(DaemonLogFile daemonLog, DaemonRegistry registry, GradleVersion version) {
        super(daemonLog, version)
        this.logFileProbe = new DaemonLogFileStateProbe(daemonLog, context)
        this.registryProbe = new DaemonRegistryStateProbe(registry, context)
    }

    protected void waitForState(State state) {
        def timer = Time.startCountdownTimer(STATE_CHANGE_TIMEOUT)
        def lastRegistryState = registryProbe.currentState
        def lastLogState = logFileProbe.currentState
        while (!timer.hasExpired() && (lastRegistryState != state || lastLogState != state)) {
            Thread.sleep(200)
            lastRegistryState = registryProbe.currentState
            lastLogState = logFileProbe.currentState
        }
        if (lastRegistryState == state && lastLogState == state) {
            return
        }
        // TODO remove debug log
        if (lastRegistryState == State.Stopped && lastLogState == State.Idle) {
            println("The daemon probably disappeared before it could've printed the stop message. Daemon log content:")
            println(logFile.text)
        }

        throw new AssertionError("""Timeout waiting for daemon with pid ${context.pid} to reach state ${state}.
Current registry state is ${lastRegistryState} and current log state is ${lastLogState}.""")
    }

    @Override
    protected void assertHasState(State state) {
        assert logFileProbe.currentState == state
        assert registryProbe.currentState == state
    }

    @Override
    void assertRegistryNotWorldReadable() {
        registryProbe.assertRegistryNotWorldReadable()
    }

    @Override
    void changeTokenVisibleToClient() {
        registryProbe.resetToken()
    }

    @Override
    int getPort() {
        Pattern pattern = Pattern.compile("^.*" + DaemonMessages.ADVERTISING_DAEMON + ".*port:(\\d+).*",
            Pattern.MULTILINE + Pattern.DOTALL);

        Matcher matcher = pattern.matcher(daemonLog.text);
        assert matcher.matches(): "Unable to find daemon address in the daemon log. Daemon: $context"

        try {
            return Integer.parseInt(matcher.group(1))
        } catch (NumberFormatException e) {
            throw new RuntimeException("Unexpected format of the port number found in the daemon log. Daemon: $context")
        }
    }
}
