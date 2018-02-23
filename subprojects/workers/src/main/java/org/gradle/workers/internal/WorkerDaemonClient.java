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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.worker.WorkerProcess;

class WorkerDaemonClient implements Worker, Stoppable {
    private final DaemonForkOptions forkOptions;
    private final WorkerDaemonProcess<ActionExecutionSpec> workerDaemonProcess;
    private final WorkerProcess workerProcess;
    private final LogLevel logLevel;
    private int uses;

    public WorkerDaemonClient(DaemonForkOptions forkOptions, WorkerDaemonProcess<ActionExecutionSpec> workerDaemonProcess, WorkerProcess workerProcess, LogLevel logLevel) {
        this.forkOptions = forkOptions;
        this.workerDaemonProcess = workerDaemonProcess;
        this.workerProcess = workerProcess;
        this.logLevel = logLevel;
    }

    @Override
    public DefaultWorkResult execute(final ActionExecutionSpec spec, WorkerLease parentWorkerWorkerLease, final BuildOperationState parentBuildOperation) {
        return execute(spec);
    }

    @Override
    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        uses++;
        return workerDaemonProcess.execute(spec);
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    JvmMemoryStatus getJvmMemoryStatus() {
        return workerProcess.getJvmMemoryStatus();
    }

    @Override
    public void stop() {
        workerDaemonProcess.stop();
    }

    DaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public int getUses() {
        return uses;
    }

    public KeepAliveMode getKeepAliveMode() {
        return forkOptions.getKeepAliveMode();
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }
}
