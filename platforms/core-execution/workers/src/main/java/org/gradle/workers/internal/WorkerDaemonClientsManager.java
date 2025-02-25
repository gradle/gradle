/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Transformer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.session.BuildSessionLifecycleListener;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.OsMemoryInfo;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Comparator.comparingInt;

@ServiceScope(Scope.UserHome.class)
public class WorkerDaemonClientsManager implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonClientsManager.class);

    private final Object lock = new Object();
    private final List<WorkerDaemonClient> allClients = new ArrayList<WorkerDaemonClient>();
    private final List<WorkerDaemonClient> idleClients = new ArrayList<WorkerDaemonClient>();

    private final WorkerDaemonStarter workerDaemonStarter;
    private final ListenerManager listenerManager;
    private final LoggingManagerInternal loggingManager;
    private final OsMemoryInfo memoryInfo;
    private final BuildSessionLifecycleListener stopSessionScopeWorkers;
    private final OutputEventListener logLevelChangeEventListener;
    private final WorkerDaemonExpiration workerDaemonExpiration;
    private final MemoryManager memoryManager;
    private volatile LogLevel currentLogLevel;

    public WorkerDaemonClientsManager(WorkerDaemonStarter workerDaemonStarter, ListenerManager listenerManager, LoggingManagerInternal loggingManager, MemoryManager memoryManager, OsMemoryInfo memoryInfo) {
        this.workerDaemonStarter = workerDaemonStarter;
        this.listenerManager = listenerManager;
        this.loggingManager = loggingManager;
        this.memoryInfo = memoryInfo;
        this.stopSessionScopeWorkers = new StopSessionScopedWorkers();
        listenerManager.addListener(stopSessionScopeWorkers);
        this.logLevelChangeEventListener = new LogLevelChangeEventListener();
        loggingManager.addOutputEventListener(logLevelChangeEventListener);
        this.currentLogLevel = loggingManager.getLevel();
        this.memoryManager = memoryManager;
        this.workerDaemonExpiration = new WorkerDaemonExpiration(this, getTotalPhysicalMemory());
        memoryManager.addMemoryHolder(workerDaemonExpiration);
    }

    // TODO - should supply and check for the same parameters as passed to reserveNewClient()
    public WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return reserveIdleClient(forkOptions, idleClients);
    }

    WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions, List<WorkerDaemonClient> clients) {
        synchronized (lock) {
            Iterator<WorkerDaemonClient> it = clients.iterator();
            while (it.hasNext()) {
                WorkerDaemonClient candidate = it.next();
                if (candidate.isFailed()) {
                    emitUnexpectedWorkerFailureWarning(candidate);
                    it.remove();
                } else {
                    if (candidate.isCompatibleWith(forkOptions)) {
                        it.remove();
                        if (candidate.getLogLevel() != currentLogLevel) {
                            // TODO: Send a message to workers to change their log level rather than stopping
                            LOGGER.info("Log level has changed, stopping idle worker daemon with out-of-date log level.");
                            candidate.stop();
                        } else {
                            return candidate;
                        }
                    }
                }
            }
            return null;
        }
    }

    private static void emitUnexpectedWorkerFailureWarning(WorkerDaemonClient candidate) {
        if (candidate.getExitCode().isPresent()) {
            int exitCode = candidate.getExitCode().get();
            if (OperatingSystem.current().isUnix() && exitCode > 127) {
                LOGGER.warn("Worker daemon '" + candidate.getDisplayName() + "' exited unexpectedly after being killed with signal " + (exitCode - 128) + ".  This is likely because an external process has killed the worker.");
            } else {
                LOGGER.warn("Worker daemon '" + candidate.getDisplayName() + "' exited unexpectedly with exit code " + exitCode + ".");
            }
        }
    }

    WorkerDaemonClient reserveNewClient(DaemonForkOptions forkOptions) {
        //allow the daemon to be started concurrently
        WorkerDaemonClient client = workerDaemonStarter.startDaemon(forkOptions);
        synchronized (lock) {
            allClients.add(client);
        }
        return client;
    }

    void release(WorkerDaemonClient client) {
        synchronized (lock) {
            if (!client.isFailed()) {
                idleClients.add(client);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopAllWorkers();
            listenerManager.removeListener(stopSessionScopeWorkers);
            memoryManager.removeMemoryHolder(workerDaemonExpiration);
        }
        // Do not hold lock while removing listener, as the listener may still be receiving events on another thread and will need to acquire the lock to handle these events
        loggingManager.removeOutputEventListener(logLevelChangeEventListener);
    }

    private long getTotalPhysicalMemory() {
        try {
            return memoryInfo.getOsSnapshot().getPhysicalMemory().getTotal();
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    /**
     * Select idle daemon clients to stop.
     *
     * @param selectionFunction Gets all idle daemon clients, daemons of returned clients are stopped
     */
    @VisibleForTesting
    void selectIdleClientsToStop(Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> selectionFunction) {
        synchronized (lock) {
            List<WorkerDaemonClient> sortedClients = CollectionUtils.sort(idleClients, comparingInt(WorkerDaemonClient::getUses));
            List<WorkerDaemonClient> clientsToStop = selectionFunction.transform(new ArrayList<>(sortedClients));
            if (!clientsToStop.isEmpty()) {
                stopWorkers(clientsToStop);
            }
        }
    }

    private void stopWorkers(List<WorkerDaemonClient> clientsToStop) {
        stopWorkers(clientsToStop, STOP_CLIENT);
    }

    private void stopWorkers(List<WorkerDaemonClient> clientsToStop, Consumer<WorkerDaemonClient> stopAction) {
        if (clientsToStop.size() > 0) {
            int clientCount = clientsToStop.size();
            LOGGER.debug("Stopping {} worker daemon(s).", clientCount);
            int failureCount = 0;
            for (WorkerDaemonClient client : clientsToStop) {
                try {
                    if (client.isFailed()) {
                        emitUnexpectedWorkerFailureWarning(client);
                    } else {
                        stopAction.accept(client);
                    }
                } catch (Exception e) {
                    failureCount++;
                    LOGGER.warn("Failed to stop worker daemon '" + client.getDisplayName() + "'", e);
                }
            }
            idleClients.removeAll(clientsToStop);
            allClients.removeAll(clientsToStop);
            if (failureCount > 0) {
                LOGGER.info("Stopped {} worker daemon(s).  {} worker daemons had failures while stopping.", clientCount, failureCount);
            } else {
                LOGGER.info("Stopped {} worker daemon(s).", clientCount);
            }
        }
    }

    private void stopAllWorkers(Consumer<WorkerDaemonClient> stopClientAction) {
        synchronized (lock) {
            stopWorkers(allClients, stopClientAction);
            allClients.clear();
            idleClients.clear();
        }
    }

    public void stopAllWorkers() {
        stopAllWorkers(STOP_CLIENT);
    }

    public void killAllWorkers() {
        stopAllWorkers(KILL_CLIENT);
    }

    private class LogLevelChangeEventListener implements OutputEventListener {
        @Override
        public void onOutput(OutputEvent event) {
            if (event instanceof LogLevelChangeEvent) {
                LogLevelChangeEvent logLevelChangeEvent = (LogLevelChangeEvent) event;
                currentLogLevel = logLevelChangeEvent.getNewLogLevel();
            }
        }
    }

    private class StopSessionScopedWorkers implements BuildSessionLifecycleListener {
        @Override
        public void beforeComplete() {
            synchronized (lock) {
                List<WorkerDaemonClient> sessionScopedClients = CollectionUtils.filter(allClients, client -> client.getKeepAliveMode() == KeepAliveMode.SESSION);
                stopWorkers(sessionScopedClients);
            }
        }
    }

    private static final Consumer<WorkerDaemonClient> STOP_CLIENT = WorkerDaemonClient::stop;
    private static final Consumer<WorkerDaemonClient> KILL_CLIENT = WorkerDaemonClient::kill;
}
