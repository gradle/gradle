/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.BuildStarted;
import org.gradle.launcher.daemon.protocol.DaemonUnavailable;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.daemon.server.api.DaemonStoppedException;
import org.gradle.launcher.daemon.server.api.DaemonUnavailableException;

/**
 * Updates the daemon idle/busy status, sending a DaemonUnavailable result back to the client if the daemon is busy.
 */
public class StartBuildOrRespondWithBusy extends BuildCommandOnly {
    
    private static final Logger LOGGER = Logging.getLogger(StartBuildOrRespondWithBusy.class);
    private final DaemonDiagnostics diagnostics;

    public StartBuildOrRespondWithBusy(DaemonDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    protected void doBuild(final DaemonCommandExecution execution, final Build build) {
        DaemonStateControl stateCoordinator = execution.getDaemonStateControl();

        try {
            Runnable command = new Runnable() {
                @Override
                public void run() {
                    LOGGER.info("Daemon is about to start building {}. Dispatching build started information...", build);
                    execution.getConnection().buildStarted(new BuildStarted(diagnostics));
                    execution.proceed();
                }
            };

            stateCoordinator.runCommand(command, execution.toString());
        } catch (DaemonUnavailableException e) {
            LOGGER.info("Daemon will not handle the command {} because is unavailable: {}", build, e.getMessage());
            execution.getConnection().daemonUnavailable(new DaemonUnavailable(e.getMessage()));
        } catch (DaemonStoppedException e) {
            execution.getConnection().completed(new Failure(e));
        }
    }
}
