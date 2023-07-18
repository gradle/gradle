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

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DefaultExecutorFactory implements ExecutorFactory, Stoppable {
    private final Set<ManagedExecutor> executors = new CopyOnWriteArraySet<ManagedExecutor>();
    @Nullable
    private final ClassLoader threadFactoryContextClassloader;

    public DefaultExecutorFactory() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public DefaultExecutorFactory(@Nullable ClassLoader threadFactoryContextClassloader) {
        this.threadFactoryContextClassloader = threadFactoryContextClassloader;
    }

    @Override
    public void stop() {
        try {
            CompositeStoppable.stoppable(executors).stop();
        } finally {
            executors.clear();
        }
    }

    @Override
    public ManagedExecutor create(String displayName) {
        ManagedExecutor executor = new TrackedManagedExecutor(createExecutor(displayName), new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    protected ExecutorService createExecutor(String displayName) {
        return Executors.newCachedThreadPool(newThreadFactory(displayName));
    }

    @Override
    public ManagedExecutor create(String displayName, int fixedSize) {
        TrackedManagedExecutor executor = new TrackedManagedExecutor(createExecutor(displayName, fixedSize), new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    @Override
    public ManagedThreadPoolExecutor createThreadPool(String displayName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit) {
        ThreadPoolExecutor executorService = createThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, timeUnit, newThreadFactory(displayName));
        TrackedThreadPoolManagedExecutor executor = new TrackedThreadPoolManagedExecutor(executorService, new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    private static ThreadPoolExecutor createThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            timeUnit,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory);
    }

    protected ExecutorService createExecutor(String displayName, int fixedSize) {
        return Executors.newFixedThreadPool(fixedSize, newThreadFactory(displayName));
    }

    @Override
    public ManagedScheduledExecutor createScheduled(String displayName, int fixedSize) {
        ManagedScheduledExecutor executor = new TrackedScheduledManagedExecutor(createScheduledExecutor(displayName, fixedSize), new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    private ScheduledExecutorService createScheduledExecutor(String displayName, int fixedSize) {
        return new ScheduledThreadPoolExecutor(fixedSize, newThreadFactory(displayName));
    }

    private ThreadFactory newThreadFactory(String displayName) {
        return new ThreadFactoryImpl(displayName, threadFactoryContextClassloader);
    }

    private class TrackedManagedExecutor extends ManagedExecutorImpl {
        TrackedManagedExecutor(ExecutorService executor, ExecutorPolicy executorPolicy) {
            super(executor, executorPolicy);
        }

        @Override
        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            try {
                super.stop(timeoutValue, timeoutUnits);
            } finally {
                executors.remove(this);
            }
        }
    }

    private class TrackedScheduledManagedExecutor extends ManagedScheduledExecutorImpl {
        TrackedScheduledManagedExecutor(ScheduledExecutorService executor, ExecutorPolicy executorPolicy) {
            super(executor, executorPolicy);
        }

        @Override
        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            try {
                super.stop(timeoutValue, timeoutUnits);
            } finally {
                executors.remove(this);
            }
        }
    }

    private class TrackedThreadPoolManagedExecutor extends ManagedThreadPoolExecutorImpl {
        public TrackedThreadPoolManagedExecutor(ThreadPoolExecutor delegate, ExecutorPolicy executorPolicy) {
            super(delegate, executorPolicy);
        }

        @Override
        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            try {
                super.stop(timeoutValue, timeoutUnits);
            } finally {
                executors.remove(this);
            }
        }
    }
}
