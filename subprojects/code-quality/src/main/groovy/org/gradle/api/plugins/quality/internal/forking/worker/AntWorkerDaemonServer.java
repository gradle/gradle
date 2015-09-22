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

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.api.plugins.quality.internal.forking.DefaultAntResult;
import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.WorkerProcessContext;


public class AntWorkerDaemonServer implements Action<WorkerProcessContext>, AntWorkerDaemonServerProtocol, Serializable {
    private static final Logger LOGGER = Logging.getLogger(AntWorkerDaemonServer.class);

    private volatile AntWorkerDaemonClientProtocol client;
    private volatile CountDownLatch stop;

    @Override
    public void execute(WorkerProcessContext context) {
        stop = new CountDownLatch(1);
        client = context.getServerConnection().addOutgoing(AntWorkerDaemonClientProtocol.class);
        context.getServerConnection().addIncoming(AntWorkerDaemonServerProtocol.class, this);
        context.getServerConnection().connect();
        try {
            stop.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public <T extends AntWorkerSpec> void executeSpec(T spec) {
        DefaultAntWorker antWorker = new DefaultAntWorker();
        try {
            LOGGER.info("Executing {} in ant worker daemon.", antWorker);
            AntResult antResult = antWorker.executeSpec(spec);
            LOGGER.info("Successfully executed {} in ant worker daemon.", antWorker);
            client.executed(antResult);
        } catch (Throwable t) {
            LOGGER.info("Exception executing {} in ant worker daemon: {}.", antWorker, t);
            client.executed(new DefaultAntResult(1, t, new HashMap<String, Object>()));
        }
    }

    @Override
    public void stop() {
        stop.countDown();
    }
}
