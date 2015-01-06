/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TODO: Expand this to support a shared thread pool + dedicated worker pools?
public class FixedExecutorFactory implements ExecutorFactory {
    private final int maxWorkerThreads;

    public FixedExecutorFactory(int maxWorkerThreads) {
        this.maxWorkerThreads = maxWorkerThreads;
    }

    public StoppableExecutor create(String displayName) {
        StoppableExecutorImpl executor = new StoppableExecutorImpl(displayName, createExecutor(displayName));
        return executor;
    }

    protected ExecutorService createExecutor(String displayName) {
        if (maxWorkerThreads < 0) {
            return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DefaultExecutorFactory.ThreadFactoryImpl(displayName));
        } else if (maxWorkerThreads == 0) {
            return MoreExecutors.sameThreadExecutor();
        } else {
            return Executors.newFixedThreadPool(maxWorkerThreads, new DefaultExecutorFactory.ThreadFactoryImpl(displayName));
        }
    }

    private class StoppableExecutorImpl implements StoppableExecutor {
        private final String displayName;
        private final ExecutorService executor;
        private final ThreadLocal<Runnable> executing = new ThreadLocal<Runnable>();
        private final Set<Throwable> failures = new CopyOnWriteArraySet<Throwable>();

        public StoppableExecutorImpl(String displayName, ExecutorService executor) {
            this.displayName = displayName;
            this.executor = executor;
        }

        public void execute(final Runnable command) {
            executor.execute(new Runnable() {
                public void run() {
                    executing.set(command);
                    try {
                        command.run();
                    } catch (Throwable throwable) {
                        failures.add(throwable);
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
            if (!failures.isEmpty()) {
                // TODO: Grab all of the exceptions, not just the first one.
                throw new GradleException(String.format("Failed to execute %s", displayName), failures.iterator().next());
            }
        }
    }
}
