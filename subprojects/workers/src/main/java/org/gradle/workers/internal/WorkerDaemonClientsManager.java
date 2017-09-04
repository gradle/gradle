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

import org.gradle.api.Transformer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.SessionLifecycleListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class WorkerDaemonClientsManager implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonClientsManager.class);

    private final Object lock = new Object();
    private final List<WorkerDaemonClient> allClients = new ArrayList<WorkerDaemonClient>();
    private final List<WorkerDaemonClient> idleClients = new ArrayList<WorkerDaemonClient>();

    private final WorkerDaemonStarter workerDaemonStarter;
    private final ListenerManager listenerManager;
    private final LoggingManagerInternal loggingManager;
    private final SessionLifecycleListener stopSessionScopeWorkers;
    private final OutputEventListener logLevelChangeEventListener;
    private LogLevel currentLogLevel;

    public WorkerDaemonClientsManager(WorkerDaemonStarter workerDaemonStarter, ListenerManager listenerManager, LoggingManagerInternal loggingManager) {
        this.workerDaemonStarter = workerDaemonStarter;
        this.listenerManager = listenerManager;
        this.loggingManager = loggingManager;
        this.stopSessionScopeWorkers = new StopSessionScopedWorkers();
        listenerManager.addListener(stopSessionScopeWorkers);
        this.logLevelChangeEventListener = new LogLevelChangeEventListener();
        loggingManager.addOutputEventListener(logLevelChangeEventListener);
        this.currentLogLevel = loggingManager.getLevel();
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
            return null;
        }
    }

    public WorkerDaemonClient reserveNewClient(Class<? extends WorkerProtocol<ActionExecutionSpec>> workerProtocolImplementationClass, DaemonForkOptions forkOptions) {
        //allow the daemon to be started concurrently
        WorkerDaemonClient client = workerDaemonStarter.startDaemon(workerProtocolImplementationClass, forkOptions);
        synchronized (lock) {
            allClients.add(client);
        }
        return client;
    }

    public void release(WorkerDaemonClient client) {
        synchronized (lock) {
            idleClients.add(client);
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopWorkers(allClients);
            allClients.clear();
            idleClients.clear();
            listenerManager.removeListener(stopSessionScopeWorkers);
            loggingManager.removeOutputEventListener(logLevelChangeEventListener);
        }
    }

    /**
     * Select idle daemon clients to stop.
     *
     * @param selectionFunction Gets all idle daemon clients, daemons of returned clients are stopped
     */
    public void selectIdleClientsToStop(Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> selectionFunction) {
        synchronized (lock) {
            List<WorkerDaemonClient> sortedClients = CollectionUtils.sort(idleClients, new Comparator<WorkerDaemonClient>() {
                @Override
                public int compare(WorkerDaemonClient o1, WorkerDaemonClient o2) {
                    return new Integer(o1.getUses()).compareTo(o2.getUses());
                }
            });
            List<WorkerDaemonClient> clientsToStop = selectionFunction.transform(new ArrayList<WorkerDaemonClient>(sortedClients));
            if (!clientsToStop.isEmpty()) {
                stopWorkers(clientsToStop);
            }
        }
    }

    private void stopWorkers(List<WorkerDaemonClient> clientsToStop) {
        if (clientsToStop.size() > 0) {
            LOGGER.debug("Stopping {} worker daemon(s).", clientsToStop.size());
            CompositeStoppable.stoppable(clientsToStop).stop();
            LOGGER.info("Stopped {} worker daemon(s).", clientsToStop.size());
            idleClients.removeAll(clientsToStop);
            allClients.removeAll(clientsToStop);
        }
    }

    private class StopSessionScopedWorkers implements SessionLifecycleListener {
        @Override
        public void afterStart() { }

        @Override
        public void beforeComplete() {
            synchronized (lock) {
                List<WorkerDaemonClient> sessionScopedClients = CollectionUtils.filter(allClients, new Spec<WorkerDaemonClient>() {
                    @Override
                    public boolean isSatisfiedBy(WorkerDaemonClient client) {
                        return client.getKeepAliveMode() == KeepAliveMode.SESSION;
                    }
                });
                stopWorkers(sessionScopedClients);
            }
        }
    }

    private class LogLevelChangeEventListener implements OutputEventListener {
        @Override
        public void onOutput(OutputEvent event) {
            if (event instanceof LogLevelChangeEvent) {
                LogLevelChangeEvent logLevelChangeEvent = (LogLevelChangeEvent) event;
                if (currentLogLevel != logLevelChangeEvent.getNewLogLevel()) {
                    synchronized (lock) {
                        currentLogLevel = logLevelChangeEvent.getNewLogLevel();
                    }
                }
            }
        }
    }
}
