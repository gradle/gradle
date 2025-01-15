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

import org.gradle.internal.concurrent.ExecutorPolicy;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.ManagedExecutorImpl;
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultGradleEnterprisePluginBackgroundJobExecutors implements GradleEnterprisePluginBackgroundJobExecutorsInternal {
    private final ManagedExecutor executorService = createExecutor();
    private final DevelocityPluginUnsafeConfigurationService unsafeConfigurationService;

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
    public DefaultGradleEnterprisePluginBackgroundJobExecutors(DevelocityPluginUnsafeConfigurationService unsafeConfigurationService) {
        this.unsafeConfigurationService = unsafeConfigurationService;
    }

    @Override
    public Executor getUserJobExecutor() {
        return this::executeUserJob;
    }

    private void executeUserJob(Runnable job) {
        executorService.execute(() -> unsafeConfigurationService.withConfigurationInputTrackingDisabled(() -> {
            job.run();
            return null;
        }));
    }

    @Override
    public boolean isInBackground() {
        return Thread.currentThread() instanceof BackgroundThread;
    }

    @Override
    public void shutdown() {
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
