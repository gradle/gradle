/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WorkerDaemonClientsManager {

    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonManager.class);

    private final Object lock = new Object();
    private final List<WorkerDaemonClient> allClients = new ArrayList<WorkerDaemonClient>();
    private final List<WorkerDaemonClient> idleClients = new ArrayList<WorkerDaemonClient>();

    private WorkerDaemonStarter workerDaemonStarter;

    public WorkerDaemonClientsManager(WorkerDaemonStarter workerDaemonStarter) {
        this.workerDaemonStarter = workerDaemonStarter;
    }

    public WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return reserveIdleClient(forkOptions, idleClients);
    }

    WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions, List<WorkerDaemonClient> clients) {
        synchronized (lock) {
            Iterator<WorkerDaemonClient> it = clients.iterator();
            while(it.hasNext()) {
                WorkerDaemonClient candidate = it.next();
                if(candidate.isCompatibleWith(forkOptions)) {
                    it.remove();
                    return candidate;
                }
            }
            return null;
        }
    }

    public WorkerDaemonClient reserveNewClient(Class<? extends WorkerDaemonProtocol> serverImplementationClass, File workingDir, DaemonForkOptions forkOptions) {
        //allow the daemon to be started concurrently
        WorkerDaemonClient client = workerDaemonStarter.startDaemon(serverImplementationClass, workingDir, forkOptions);
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

    public void stop() {
        synchronized (lock) {
            LOGGER.debug("Stopping {} worker daemon(s).", allClients.size());
            CompositeStoppable.stoppable(allClients).stop();
            LOGGER.info("Stopped {} worker daemon(s).", allClients.size());
            allClients.clear();
        }
    }
}
