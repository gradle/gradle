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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.process.internal.shutdown.ShutdownHooks;

import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.*;

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
    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorFactory executorFactory;
    private final ListenerManager listenerManager;

    private DaemonStateCoordinator stateCoordinator;

    private final Lock lifecycleLock = new ReentrantLock();

    private Address connectorAddress;
    private DaemonRegistryUpdater registryUpdater;
    private DefaultIncomingConnectionHandler connectionHandler;

    /**
     * Creates a new daemon instance.
     *
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    public Daemon(DaemonServerConnector connector, DaemonRegistry daemonRegistry, DaemonContext daemonContext, DaemonCommandExecuter commandExecuter, ExecutorFactory executorFactory, ListenerManager listenerManager) {
        this.connector = connector;
        this.daemonRegistry = daemonRegistry;
        this.daemonContext = daemonContext;
        this.commandExecuter = commandExecuter;
        this.executorFactory = executorFactory;
        this.scheduledExecutorService = executorFactory.createScheduled("Daemon periodic checks", 1);
        this.listenerManager = listenerManager;
    }

    public String getUid() {
        return daemonContext.getUid();
    }

    public Address getAddress() {
        return connectorAddress;
    }

    public DaemonContext getDaemonContext() {
        return daemonContext;
    }

    public DaemonRegistry getDaemonRegistry() {
        return this.daemonRegistry;
    }

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     *
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    public void start() {
        LOGGER.info("start() called on daemon - {}", daemonContext);
        lifecycleLock.lock();
        try {
            if (stateCoordinator != null) {
                throw new IllegalStateException("cannot start daemon as it is already running");
            }

            // Generate an authentication token, which must be provided by the client in any requests it makes
            SecureRandom secureRandom = new SecureRandom();
            byte[] token = new byte[16];
            secureRandom.nextBytes(token);

            registryUpdater = new DaemonRegistryUpdater(daemonRegistry, daemonContext, token);

            ShutdownHooks.addShutdownHook(new Runnable() {
                @Override
                public void run() {
                    try {
                        daemonRegistry.remove(connectorAddress);
                    } catch (Exception e) {
                        LOGGER.debug("VM shutdown hook was unable to remove the daemon address from the registry. It will be cleaned up later.", e);
                    }
                }
            });

            Runnable onStartCommand = new Runnable() {
                @Override
                public void run() {
                    registryUpdater.onStartActivity();
                }
            };

            Runnable onFinishCommand = new Runnable() {
                @Override
                public void run() {
                    registryUpdater.onCompleteActivity();
                }
            };

            Runnable onCancelCommand = new Runnable() {
                @Override
                public void run() {
                    registryUpdater.onCancel();
                }
            };

            // Start the pipeline in reverse order:
            // 1. mark daemon as running
            // 2. start handling incoming commands
            // 3. start accepting incoming connections
            // 4. advertise presence in registry

            stateCoordinator = new DaemonStateCoordinator(executorFactory, onStartCommand, onFinishCommand, onCancelCommand);
            connectionHandler = new DefaultIncomingConnectionHandler(commandExecuter, daemonContext, stateCoordinator, executorFactory, token);
            Runnable connectionErrorHandler = new Runnable() {
                @Override
                public void run() {
                    stateCoordinator.stop();
                }
            };
            connectorAddress = connector.start(connectionHandler, connectionErrorHandler);
            LOGGER.debug("Daemon starting at: {}, with address: {}", new Date(), connectorAddress);
            registryUpdater.onStart(connectorAddress);
        } finally {
            lifecycleLock.unlock();
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
    @Override
    public void stop() {
        LOGGER.debug("stop() called on daemon");
        lifecycleLock.lock();
        try {
            if (stateCoordinator == null) {
                throw new IllegalStateException("cannot stop daemon as it has not been started.");
            }

            LOGGER.info(DaemonMessages.REMOVING_PRESENCE_DUE_TO_STOP);

            // Stop periodic checks
            scheduledExecutorService.shutdown();

            // Stop the pipeline:
            // 1. mark daemon as stopped, so that any incoming requests will be rejected with 'daemon unavailable'
            // 2. remove presence from registry
            // 3. stop accepting new connections
            // 4. wait for commands in progress to finish (except for abandoned long running commands, like running a build)

            CompositeStoppable.stoppable(stateCoordinator, registryUpdater, connector, connectionHandler).stop();
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void stopOnExpiration(DaemonExpirationStrategy expirationStrategy, int checkIntervalMills) {
        LOGGER.debug("stopOnExpiration() called on daemon");
        scheduleExpirationChecks(expirationStrategy, checkIntervalMills);
        awaitExpiration();
    }

    private void scheduleExpirationChecks(DaemonExpirationStrategy expirationStrategy, int checkIntervalMills) {
        DaemonExpirationPeriodicCheck periodicCheck = new DaemonExpirationPeriodicCheck(expirationStrategy, listenerManager);
        listenerManager.addListener(new DefaultDaemonExpirationListener(stateCoordinator, registryUpdater));
        scheduledExecutorService.scheduleAtFixedRate(periodicCheck, checkIntervalMills, checkIntervalMills, TimeUnit.MILLISECONDS);
    }

    /**
     * Tell DaemonStateCoordinator to block until it's state is Stopped.
     */
    private void awaitExpiration() {
        LOGGER.debug("awaitExpiration() called on daemon");

        DaemonStateCoordinator stateCoordinator;
        lifecycleLock.lock();
        try {
            if (this.stateCoordinator == null) {
                throw new IllegalStateException("cannot await stop on daemon as it has not been started.");
            }
            stateCoordinator = this.stateCoordinator;
        } finally {
            lifecycleLock.unlock();
        }

        stateCoordinator.awaitStop();
    }

    public DaemonStateCoordinator getStateCoordinator() {
        return stateCoordinator;
    }

    private static class DaemonExpirationPeriodicCheck implements Runnable {
        private final DaemonExpirationStrategy expirationStrategy;
        private final DaemonExpirationListener listenerBroadcast;

        DaemonExpirationPeriodicCheck(DaemonExpirationStrategy expirationStrategy, ListenerManager listenerManager) {
            this.expirationStrategy = expirationStrategy;
            this.listenerBroadcast = listenerManager.getBroadcaster(DaemonExpirationListener.class);
        }

        @Override
        public void run() {
            try {
                final DaemonExpirationResult result = expirationStrategy.checkExpiration();
                if (result.getStatus() != DO_NOT_EXPIRE) {
                    listenerBroadcast.onExpirationEvent(result);
                }
            } catch (Throwable t) {
                // this class is used as task in a scheduled executor service, so it must not throw any throwable,
                // otherwise the further invocations of this task get automatically and silently cancelled
                LOGGER.error("Problem in daemon expiration check", t);
            }
        }
    }

    private static class DefaultDaemonExpirationListener implements DaemonExpirationListener {
        private final DaemonStateControl stateControl;
        private final DaemonRegistryUpdater registryUpdater;

        public DefaultDaemonExpirationListener(DaemonStateControl stateControl, DaemonRegistryUpdater registryUpdater) {
            this.stateControl = stateControl;
            this.registryUpdater = registryUpdater;
        }

        @Override
        public void onExpirationEvent(DaemonExpirationResult result) {
            final DaemonExpirationStatus expirationCheck = result.getStatus();

            if (expirationCheck != DO_NOT_EXPIRE) {
                if (expirationCheck != QUIET_EXPIRE) {
                    registryUpdater.onExpire(result.getReason(), expirationCheck);
                }

                if (expirationCheck == IMMEDIATE_EXPIRE) {
                    stateControl.requestForcefulStop(result.getReason());
                } else {
                    stateControl.requestStop(result.getReason());
                }
            }
        }
    }
}
