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

import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.server.DaemonStateCoordinator;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DisconnectAwareConnectionDecorator;

/**
 * The default implementation of how to execute commands that the daemon receives.
 */
public class DefaultDaemonCommandExecuter implements DaemonCommandExecuter {

    final private ServiceRegistry loggingServices;
    private final ExecutorFactory executorFactory;
    final private LoggingOutputInternal loggingOutput;
    final private GradleLauncherFactory launcherFactory;

    public DefaultDaemonCommandExecuter(ServiceRegistry loggingServices, ExecutorFactory executorFactory) {
        this.loggingServices = loggingServices;
        this.executorFactory = executorFactory;
        this.loggingOutput = loggingServices.get(LoggingOutputInternal.class);
        this.launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void executeCommand(Connection<Object> connection, Command command, DaemonStateCoordinator daemonStateCoordinator) {
        new DaemonCommandExecution(
            new DisconnectAwareConnectionDecorator<Object>(connection, executorFactory.create("DefaultDaemonCommandExecuter > DisconnectAwareConnectionDecorator")),
            command,
            daemonStateCoordinator,
            new StopConnectionAfterExecution(),
            new HandleClientDisconnectBeforeSendingCommand(),
            new CatchAndForwardDaemonFailure(),
            new HandleStop(),
            new UpdateDaemonStateAndHandleBusyDaemon(),
            new ReturnResult(),
            new ForwardOutput(loggingOutput),
            new ResetDeprecationLogger(),
            new ReportExceptions(loggingServices.get(StyledTextOutputFactory.class)),
            new HandleSleep(),
            new EstablishBuildEnvironment(),
            new WatchForDisconnection(),
            new ForwardClientInput(executorFactory),
            new ExecuteBuild(loggingServices, launcherFactory)
        ).proceed();
    }

}