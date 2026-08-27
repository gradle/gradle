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

import org.gradle.api.Describable;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.worker.MultiRequestClient;
import org.gradle.process.internal.worker.WorkerProcess;

import java.util.Optional;

class WorkerDaemonClient implements Stoppable, Describable {
    public static final String DISABLE_EXPIRATION_PROPERTY_KEY = "org.gradle.workers.internal.disable-daemons-expiration";
    private final DaemonForkOptions forkOptions;
    private final MultiRequestClient<TransportableActionExecutionSpec, DefaultWorkResult> workerClient;
    private final WorkerProcess workerProcess;
    private final LogLevel logLevel;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private int uses;
    private volatile boolean executing;
    private volatile boolean abandoned;
    private boolean cannotBeExpired = Boolean.getBoolean(DISABLE_EXPIRATION_PROPERTY_KEY);

    public WorkerDaemonClient(DaemonForkOptions forkOptions, MultiRequestClient<TransportableActionExecutionSpec, DefaultWorkResult> workerClient, WorkerProcess workerProcess, LogLevel logLevel, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        this.forkOptions = forkOptions;
        this.workerClient = workerClient;
        this.workerProcess = workerProcess;
        this.logLevel = logLevel;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
    }

    void bindProblemsService(ProblemsInternal problems) {
        workerClient.bindProblemsService(problems);
    }

    void clearProblemsService() {
        workerClient.clearProblemsService();
    }

    public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec) {
        TransportableActionExecutionSpec transportableSpec = actionExecutionSpecFactory.newTransportableSpec(spec);
        uses++;
        executing = true;
        try {
            return workerClient.run(transportableSpec);
        } catch (Throwable t) {
            // The request never produced a response, typically because the thread waiting on it was
            // interrupted when the owning task exceeded its timeout. The build has given up on the result,
            // but the worker process is still running the work item, so this client is neither safe to
            // reuse nor safe to stop gracefully.
            abandoned = true;
            throw t;
        } finally {
            executing = false;
        }
    }

    /**
     * Whether this client is currently executing a work item, i.e. the worker process is busy running it.
     */
    public boolean isExecuting() {
        return executing;
    }

    /**
     * Whether a work item submitted to this client was abandoned before the worker reported a result.
     * The worker process may still be running that work item.
     */
    public boolean isAbandoned() {
        return abandoned;
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

    public void kill() {
        workerClient.stopNow();
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

    public boolean isFailed() {
        return workerProcess.getExecResult().map(execResult -> execResult.getExitValue() != 0).orElse(false);
    }

    public Optional<Integer> getExitCode() {
        return workerProcess.getExecResult().map(ExecResult::getExitValue);
    }

    public boolean isNotExpirable() {
        return cannotBeExpired;
    }

    @Override
    public String getDisplayName() {
        return workerProcess.getDisplayName();
    }

    @Override
    public String toString() {
        return "WorkerDaemonClient{" +
            " log level=" + logLevel +
            ", use count=" + uses +
            ", has failed=" + isFailed() +
            ", can be expired=" + !cannotBeExpired +
            ", workerProcess=" + workerProcess +
            ", forkOptions=" + forkOptions +
            '}';
    }
}
