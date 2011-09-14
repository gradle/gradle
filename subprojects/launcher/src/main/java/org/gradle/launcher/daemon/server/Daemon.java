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
package org.gradle.launcher.daemon.server;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.BusyException;
import org.gradle.launcher.daemon.protocol.CommandComplete;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.Connection;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A long-lived build server that accepts commands via a communication channel.
 * <p>
 * Daemon instances are single use and have a start/stop lifecycle. They are also threadsafe.
 * <p>
 * See {@link org.gradle.launcher.daemon.client.DaemonClient} for a description of the daemon communication protocol.
 */
public class Daemon implements Runnable, Stoppable {

    private static final Logger LOGGER = Logging.getLogger(Daemon.class);

    private final DaemonServerConnector connector;
    private final DaemonRegistry daemonRegistry;
    private final DaemonCommandExecuter commandExecuter;

    private final DaemonStateCoordinator stateCoordinator = new DaemonStateCoordinator();

    private final StoppableExecutor handlersExecutor = new DefaultExecutorFactory().create("Daemon Connection Handler");

    private final Lock lifecycleLock = new ReentrantLock();

    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final StoppableExecutor stopperExecutor = new DefaultExecutorFactory().create("Daemon Stopper");

    private Address connectorAddress;
    private DomainRegistryUpdater registryUpdater;

    /**
     * Creates a new daemon instance.
     * 
     * @param loggingServices The service registry for logging services used by this daemon
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    public Daemon(DaemonServerConnector connector, DaemonRegistry daemonRegistry, DaemonCommandExecuter commandExecuter) {
        this.connector = connector;
        this.daemonRegistry = daemonRegistry;
        this.commandExecuter = commandExecuter;
    }

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     * 
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    public void start() {
        lifecycleLock.lock();
        try {
            if (stateCoordinator.isStarted()) {
                throw new IllegalStateException("cannot start daemon as it is already running");
            }

            // Get ready to accept connections, but we are assuming that no connections will be established
            // because we have not yet advertised that we are open for business by entering our address into
            // the registry, which happens a little further down in this method.
            connectorAddress = connector.start(new IncomingConnectionHandler() {
                public void handle(final Connection<Object> connection) {

                    //we're spinning a thread to do work to avoid blocking the connection
                    //This means that the Daemon potentially can do multiple things but we only allows a single build at a time
                    handlersExecutor.execute(new Runnable() {
                        public void run() {
                            try {
                                handleIncoming(connection);
                            } catch (RuntimeException e) {
                                LOGGER.error("Error processing the incoming command.", e);
                                //TODO SF figure out if we can use our executor's exception handler.
                                throw e; //in case the default exception handler needs it.
                            }
                        }
                    });
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

                    onStop();
                }
            });

            registryUpdater = new DomainRegistryUpdater(daemonRegistry, connectorAddress);

            onStart();
        } catch (Exception e) {
            LOGGER.warn("exception starting daemon", e);
            stopLatch.countDown();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Called in start() when the daemon is up and running and ready for connections.
     */
    private void onStart() {
        stateCoordinator.start(); // go into started state
        registryUpdater.onStart(); // advertise in the registry

        LOGGER.lifecycle("Daemon starting at: " + new Date() + ", with address: " + connectorAddress);
    }

    /**
     * Called whenever a command is received, except Stop, and is about to be actioned.
     * 
     * @throws BusyException if the daemon is already actioning a command.
     */
    private void onStartActivity() throws BusyException {
        stateCoordinator.onStartActivity(); // will throw BusyException if the daemon is already busy
        registryUpdater.onStartActivity(); // mark as busy in the registry
    }

    /**
     * Called whenever a received command has been completed.
     */
    private void onCompleteActivity() {
        stateCoordinator.onActivityComplete();
        registryUpdater.onCompleteActivity(); // mark as idle in the registry
    }

    /**
     * Called by the “stopping thread” when the countdown latch is triggered (either by the stop() method or a Stop command) 
     */
    private void onStop() {
        LOGGER.info("Stop requested. Daemon is stopping accepting new connections...");
        registryUpdater.onStop(); // remove this daemon from the registry
        connector.stop(); // stop accepting new connections

        LOGGER.info("Waking and signalling stop to the main daemon thread...");
        stateCoordinator.stop(); // will block until any “workers” finish and put the daemon in “stopped” state
    }

    private void handleIncoming(Connection<Object> connection) {
        Command command = (Command) connection.receive();
        if (command == null) {
            LOGGER.warn("It seems the client dropped the connection before sending any command. Stopping connection.");
            connection.stop(); //TODO SF why do we need to stop the connection here and there?
            return;
        }
        if (command instanceof Stop) {
            LOGGER.lifecycle("Stopping");
            connection.dispatch(new CommandComplete(null));
            stopLatch.countDown();
            stopperExecutor.stop();
            return;
        }

        try {
            onStartActivity();
        } catch (BusyException e) {
            LOGGER.info("The daemon is busy and another build request received. Returning Busy response.");
            connection.dispatch(new CommandComplete(e));
            return;
        }
        try {
            commandExecuter.executeCommand(connection, command);
        } finally {
            onCompleteActivity();
            connection.stop();
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
        stateCoordinator.awaitStop();
    }

    /**
     * Waits until the daemon is either stopped, or has been idle for the given number of milliseconds.
     *
     * @return true if it was stopped, false if it hit the given idle timeout.
     */
    public boolean awaitStopOrIdleTimeout(int idleTimeout) {
        return stateCoordinator.awaitStopOrIdleTimeout(idleTimeout);
    }

    /**
     * Starts the daemon, blocking until it is stopped (either by Stop command or by another thread calling stop())
     */
    public void run() {
        start();
        awaitStop();
    }

}
