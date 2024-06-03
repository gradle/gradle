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

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Busy
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Canceled
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Stopped

class DaemonLogFileStateProbe implements DaemonStateProbe {
    private final DaemonContext context
    private final DaemonLogFile log
    private final String startBuildMessage
    private final String finishBuildMessage

    DaemonLogFileStateProbe(DaemonLogFile daemonLog, DaemonContext context, String startBuildMessage = DaemonMessages.STARTED_BUILD, String finishBuildMessage = DaemonMessages.FINISHED_BUILD) {
        this.finishBuildMessage = finishBuildMessage
        this.startBuildMessage = startBuildMessage
        this.log = daemonLog
        this.context = context
    }

    @Override
    String toString() {
        return "DaemonLogFileStateProbe{file: ${log.file}, context: ${context}}"
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
        log.lines().withCloseable { stream ->
            stream.forEach {
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
        }
        states
    }
}
