/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.BuildExceptionReporter;
import org.gradle.GradleLauncherFactory;
import org.gradle.StartParameter;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.launcher.protocol.Build;
import org.gradle.launcher.protocol.Command;
import org.gradle.launcher.protocol.CommandComplete;
import org.gradle.launcher.protocol.Stop;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.remote.internal.Connection;

/**
 * The server portion of the build daemon. See {@link org.gradle.launcher.DaemonClientAction} for a description of the
 * protocol.
 */
public class GradleDaemon implements Runnable {
    private static final Logger LOGGER = Logging.getLogger(Main.class);
    private final ServiceRegistry loggingServices;
    private final GradleLauncherFactory launcherFactory;

    public static void main(String[] args) {
        try {
            new GradleDaemon(new LoggingServiceRegistry()).run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    public GradleDaemon(ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
        launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void run() {
        DaemonConnector connector = new DaemonConnector();
        connector.accept(new IncomingConnectionHandler() {
            public void handle(Connection<Object> connection, Stoppable serverControl) {
                doRun(connection, serverControl);
            }
        });
    }

    private void doRun(final Connection<Object> connection, Stoppable serverControl) {
        ExecutionListenerImpl executionListener = new ExecutionListenerImpl();
        try {
            LoggingOutputInternal loggingOutput = loggingServices.get(LoggingOutputInternal.class);
            OutputEventListener listener = new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    connection.dispatch(event);
                }
            };

            // Perform as much as possible of the interaction while the logging is routed to the client
            loggingOutput.addOutputEventListener(listener);
            try {
                doRunWithLogging(connection, serverControl, executionListener);
            } finally {
                loggingOutput.removeOutputEventListener(listener);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Could not execute build.", throwable);
            executionListener.onFailure(throwable);
        }
        connection.dispatch(new CommandComplete(executionListener.failure));
    }

    private void doRunWithLogging(Connection<Object> connection, Stoppable serverControl, ExecutionListener executionListener) {
        Command command = (Command) connection.receive();
        try {
            doRunWithExceptionHandling(command, serverControl, executionListener);
        } catch (Throwable throwable) {
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), new StartParameter());
            exceptionReporter.reportException(throwable);
            executionListener.onFailure(throwable);
        }
    }

    private void doRunWithExceptionHandling(Command command, Stoppable serverControl, ExecutionListener executionListener) {
        LOGGER.info("Executing {}", command);
        if (command instanceof Stop) {
            LOGGER.lifecycle("Stopping");
            serverControl.stop();
            return;
        }

        assert command instanceof Build;
        build((Build) command, executionListener);
    }

    private void build(Build build, ExecutionListener executionListener) {
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(build.getCurrentDir());
        converter.convert(build.getArgs(), startParameter);
        LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
        loggingManager.setLevel(startParameter.getLogLevel());
        loggingManager.start();

        try {
            RunBuildAction action = new RunBuildAction(startParameter, loggingServices) {
                @Override
                GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
                    return launcherFactory;
                }
            };
            action.execute(executionListener);
        } catch (Throwable throwable) {
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), new StartParameter());
            exceptionReporter.reportException(throwable);
            executionListener.onFailure(throwable);
        }

        loggingManager.stop();
    }

    private static class ExecutionListenerImpl implements ExecutionListener {
        public Throwable failure;

        public void onFailure(Throwable failure) {
            this.failure = failure;
        }
    }
}
