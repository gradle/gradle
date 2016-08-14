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

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.logging.DaemonMessages

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.*
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*

class DaemonLogFileStateProbe implements DaemonStateProbe {
    private final DaemonContext context
    private final File log
    private final String startBuildMessage
    private final String finishBuildMessage

    DaemonLogFileStateProbe(File daemonLog, DaemonContext context, String startBuildMessage = DaemonMessages.STARTED_BUILD, String finishBuildMessage = DaemonMessages.FINISHED_BUILD) {
        this.finishBuildMessage = finishBuildMessage
        this.startBuildMessage = startBuildMessage
        this.log = daemonLog
        this.context = context
    }

    @Override
    String toString() {
        return "DaemonLogFile{file: ${log}, context: ${context}}"
    }

    DaemonContext getContext() {
        return context
    }

    State getCurrentState() {
        getStates().last()
    }

    List<State> getStates() {
        def states = new LinkedList<State>()
        states << Idle
        log.eachLine {
            if (it.contains(startBuildMessage)) {
                states << Busy
            } else if (it.contains(finishBuildMessage)) {
                states << Idle
            } else if (it.contains(DaemonMessages.CANCELED_BUILD)) {
                states << Canceled
            } else if (it.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)) {
                states << Stopped
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
