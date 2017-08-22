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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.TotalPhysicalMemoryProvider;
import org.gradle.workers.IsolationMode;

/**
 * Controls the lifecycle of the worker daemon and provides access to it.
 */
@ThreadSafe
public class WorkerDaemonFactory implements WorkerFactory, Stoppable {
    private final WorkerDaemonClientsManager clientsManager;
    private final MemoryManager memoryManager;
    private final WorkerDaemonExpiration workerDaemonExpiration;
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;

    public WorkerDaemonFactory(WorkerDaemonClientsManager clientsManager, MemoryManager memoryManager, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.clientsManager = clientsManager;
        this.memoryManager = memoryManager;
        this.workerDaemonExpiration = new WorkerDaemonExpiration(clientsManager, getTotalPhysicalMemory());
        memoryManager.addMemoryHolder(workerDaemonExpiration);
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Worker getWorker(final DaemonForkOptions forkOptions) {
        return new Worker() {
            public DefaultWorkResult execute(final ActionExecutionSpec spec, WorkerLease parentWorkerWorkerLease, final BuildOperationState parentBuildOperation) {
                WorkerLeaseRegistry.WorkerLeaseCompletion workerLease = parentWorkerWorkerLease.startChild();
                try {
                    WorkerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                    if (client == null) {
                        client = clientsManager.reserveNewClient(WorkerDaemonServer.class, forkOptions);
                    }

                    try {
                        return executeInClient(client, spec, parentBuildOperation);
                    } finally {
                        clientsManager.release(client);
                    }
                } finally {
                    workerLease.leaseFinish();
                }
            }

            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec) {
                return execute(spec, workerLeaseRegistry.getCurrentWorkerLease(), buildOperationExecutor.getCurrentOperation());
            }

            private DefaultWorkResult executeInClient(final WorkerDaemonClient client, final ActionExecutionSpec spec, final BuildOperationState parentBuildOperation) {
                return buildOperationExecutor.call(new CallableBuildOperation<DefaultWorkResult>() {
                    @Override
                    public DefaultWorkResult call(BuildOperationContext context) {
                        return client.execute(spec);
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName(spec.getDisplayName()).parent(parentBuildOperation);
                    }
                });
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.PROCESS;
    }

    @Override
    public void stop() {
        memoryManager.removeMemoryHolder(workerDaemonExpiration);
    }

    private static long getTotalPhysicalMemory() {
        try {
            return TotalPhysicalMemoryProvider.getTotalPhysicalMemory();
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }
}
