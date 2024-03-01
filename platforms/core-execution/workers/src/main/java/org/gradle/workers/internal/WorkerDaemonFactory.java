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

import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Controls the lifecycle of the worker daemon and provides access to it.
 */
@ThreadSafe
public class WorkerDaemonFactory implements WorkerFactory {
    private final WorkerDaemonClientsManager clientsManager;
    private final BuildOperationExecutor buildOperationExecutor;

    public WorkerDaemonFactory(WorkerDaemonClientsManager clientsManager, BuildOperationExecutor buildOperationExecutor) {
        this.clientsManager = clientsManager;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public BuildOperationAwareWorker getWorker(WorkerRequirement workerRequirement) {
        return new AbstractWorker(buildOperationExecutor) {
            @Override
            public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec, BuildOperationRef parentBuildOperation) {
                // wrap in build operation for logging startup failures
                final WorkerDaemonClient client = CurrentBuildOperationRef.instance().with(parentBuildOperation, this::reserveClient);
                try {
                    return executeWrappedInBuildOperation(spec, parentBuildOperation, client::execute);
                } finally {
                    clientsManager.release(client);
                }
            }

            private WorkerDaemonClient reserveClient() {
                DaemonForkOptions forkOptions = ((ForkedWorkerRequirement) workerRequirement).getForkOptions();
                WorkerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    client = clientsManager.reserveNewClient(forkOptions);
                }
                return client;
            }
        };
    }
}
