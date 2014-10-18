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

package org.gradle.launcher.daemon.server.exec;

import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.protocol.Success;

public class StopHandlingCommandExecuter implements DaemonCommandExecuter {
    private final DaemonCommandExecuter executer;

    public StopHandlingCommandExecuter(DaemonCommandExecuter executer) {
        this.executer = executer;
    }

    public void executeCommand(DaemonConnection connection, Command command, DaemonContext daemonContext, DaemonStateControl daemonStateControl) {
        if (command instanceof Stop) {
            daemonStateControl.requestForcefulStop();
            connection.completed(new Success(null));
        } else if (command instanceof StopWhenIdle) {
            daemonStateControl.requestStop();
            connection.completed(new Success(null));
        } else {
            executer.executeCommand(connection, command, daemonContext, daemonStateControl);
        }
    }
}
