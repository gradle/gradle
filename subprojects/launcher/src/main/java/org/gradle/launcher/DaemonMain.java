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
import org.gradle.StartParameter;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.protocol.*;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.util.UncheckedException;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

/**
 * The server portion of the build daemon. See {@link DaemonClient} for a description of the protocol.
 */
public class DaemonMain implements Runnable {
    private static final Logger LOGGER = Logging.getLogger(Main.class);
    private final ServiceRegistry loggingServices;
    private final DaemonServer server;
    private final StartParameter startParameter;
    private final GradleLauncherFactory launcherFactory;

    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();

    public DaemonMain(ServiceRegistry loggingServices, DaemonServer server, StartParameter startParameter) {
        this.loggingServices = loggingServices;
        this.server = server;
        this.startParameter = startParameter;
        this.launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public static void main(String[] args) throws IOException {
        StartParameter startParameter = new DefaultCommandLineConverter().convert(Arrays.asList(args));
        DaemonServer server = new DaemonServer(new PersistentDaemonRegistry(startParameter.getGradleUserHomeDir()));
        redirectOutputsAndInput(startParameter);
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newChildProcessLogging();
        new DaemonMain(loggingServices, server, startParameter).run();
    }

    private static void redirectOutputsAndInput(StartParameter startParameter) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
//        InputStream originalIn = System.in;
        DaemonDir daemonDir = new DaemonDir(startParameter.getGradleUserHomeDir());
        File logOutputFile = daemonDir.createUniqueLog();
        logOutputFile.getParentFile().mkdirs();
        PrintStream printStream = new PrintStream(new FileOutputStream(logOutputFile), true);
        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        originalOut.close();
        originalErr.close();
        // TODO - make this work on windows
//        originalIn.close();
    }

    public void run() {
        //TODO SF - very simple/no validation
        String timeoutProperty = startParameter.getSystemPropertiesArgs().get(DaemonTimeout.TIMEOUT_PROPERTY);
        int idleTimeout = (timeoutProperty != null)? Integer.parseInt(timeoutProperty) : 3 * 60 * 60 * 1000;
        LOGGER.info("Daemon idle timeout is configured to: " + idleTimeout/1000 + " secs");

        final StoppableExecutor executor = executorFactory.create("DaemonMain executor");

        server.accept(idleTimeout, new IncomingConnectionHandler() {
            public void handle(final Connection<Object> connection, final CompletionHandler serverControl) {
                //we're spinning a thread to do work to avoid blocking the connection
                //This means that the Daemon potentially can have multiple jobs running.
                //We only allow 2 threads max - one for the build, second for potential Stop request
                executor.execute(new Runnable() {
                    public void run() {
                        Command command = null;
                        try {
                            command = lockAndReceive(connection, serverControl);
                            if (command == null) {
                                LOGGER.warn("It seems the client dropped the connection before sending any command. Stopping connection.");
                                unlock(serverControl);  //TODO SF - if receiving is first we don't need this really
                                connection.stop();
                                return;
                            }
                        } catch (BusyException e) {
                            connection.dispatch(new CommandComplete(e));
                            return;
                        }
                        try {
                            doRun(connection, serverControl, command);
                        } finally {
                            unlock(serverControl);
                            connection.stop();
                        }
                    }
                });
            }
        });

        executorFactory.stop();
    }

    private void unlock(CompletionHandler serverControl) {
        serverControl.onActivityComplete();
    }

    private void doRun(final Connection<Object> connection, CompletionHandler serverControl, Command command) {
        CommandComplete result = null;
        Throwable failure = null;
        try {
            LoggingOutputInternal loggingOutput = loggingServices.get(LoggingOutputInternal.class);
            OutputEventListener listener = new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    try {
                        connection.dispatch(event);
                    } catch (Exception e) {
                        //TODO SF we need handling for this. It means the client disconnected
                    }
                }
            };

            // Perform as much as possible of the interaction while the logging is routed to the client
            loggingOutput.addOutputEventListener(listener);
            try {
                result = doRunWithLogging(serverControl, command);
            } finally {
                loggingOutput.removeOutputEventListener(listener);
            }
         } catch (ReportedException e) {
            failure = e;
        } catch (Throwable throwable) {
            LOGGER.error("Could not execute build.", throwable);
            failure = throwable;
        }
        if (failure != null) {
            result = new CommandComplete(UncheckedException.asUncheckedException(failure));
        }
        assert result != null;
        connection.dispatch(result);
    }

    private Command lockAndReceive(Connection<Object> connection, CompletionHandler serverControl) {
        try {
            //TODO SF - receiving can be first and the logic gets simpler
            serverControl.onStartActivity();
            return (Command) connection.receive();
        } catch (BusyException busy) {
            Command command = (Command) connection.receive();
            if (command instanceof Stop) {
                //that's ok, if the daemon is busy we still want to be able to stop it
                LOGGER.info("The daemon is busy and Stop command was received. Stopping...");
                return command;
            }
            //otherwise it is a build request and we are already busy
            LOGGER.info("The daemon is busy and another build request received. Returning Busy response.");
            throw busy;
        }
    }

    private CommandComplete doRunWithLogging(Stoppable serverControl, Command command) {
        try {
            return doRunWithExceptionHandling(command, serverControl);
        } catch (ReportedException e) {
            throw e;
        } catch (Throwable throwable) {
            StyledTextOutputFactory outputFactory = loggingServices.get(StyledTextOutputFactory.class);
            BuildClientMetaData clientMetaData = command.getClientMetaData();
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(outputFactory, new StartParameter(), clientMetaData);
            exceptionReporter.reportException(throwable);
            throw new ReportedException(throwable);
        }
    }

    private CommandComplete doRunWithExceptionHandling(Command command, Stoppable serverControl) {
        LOGGER.info("Executing {}", command);
        if (command instanceof Stop) {
            LOGGER.lifecycle("Stopping");
            serverControl.stop();
            return new CommandComplete(null);
        }

        return build((Build) command);
    }

    private Result build(Build build) {
        Properties originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
        Properties clientSystemProperties = new Properties();
        clientSystemProperties.putAll(build.getParameters().getSystemProperties());
        System.setProperties(clientSystemProperties);
        try {
            DefaultGradleLauncherActionExecuter executer = new DefaultGradleLauncherActionExecuter(launcherFactory, loggingServices);
            Object result = executer.execute(build.getAction(), build.getParameters());
            return new Result(result);
        } finally {
            System.setProperties(originalSystemProperties);
        }
    }
}
