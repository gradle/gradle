/*
 * Copyright 2010 the original author or authors.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultExecutorFactory implements ExecutorFactory, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutorFactory.class);
    private final Set<StoppableExecutorImpl> executors = new CopyOnWriteArraySet<StoppableExecutorImpl>();

    public void stop() {
        try {
            CompositeStoppable.stoppable(executors).stop();
        } finally {
            executors.clear();
        }
    }

    public StoppableExecutor create(String displayName) {
        StoppableExecutorImpl executor = new StoppableExecutorImpl(createExecutor(displayName));
        executors.add(executor);
        return executor;
    }

    protected ExecutorService createExecutor(String displayName) {
        return Executors.newCachedThreadPool(new ThreadFactoryImpl(displayName));
    }

    private class StoppableExecutorImpl implements StoppableExecutor {
        private final ExecutorService executor;
        private final ThreadLocal<Runnable> executing = new ThreadLocal<Runnable>();
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        public StoppableExecutorImpl(ExecutorService executor) {
            this.executor = executor;
        }

        public void execute(final Runnable command) {
            executor.execute(new Runnable() {
                public void run() {
                    executing.set(command);
                    try {
                        command.run();
                    } catch (Throwable throwable) {
                        if (!failure.compareAndSet(null, throwable)) {
                            LOGGER.error(String.format("Failed to execute %s.", command), throwable);
                        }
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
                try {
                    if (!executor.awaitTermination(timeoutValue, timeoutUnits)) {
                        executor.shutdownNow();
                        throw new IllegalStateException("Timeout waiting for concurrent jobs to complete.");
                    }
                } catch (InterruptedException e) {
                    throw new UncheckedException(e);
                }
                if (failure.get() != null) {
                    throw UncheckedException.throwAsUncheckedException(failure.get());
                }
            } finally {
                executors.remove(this);
            }
        }
    }

    private static class ThreadFactoryImpl implements ThreadFactory {
        private final AtomicLong counter = new AtomicLong();
        private final String displayName;

        public ThreadFactoryImpl(String displayName) {
            this.displayName = displayName;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            long count = counter.incrementAndGet();
            if (count == 1) {
                thread.setName(displayName);
            } else {
                thread.setName(String.format("%s Thread %s", displayName, count));
            }
            return thread;
        }
    }
}
