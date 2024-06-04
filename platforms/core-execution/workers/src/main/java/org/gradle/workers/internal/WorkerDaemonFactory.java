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

import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Controls the lifecycle of the worker daemon and provides access to it.
 */
@ThreadSafe
public class WorkerDaemonFactory implements WorkerFactory {
    private final WorkerDaemonClientsManager clientsManager;
    private final BuildOperationRunner buildOperationRunner;
    private final WorkerDaemonClientCancellationHandler workerDaemonClientCancellationHandler;

    public WorkerDaemonFactory(WorkerDaemonClientsManager clientsManager, BuildOperationRunner buildOperationRunner, WorkerDaemonClientCancellationHandler workerDaemonClientCancellationHandler) {
        this.clientsManager = clientsManager;
        this.buildOperationRunner = buildOperationRunner;
        this.workerDaemonClientCancellationHandler = workerDaemonClientCancellationHandler;
    }

    @Override
    public BuildOperationAwareWorker getWorker(WorkerRequirement workerRequirement) {
        return new AbstractWorker(buildOperationRunner) {
            @Override
            public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec, BuildOperationRef parentBuildOperation) {
                // This notifies the cancellation handler that a worker daemon has been in use during this build session.  If the
                // build session is cancelled, we can't guarantee that all worker daemons are in a safe state, so the cancellation
                // handler will stop any long-lived worker daemons.  If a worker is not used during this session (i.e. this method
                // is never called) the cancellation handler will not stop daemons on a cancellation (as there is no danger of
                // leaving one in an unsafe state).
                workerDaemonClientCancellationHandler.start();
                
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
