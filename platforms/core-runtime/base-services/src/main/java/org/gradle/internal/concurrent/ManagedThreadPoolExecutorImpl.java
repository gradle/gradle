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

import javax.annotation.Nonnull;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ManagedThreadPoolExecutorImpl extends AbstractManagedExecutor<ThreadPoolExecutor> implements ManagedThreadPoolExecutor {
    public ManagedThreadPoolExecutorImpl(ThreadPoolExecutor delegate, ExecutorPolicy executorPolicy) {
        super(delegate, executorPolicy);
    }

    @Override
    public void setThreadFactory(@Nonnull ThreadFactory threadFactory) {
        delegate.setThreadFactory(threadFactory);
    }

    @Override
    public ThreadFactory getThreadFactory() {
        return delegate.getThreadFactory();
    }

    @Override
    public void setRejectedExecutionHandler(@Nonnull RejectedExecutionHandler handler) {
        delegate.setRejectedExecutionHandler(handler);
    }

    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return delegate.getRejectedExecutionHandler();
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
        delegate.setCorePoolSize(corePoolSize);
    }

    @Override
    public int getCorePoolSize() {
        return delegate.getCorePoolSize();
    }

    @Override
    public boolean prestartCoreThread() {
        return delegate.prestartCoreThread();
    }

    @Override
    public int prestartAllCoreThreads() {
        return delegate.prestartAllCoreThreads();
    }

    @Override
    public boolean allowsCoreThreadTimeOut() {
        return delegate.allowsCoreThreadTimeOut();
    }

    @Override
    public void allowCoreThreadTimeOut(boolean value) {
        delegate.allowCoreThreadTimeOut(value);
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        delegate.setMaximumPoolSize(maximumPoolSize);
    }

    @Override
    public int getMaximumPoolSize() {
        return delegate.getMaximumPoolSize();
    }

    @Override
    public void setKeepAliveTime(long time, TimeUnit unit) {
        delegate.setKeepAliveTime(time, unit);
    }

    @Override
    public long getKeepAliveTime(TimeUnit unit) {
        return delegate.getKeepAliveTime(unit);
    }

    @Override
    public BlockingQueue<Runnable> getQueue() {
        return delegate.getQueue();
    }

    @Override
    public boolean remove(Runnable task) {
        return delegate.remove(task);
    }

    @Override
    public void purge() {
        delegate.purge();
    }

    @Override
    public int getPoolSize() {
        return delegate.getPoolSize();
    }

    @Override
    public int getActiveCount() {
        return delegate.getActiveCount();
    }

    @Override
    public int getLargestPoolSize() {
        return delegate.getLargestPoolSize();
    }

    @Override
    public long getTaskCount() {
        return delegate.getTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        return delegate.getCompletedTaskCount();
    }
}
