/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking.worker;

import java.io.File;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.internal.concurrent.Stoppable;


@ThreadSafe
public class AntWorkerDaemonManager implements AntWorkerDaemonFactory, Stoppable {

    private static final Logger LOGGER = Logging.getLogger(AntWorkerDaemonManager.class);

    AntWorkerClientsManager clientsManager;

    public AntWorkerDaemonManager(AntWorkerClientsManager clientsManager) {
        this.clientsManager = clientsManager;
    }

    @Override
    public AntWorkerDaemon getDaemon(final File workingDir, final DaemonForkOptions forkOptions) {
        return new AntWorkerDaemon() {
            public <T extends AntWorkerSpec> AntResult execute(T spec) {
                AntWorkerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    LOGGER.debug("Getting new client");
                    client = clientsManager.reserveNewClient(workingDir, forkOptions);
                }
                try {
                    return client.execute(spec);
                } finally {
                    LOGGER.debug("Releasing client: {}", client);
                    clientsManager.release(client);
                }
            }
        };
    }

    public void stop() {
        clientsManager.stop();
    }
}
