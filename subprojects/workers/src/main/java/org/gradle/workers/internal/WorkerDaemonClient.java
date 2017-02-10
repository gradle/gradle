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

import org.gradle.api.Transformer;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationWorkerRegistry.Completion;
import org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.worker.WorkerProcess;

class WorkerDaemonClient implements WorkerDaemon, Stoppable {
    private final DaemonForkOptions forkOptions;
    private final WorkerDaemonWorker workerDaemonWorker;
    private final WorkerProcess workerProcess;
    private final BuildOperationExecutor buildOperationExecutor;
    private int uses;

    public WorkerDaemonClient(DaemonForkOptions forkOptions, WorkerDaemonWorker workerDaemonWorker, WorkerProcess workerProcess, BuildOperationExecutor buildOperationExecutor) {
        this.forkOptions = forkOptions;
        this.workerDaemonWorker = workerDaemonWorker;
        this.workerProcess = workerProcess;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public <T extends WorkSpec> DefaultWorkResult execute(final WorkerDaemonAction<T> action, final T spec, Operation parentWorkerOperation, BuildOperationExecutor.Operation parentBuildOperation) {
        Completion workerLease = parentWorkerOperation.operationStart();
        BuildOperationDetails buildOperation = BuildOperationDetails.displayName(action.getDescription()).parent(parentBuildOperation).build();
        try {
            return buildOperationExecutor.run(buildOperation, new Transformer<DefaultWorkResult, BuildOperationContext>() {
                @Override
                public DefaultWorkResult transform(BuildOperationContext buildOperationContext) {
                    uses++;
                    return workerDaemonWorker.execute(action, spec);
                }
            });
        } finally {
            workerLease.operationFinish();
        }
    }

    @Override
    public <T extends WorkSpec> DefaultWorkResult execute(WorkerDaemonAction<T> action, T spec) {
        throw new UnsupportedOperationException();
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    JvmMemoryStatus getJvmMemoryStatus() {
        return workerProcess.getJvmMemoryStatus();
    }

    @Override
    public void stop() {
        workerDaemonWorker.stop();
    }

    DaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public int getUses() {
        return uses;
    }
}
