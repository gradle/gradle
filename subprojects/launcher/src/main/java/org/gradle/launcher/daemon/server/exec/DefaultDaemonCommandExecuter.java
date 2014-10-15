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

import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.internal.LoggingOutputInternal;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * The default implementation of how to execute commands that the daemon receives.
 */
public class DefaultDaemonCommandExecuter implements DaemonCommandExecuter {
    private final LoggingOutputInternal loggingOutput;
    private final BuildActionExecuter<BuildActionParameters> actionExecuter;
    private final DaemonCommandAction hygieneAction;
    private final ProcessEnvironment processEnvironment;
    private final File daemonLog;

    public DefaultDaemonCommandExecuter(BuildActionExecuter<BuildActionParameters> actionExecuter, ProcessEnvironment processEnvironment,
                                        LoggingManagerInternal loggingOutput, File daemonLog, DaemonCommandAction hygieneAction) {
        this.processEnvironment = processEnvironment;
        this.daemonLog = daemonLog;
        this.loggingOutput = loggingOutput;
        this.actionExecuter = actionExecuter;
        this.hygieneAction = hygieneAction;
    }

    public void executeCommand(DaemonConnection connection, Command command, DaemonContext daemonContext, DaemonStateControl daemonStateControl) {
        new DaemonCommandExecution(
            connection,
            command,
            daemonContext,
            daemonStateControl,
            createActions(daemonContext)
        ).proceed();
    }

    protected List<DaemonCommandAction> createActions(DaemonContext daemonContext) {
        DaemonDiagnostics daemonDiagnostics = new DaemonDiagnostics(daemonLog, daemonContext.getPid());
        return new LinkedList<DaemonCommandAction>(Arrays.asList(
            new HandleCancel(),
            new ReturnResult(),
            new StartBuildOrRespondWithBusy(daemonDiagnostics), // from this point down, the daemon is 'busy'
            hygieneAction,
            new EstablishBuildEnvironment(processEnvironment),
            new LogToClient(loggingOutput, daemonDiagnostics), // from this point down, logging is sent back to the client
            new ForwardClientInput(),
            new StartStopIfBuildAndStop(),
            new ResetDeprecationLogger(),
            new WatchForDisconnection(),
            new ExecuteBuild(actionExecuter)
        ));
    }
}