/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompilerClientsManager {

    private static final Logger LOGGER = Logging.getLogger(CompilerDaemonManager.class);

    private final Object lock = new Object();
    private final List<CompilerDaemonClient> allClients = new ArrayList<CompilerDaemonClient>();
    private final List<CompilerDaemonClient> idleClients = new ArrayList<CompilerDaemonClient>();

    private CompilerDaemonStarter compilerDaemonStarter;

    public CompilerClientsManager(CompilerDaemonStarter compilerDaemonStarter) {
        this.compilerDaemonStarter = compilerDaemonStarter;
    }

    public CompilerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return reserveIdleClient(forkOptions, idleClients);
    }

    CompilerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions, List<CompilerDaemonClient> clients) {
        synchronized (lock) {
            Iterator<CompilerDaemonClient> it = clients.iterator();
            while(it.hasNext()) {
                CompilerDaemonClient candidate = it.next();
                if(candidate.isCompatibleWith(forkOptions)) {
                    it.remove();
                    return candidate;
                }
            }
            return null;
        }
    }

    public CompilerDaemonClient reserveNewClient(File workingDir, DaemonForkOptions forkOptions) {
        //allow the daemon to be started concurrently
        CompilerDaemonClient client = compilerDaemonStarter.startDaemon(workingDir, forkOptions);
        synchronized (lock) {
            allClients.add(client);
        }
        return client;
    }

    public void release(CompilerDaemonClient client) {
        synchronized (lock) {
            idleClients.add(client);
        }
    }

    public void stop() {
        synchronized (lock) {
            LOGGER.debug("Stopping {} compiler daemon(s).", allClients.size());
            CompositeStoppable.stoppable(allClients).stop();
            LOGGER.info("Stopped {} compiler daemon(s).", allClients.size());
            allClients.clear();
        }
    }
}