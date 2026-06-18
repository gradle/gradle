/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.NullMarked;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * An {@link Executor} for running exec-handle work that also tracks the live handles it spawns,
 * so they can be stopped automatically.
 */
@NullMarked
@ServiceScope({Scope.Global.class, Scope.Project.class})
public class ExecHandleTrackingExecutor implements Executor, Stoppable, ExecHandleListener {

    public static ExecHandleTrackingExecutor create(ExecutorFactory executorFactory) {
        return new ExecHandleTrackingExecutor(executorFactory.create("Exec process"));
    }

    private final ManagedExecutor executor;

    private final Set<ExecHandle> handles = ConcurrentHashMap.newKeySet();

    private ExecHandleTrackingExecutor(ManagedExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public void beforeExecutionStarted(ExecHandle execHandle) {
    }

    @Override
    public void executionStarted(ExecHandle execHandle) {
        handles.add(execHandle);
    }

    @Override
    public void executionFinished(ExecHandle execHandle, ExecResult execResult) {
        handles.remove(execHandle);
    }

    @Override
    public void executionDetached(ExecHandle execHandle) {
        handles.remove(execHandle);
    }

    /**
     * The number of live, non-detached handles currently tracked.
     *
     * @return the registry size
     */
    int trackedHandleCount() {
        return handles.size();
    }

    @Override
    public void stop() {
        try {
            for (ExecHandle handle : handles) {
                if (handle.getState() == ExecHandleState.STARTED) {
                    handle.abortNonBlocking();
                }
            }
            executor.stop();
        } finally {
            handles.clear();
        }
    }
}
