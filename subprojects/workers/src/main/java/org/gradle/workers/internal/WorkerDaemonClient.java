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
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.worker.MultiRequestClient;
import org.gradle.process.internal.worker.WorkerProcess;

class WorkerDaemonClient implements Stoppable {
    public static final String DISABLE_EXPIRATION_PROPERTY_KEY = "org.gradle.workers.internal.disable-daemons-expiration";
    private final DaemonForkOptions forkOptions;
    private final MultiRequestClient<TransportableActionExecutionSpec, DefaultWorkResult> workerClient;
    private final WorkerProcess workerProcess;
    private final LogLevel logLevel;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private int uses;
    private boolean failed;
    private boolean cannotBeExpired = Boolean.getBoolean(DISABLE_EXPIRATION_PROPERTY_KEY);

    public WorkerDaemonClient(DaemonForkOptions forkOptions, MultiRequestClient<TransportableActionExecutionSpec, DefaultWorkResult> workerClient, WorkerProcess workerProcess, LogLevel logLevel, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        this.forkOptions = forkOptions;
        this.workerClient = workerClient;
        this.workerProcess = workerProcess;
        this.logLevel = logLevel;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
    }

    public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec) {
        uses++;
        return workerClient.run(actionExecutionSpecFactory.newTransportableSpec(spec));
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    JvmMemoryStatus getJvmMemoryStatus() {
        return workerProcess.getJvmMemoryStatus();
    }

    @Override
    public void stop() {
        workerClient.stop();
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

    public boolean isProcess(WorkerProcess workerProcess) {
        return this.workerProcess.equals(workerProcess);
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isNotExpirable() {
        return cannotBeExpired;
    }

    @Override
    public String toString() {
        return "WorkerDaemonClient{" +
            " log level=" + logLevel +
            ", use count=" + uses +
            ", has failed=" + failed +
            ", can be expired=" + !cannotBeExpired +
            ", workerProcess=" + workerProcess +
            ", forkOptions=" + forkOptions +
            '}';
    }
}
