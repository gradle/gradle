/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.configurationcache.InputTrackingState;
import org.gradle.internal.concurrent.ExecutorPolicy;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.ManagedExecutorImpl;
import org.gradle.internal.enterprise.GradleEnterprisePluginBackgroundJobExecutors;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@ServiceScope(Scopes.Gradle.class)
public class DefaultGradleEnterprisePluginBackgroundJobExecutors implements GradleEnterprisePluginBackgroundJobExecutors {
    private final ManagedExecutor executorService = createExecutor();
    private final InputTrackingState inputTrackingState;

    private static ManagedExecutor createExecutor() {
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
            4, 4,
            30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new BackgroundThreadFactory()
        );
        poolExecutor.allowCoreThreadTimeOut(true);

        return new ManagedExecutorImpl(poolExecutor, new ExecutorPolicy.CatchAndRecordFailures());
    }

    @Inject
    public DefaultGradleEnterprisePluginBackgroundJobExecutors(InputTrackingState inputTrackingState) {
        this.inputTrackingState = inputTrackingState;
    }

    @Override
    public Executor getUserJobExecutor() {
        return this::executeUserJob;
    }

    private void executeUserJob(Runnable job) {
        executorService.execute(() -> runWithInputTrackingDisabled(job));
    }

    private void runWithInputTrackingDisabled(Runnable job) {
        inputTrackingState.disableForCurrentThread();
        try {
            job.run();
        } finally {
            inputTrackingState.restoreForCurrentThread();
        }
    }

    @Override
    public boolean isInBackground() {
        return Thread.currentThread() instanceof BackgroundThread;
    }

    /**
     * Shuts the executors down.
     * All executors immediately stops accepting new jobs. The method blocks until already submitted jobs complete.
     *
     * @throws RuntimeException any exception or error thrown by a job is rethrown from this method, potentially wrapped as a RuntimeException
     */
    public void stop() {
        if (executorService.isShutdown()) {
            return;
        }
        executorService.stop();
    }

    private static final class BackgroundThreadFactory implements ThreadFactory {
        private static final String NAME = "gradle-enterprise-background-job";

        private final ThreadGroup group = new ThreadGroup(NAME);
        private final AtomicLong counter = new AtomicLong();

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new BackgroundThread(group, r, NAME + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class BackgroundThread extends Thread {
        BackgroundThread(ThreadGroup group, Runnable r, String s) {
            super(group, r, s);
        }
    }
}
