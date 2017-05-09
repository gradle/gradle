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
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.TotalPhysicalMemoryProvider;

import java.io.File;

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
    public <T extends WorkSpec> Worker<T> getWorker(final Class<? extends WorkerProtocol<T>> workerImplementationClass, final File workingDir, final DaemonForkOptions forkOptions) {
        return new Worker<T>() {
            public DefaultWorkResult execute(T spec, WorkerLease parentWorkerWorkerLease, BuildOperationState parentBuildOperation) {
                WorkerDaemonClient<T> client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    client = clientsManager.reserveNewClient(workerImplementationClass, workingDir, forkOptions);
                }
                try {
                    return client.execute(spec, parentWorkerWorkerLease, parentBuildOperation);
                } finally {
                    clientsManager.release(client);
                }
            }

            @Override
            public DefaultWorkResult execute(T spec) {
                return execute(spec, workerLeaseRegistry.getCurrentWorkerLease(), buildOperationExecutor.getCurrentOperation());
            }
        };
    }

    @Override
    public void stop() {
        clientsManager.stop();
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
