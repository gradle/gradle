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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.GradleLauncherFactory;
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
import org.gradle.util.GradleVersion;
import org.gradle.util.UncheckedException;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

/**
 * The server portion of the build daemon. See {@link DaemonClientAction} for a description of the
 * protocol.
 */
public class DaemonMain implements Runnable {
    private static final Logger LOGGER = Logging.getLogger(Main.class);
    private final ServiceRegistry loggingServices;
    private final DaemonConnector connector;
    private final GradleLauncherFactory launcherFactory;

    public DaemonMain(ServiceRegistry loggingServices, DaemonConnector connector) {
        this.loggingServices = loggingServices;
        this.connector = connector;
        launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public static void main(String[] args) throws IOException {
        StartParameter startParameter = new DefaultCommandLineConverter().convert(Arrays.asList(args));
        DaemonConnector connector = new DaemonConnector(startParameter.getGradleUserHomeDir());
        LoggingServiceRegistry loggingServices = new LoggingServiceRegistry();
        addLogFileWriters(startParameter, loggingServices);
        new DaemonMain(loggingServices, connector).run();
    }

    private static void addLogFileWriters(StartParameter startParameter, LoggingServiceRegistry loggingServices) throws IOException {
        File stderrOut = new File(startParameter.getGradleUserHomeDir(), String.format("daemon/%s/daemon.err.log", GradleVersion.current().getVersion()));
        stderrOut.getParentFile().mkdirs();
        final Writer writer = new BufferedWriter(new FileWriter(stderrOut));
        loggingServices.get(LoggingOutputInternal.class).addStandardErrorListener(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                try {
                    writer.append(output);
                    writer.flush();
                } catch (IOException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
        });
    }

    public void run() {
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
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), new StartParameter(), command.getClientMetaData());
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

        Properties sysProperties = new Properties();
        sysProperties.putAll(System.getProperties());

        try {
            RunBuildAction action = new RunBuildAction(startParameter, loggingServices, new DefaultBuildRequestMetaData(build.getClientMetaData(), build.getStartTime())) {
                @Override
                GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
                    return launcherFactory;
                }
            };
            action.execute(executionListener);
        } catch (Throwable throwable) {
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), new StartParameter(), build.getClientMetaData());
            exceptionReporter.reportException(throwable);
            executionListener.onFailure(throwable);
        }

        loggingManager.stop();

        System.setProperties(sysProperties);
    }

    private static class ExecutionListenerImpl implements ExecutionListener {
        public Throwable failure;

        public void onFailure(Throwable failure) {
            this.failure = failure;
        }
    }
}
