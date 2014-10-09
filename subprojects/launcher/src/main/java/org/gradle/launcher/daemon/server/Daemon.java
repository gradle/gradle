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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.messaging.remote.Address;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A long-lived build server that accepts commands via a communication channel.
 * <p>
 * Daemon instances are single use and have a start/stop debug. They are also threadsafe.
 * <p>
 * See {@link org.gradle.launcher.daemon.client.DaemonClient} for a description of the daemon communication protocol.
 */
public class Daemon implements Stoppable {
    private static final Logger LOGGER = Logging.getLogger(Daemon.class);

    private final DaemonServerConnector connector;
    private final DaemonRegistry daemonRegistry;
    private final DaemonContext daemonContext;
    private final DaemonCommandExecuter commandExecuter;
    private final ExecutorFactory executorFactory;
    private final String password;

    private DaemonStateCoordinator stateCoordinator;

    private final Lock lifecyleLock = new ReentrantLock();

    private Address connectorAddress;
    private DomainRegistryUpdater registryUpdater;
    private DefaultIncomingConnectionHandler connectionHandler;

    /**
     * Creates a new daemon instance.
     * 
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    public Daemon(DaemonServerConnector connector, DaemonRegistry daemonRegistry, DaemonContext daemonContext, String password, DaemonCommandExecuter commandExecuter, ExecutorFactory executorFactory) {
        this.connector = connector;
        this.daemonRegistry = daemonRegistry;
        this.daemonContext = daemonContext;
        this.password = password;
        this.commandExecuter = commandExecuter;
        this.executorFactory = executorFactory;
    }

    public String getUid() {
        return daemonContext.getUid();
    }

    public Address getAddress() {
        return connectorAddress;
    }

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     * 
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    public void start() {
        LOGGER.info("start() called on daemon - {}", daemonContext);
        lifecyleLock.lock();
        try {
            if (stateCoordinator != null) {
                throw new IllegalStateException("cannot start daemon as it is already running");
            }

            registryUpdater = new DomainRegistryUpdater(daemonRegistry, daemonContext, password);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        daemonRegistry.remove(connectorAddress);
                    } catch (Exception e) {
                        LOGGER.debug("VM shutdown hook was unable to remove the daemon address from the registry. It will be cleaned up later.", e);
                    }
                }
            });

            Runnable onStartCommand = new Runnable() {
                public void run() {
                    registryUpdater.onStartActivity();
                }
            };

            Runnable onFinishCommand = new Runnable() {
                public void run() {
                    registryUpdater.onCompleteActivity();
                }
            };

            // Start the pipeline in reverse order:
            // 1. mark daemon as running
            // 2. start handling incoming commands
            // 3. start accepting incoming connections
            // 4. advertise presence in registry

            stateCoordinator = new DaemonStateCoordinator(executorFactory, onStartCommand, onFinishCommand);
            connectionHandler = new DefaultIncomingConnectionHandler(commandExecuter, daemonContext, stateCoordinator, executorFactory);
            connectorAddress = connector.start(connectionHandler);
            LOGGER.debug("Daemon starting at: " + new Date() + ", with address: " + connectorAddress);
            registryUpdater.onStart(connectorAddress);
        } finally {
            lifecyleLock.unlock();
        }

        LOGGER.lifecycle(DaemonMessages.PROCESS_STARTED);
    }

    /**
     * Stops the daemon, blocking until any current requests/connections have been satisfied.
     * <p>
     * This is the semantically the same as sending the daemon the Stop command.
     * <p>
     * This method does not quite conform to the semantics of the Stoppable contract in that it will NOT
     * wait for any executing builds to stop before returning. This is by design as we currently have no way of
     * gracefully stopping a build process and blocking until it's done would not allow us to tear down the jvm
     * like we need to. This may change in the future if we create a way to interrupt a build.
     * <p>
     * What will happen though is that the daemon will immediately disconnect from any clients and remove itself
     * from the registry.
     */
    public void stop() {
        LOGGER.debug("stop() called on daemon");
        lifecyleLock.lock();
        try {
            if (stateCoordinator == null) {
                throw new IllegalStateException("cannot stop daemon as it has not been started.");
            }

            LOGGER.info(DaemonMessages.REMOVING_PRESENCE_DUE_TO_STOP);

            // Stop the pipeline:
            // 1. mark daemon as stopped, so that any incoming requests will be rejected with 'daemon unavailable'
            // 2. remove presence from registry
            // 3. stop accepting new connections
            // 4. wait for commands in progress to finish (except for abandoned long running commands, like running a build)

            CompositeStoppable.stoppable(stateCoordinator, registryUpdater, connector, connectionHandler).stop();
        } finally {
            lifecyleLock.unlock();
        }
    }

    /**
     * Waits for the daemon to be idle for the specified number of milliseconds, then requests that the daemon stop.
     *
     * <p>May return earlier if the daemon is stopped before the idle timeout is reached.</p>
     */
    public void requestStopOnIdleTimeout(int idleTimeout, TimeUnit idleTimeoutUnits) {
        LOGGER.debug("requestStopOnIdleTimeout({} {}) called on daemon", idleTimeout, idleTimeoutUnits);
        DaemonStateCoordinator stateCoordinator;
        lifecyleLock.lock();
        try {
            if (this.stateCoordinator == null) {
                throw new IllegalStateException("cannot stop daemon as it has not been started.");
            }
            stateCoordinator = this.stateCoordinator;
        } finally {
            lifecyleLock.unlock();
        }

        stateCoordinator.stopOnIdleTimeout(idleTimeout, idleTimeoutUnits);
    }
}