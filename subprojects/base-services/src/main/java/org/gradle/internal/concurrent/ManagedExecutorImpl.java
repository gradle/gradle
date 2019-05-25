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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class ManagedExecutorImpl extends AbstractDelegatingExecutorService implements ManagedExecutor {
    private final ExecutorService executor;
    private final ThreadLocal<Object> executing = new ThreadLocal<Object>();
    private final ExecutorPolicy executorPolicy;

    ManagedExecutorImpl(ExecutorService executor, ExecutorPolicy executorPolicy) {
        super(executor);
        this.executor = executor;
        this.executorPolicy = executorPolicy;
    }

    @Override
    public void execute(final Runnable command) {
        executor.execute(trackedCommand(command));
    }

    protected Runnable trackedCommand(final Runnable command) {
        return new Runnable() {
            @Override
            public void run() {
                executing.set(command);
                try {
                    executorPolicy.onExecute(command);
                } finally {
                    executing.set(null);
                }
            }
        };
    }

    protected <V> Callable<V> trackedCommand(final Callable<V> command) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                executing.set(command);
                try {
                    return executorPolicy.onExecute(command);
                } finally {
                    executing.set(null);
                }
            }
        };
    }

    @Override
    public void requestStop() {
        executor.shutdown();
    }

    @Override
    public void stop() {
        stop(Integer.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Override
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
            executor.shutdownNow();
            throw UncheckedException.throwAsUncheckedException(e);
        }
        executorPolicy.onStop();
    }

    @Override
    public void setFixedPoolSize(int numThreads) {
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
            if (numThreads < threadPoolExecutor.getCorePoolSize()) {
                threadPoolExecutor.setCorePoolSize(numThreads);
                threadPoolExecutor.setMaximumPoolSize(numThreads);
            } else {
                threadPoolExecutor.setMaximumPoolSize(numThreads);
                threadPoolExecutor.setCorePoolSize(numThreads);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setKeepAlive(int timeout, TimeUnit timeUnit) {
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor)executor).setKeepAliveTime(timeout, timeUnit);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
