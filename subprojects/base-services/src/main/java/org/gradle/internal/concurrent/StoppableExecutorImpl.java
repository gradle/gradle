/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.concurrent;

import org.gradle.internal.UncheckedException;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

class StoppableExecutorImpl extends AbstractExecutorService implements StoppableExecutor {
    private final ExecutorService executor;
    private final ThreadLocal<Runnable> executing = new ThreadLocal<Runnable>();
    private final ExecutorPolicy executorPolicy;
    StoppableExecutorImpl(ExecutorService executor, ExecutorPolicy executorPolicy) {
        this.executor = executor;
        this.executorPolicy = executorPolicy;
    }

    public void execute(final Runnable command) {
        executor.execute(new Runnable() {
            public void run() {
            executing.set(command);
            try {
                executorPolicy.onExecute(command);
            } finally {
                executing.set(null);
            }
            }
        });
    }

    public void requestStop() {
        executor.shutdown();
    }

    public void stop() {
        stop(Integer.MAX_VALUE, TimeUnit.SECONDS);
    }

    public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
        requestStop();
        if (executing.get() != null) {
            throw new IllegalStateException("Cannot stop this executor from an executor thread.");
        }
        try {
            if (!executor.awaitTermination(timeoutValue, timeoutUnits)) {
                executor.shutdownNow();
                throw new IllegalStateException("Timeout waiting for concurrent jobs to complete.");
            }
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        }
        executorPolicy.onStop();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
