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

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;

class WorkerDaemonClient implements WorkerDaemon, Stoppable {
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;
    private final DaemonForkOptions forkOptions;
    private final WorkerDaemonWorker workerProcess;

    public WorkerDaemonClient(BuildOperationWorkerRegistry buildOperationWorkerRegistry, DaemonForkOptions forkOptions, WorkerDaemonWorker workerProcess) {
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        this.forkOptions = forkOptions;
        this.workerProcess = workerProcess;
    }

    @Override
    public <T extends WorkSpec> WorkerDaemonResult execute(WorkerDaemonAction<T> action, T spec) {
        // currently we just allow a single compilation thread at a time (per compiler daemon)
        // one problem to solve when allowing multiple threads is how to deal with memory requirements specified by compile tasks
        BuildOperationWorkerRegistry.Completion workerLease = buildOperationWorkerRegistry.getCurrent().operationStart();
        try {
            return workerProcess.execute(action, spec);
        } finally {
            workerLease.operationFinish();
        }
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    @Override
    public void stop() {
        workerProcess.stop();
    }
}
