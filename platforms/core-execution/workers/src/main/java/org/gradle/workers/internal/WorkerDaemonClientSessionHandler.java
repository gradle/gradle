/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.Stoppable;

@NonNullApi
public class WorkerDaemonClientSessionHandler implements Stoppable {
    private final WorkerDaemonClientsManager workerDaemonClientsManager;
    private final BuildCancellationToken buildCancellationToken;

    private final Runnable cancellationCallback = new KillWorkers();

    public WorkerDaemonClientSessionHandler(WorkerDaemonClientsManager workerDaemonClientsManager, BuildCancellationToken buildCancellationToken) {
        this.workerDaemonClientsManager = workerDaemonClientsManager;
        this.buildCancellationToken = buildCancellationToken;
    }

    public void start() {
        buildCancellationToken.addCallback(cancellationCallback);
    }

    @Override
    public void stop() {
        buildCancellationToken.removeCallback(cancellationCallback);
    }

    @NonNullApi
    private class KillWorkers implements Runnable {
        @Override
        public void run() {
            workerDaemonClientsManager.killAllWorkers();
        }
    }
}
