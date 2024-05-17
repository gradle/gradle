/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.api.DaemonConnection;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;

public class DaemonCommandExecuter {

    private final DaemonServerConfiguration configuration;
    private final ImmutableList<DaemonCommandAction> actions;

    public DaemonCommandExecuter(DaemonServerConfiguration configuration, ImmutableList<DaemonCommandAction> actions) {
        this.configuration = configuration;
        this.actions = actions;
    }

    /**
     * Handle the given command, and communicate as necessary with the client over the given connection.
     * <p>
     * If an error occurs during the action of the command that is to be reasonably expected
     * (e.g. a failure in actually running the build for a Build command), the exception should be
     * reported to the client and <b>NOT</b> thrown from this method.
     * <p>
     * The {@code command} param may be {@code null}, which means the client disconnected before sending a command.
     */
    public void executeCommand(DaemonConnection connection, Command command, DaemonContext daemonContext, DaemonStateControl daemonStateControl) {
        new DaemonCommandExecution(
            configuration,
            connection,
            command,
            daemonContext,
            daemonStateControl,
            actions
        ).proceed();
    }

}
