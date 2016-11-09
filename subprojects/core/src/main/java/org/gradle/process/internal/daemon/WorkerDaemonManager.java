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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.concurrent.Stoppable;

import java.io.File;

/**
 * Controls the lifecycle of the worker daemon and provides access to it.
 */
@ThreadSafe
public class WorkerDaemonManager implements WorkerDaemonFactory, Stoppable {

    private WorkerDaemonClientsManager clientsManager;

    public WorkerDaemonManager(WorkerDaemonClientsManager clientsManager) {
        this.clientsManager = clientsManager;
    }

    @Override
    public WorkerDaemon getDaemon(final Class<? extends WorkerDaemonProtocol> serverImplementationClass, final File workingDir, final DaemonForkOptions forkOptions) {
        return new WorkerDaemon() {
            public <T extends WorkSpec> WorkerDaemonResult execute(WorkerDaemonAction<T> action, T spec) {
                WorkerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    client = clientsManager.reserveNewClient(serverImplementationClass, workingDir, forkOptions);
                }
                try {
                    return client.execute(action, spec);
                } finally {
                    clientsManager.release(client);
                }
            }
        };
    }

    @Override
    public void stop() {
        clientsManager.stop();
    }
}
