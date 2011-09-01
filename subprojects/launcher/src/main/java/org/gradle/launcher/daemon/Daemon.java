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
package org.gradle.launcher.daemon;

import org.gradle.BuildExceptionReporter;
import org.gradle.StartParameter;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.ReportedException;
import org.gradle.launcher.DefaultGradleLauncherActionExecuter;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.protocol.*;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.Address;
import org.gradle.util.UncheckedException;

import java.io.*;
import java.util.Properties;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;

/**
 * A long-lived build server that accepts commands via a communication channel.
 * <p>
 * Daemon instances are single use and have a start/stop lifecycle. They are also threadsafe.
 * <p>
 * See {@link DaemonClient} for a description of the daemon communication protocol.
 */
public class Daemon implements Runnable, Stoppable {

    private static final Logger LOGGER = Logging.getLogger(Daemon.class);

    private final ServiceRegistry loggingServices;
    private final DaemonServerConnector connector;
    private final DaemonRegistry daemonRegistry;
    private final GradleLauncherFactory launcherFactory;

    private Address connectorAddress;
    final private CompletionHandler control;

    private final StoppableExecutor handlersExecutor = new DefaultExecutorFactory().create("Daemon Connection Handler");

    private boolean started;
    private boolean stopped;
    private final Lock lifecycleLock = new ReentrantLock();

    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final StoppableExecutor stopperExecutor = new DefaultExecutorFactory().create("Daemon Stopper");

    /**
     * Creates a new daemon instance.
     * 
     * @param loggingServices The service registry for logging services used by this daemon
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    public Daemon(ServiceRegistry loggingServices, DaemonServerConnector connector, DaemonRegistry daemonRegistry) {
        this.loggingServices = loggingServices;
        this.connector = connector;
        this.daemonRegistry = daemonRegistry;
        
        this.launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
        this.control = new CompletionHandler();
    }

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     * 
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    public void start() {
        lifecycleLock.lock();
        try {
            if (stopped) {
                throw new IllegalStateException("cannot start daemon as it has already been used");
            }
            if (started) {
                throw new IllegalStateException("cannot start daemon as it is already running");
            }

            // Get ready to accept connections, but we are assuming that no connections will be established
            // because we have not yet advertised that we are open for business by entering our address into
            // the registry, which happens a little further down in this method.
            connectorAddress = connector.start(new IncomingConnectionHandler() {
                public void handle(final Connection<Object> connection) {

                    //we're spinning a thread to do work to avoid blocking the connection
                    //This means that the Daemon potentially can have multiple jobs running.
                    //We only allow 2 threads max - one for the build, second for potential Stop request
                    handlersExecutor.execute(new Runnable() {
                        public void run() {
                            Command command = null;
                            try {
                                //There's a reason why we lock first and then read the incoming command
                                //This way we're marking the daemon as 'busy' as soon as it is connected to the client
                                //It does not have impact for daemon usage in real world but it makes certain things easier and it helps a lot with testing daemon
                                command = lockAndReceive(connection, control);
                                if (command == null) {
                                    LOGGER.warn("It seems the client dropped the connection before sending any command. Stopping connection.");
                                    unlock(control);
                                    connection.stop();
                                    return;
                                }
                            } catch (BusyException e) {
                                connection.dispatch(new CommandComplete(e));
                                return;
                            }
                            try {
                                doRun(connection, control, command);
                            } finally {
                                unlock(control);
                                connection.stop();
                            }
                        }
                    });
                }
            });

            control.setActivityListener(new CompletionHandler.ActivityListener() {
                public void onStart() {
                    daemonRegistry.markBusy(connectorAddress);
                }

                public void onComplete() {
                    daemonRegistry.markIdle(connectorAddress);
                }
            });

            // Start a new thread to watch the stop latch
            stopperExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        stopLatch.await();
                    } catch (InterruptedException e) {
                        // unsure what we can really do here, it shouldn't happen anyway.
                        return;
                    }
                    
                    daemonRegistry.remove(connectorAddress); // remove our presence to clients
                    connector.stop(); // stop accepting new connections
                    handlersExecutor.stop(); // wait for any connection handlers to stop (though connector.stop() will have already waited for this)
                    control.stop(); // wake up anyone waiting on our completion (i.e. )
                }
            });
            
            // Advertise that the daemon is now ready to accept connections
            daemonRegistry.store(connectorAddress);
            control.start();
            started = true;
            LOGGER.lifecycle("Daemon started at: " + new Date() + ", with address: " + connectorAddress);
        } catch (Exception e) {
            LOGGER.warn("exception starting daemon", e);
            stopLatch.countDown();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Stops the daemon, blocking until any current requests/connections have been satisfied.
     * <p>
     * This is the semantically the same as sending the daemon the Stop command.
     */
    public void stop() {
        lifecycleLock.lock();
        try {
            stopLatch.countDown();
            stopperExecutor.stop();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Blocks until this daemon is stopped by something else (i.e. does not ask it to stop)
     */
    public void awaitStop() {
        control.awaitStop();
    }

    /**
     * Waits until the daemon is either stopped, or has been idle for the given number of milliseconds.
     *
     * @return true if it was stopped, false if it hit the given idle timeout.
     */
    public boolean awaitStopOrIdleTimeout(int idleTimeout) {
        return control.awaitStopOrIdleTimeout(idleTimeout);
    }

    /**
     * Starts the daemon, blocking until it is stopped (either by Stop command or by another thread calling stop())
     */
    public void run() {
        start();
        awaitStop();
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
                        //Ignore. It means the client has disconnected so no point sending him any log output.
                        //we should be checking if client still listens elsewhere anyway.
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
            stopLatch.countDown();
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
