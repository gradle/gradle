/*
 * Copyright 2023 the original author or authors.
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
import java.util.concurrent.TimeUnit;

public interface ManagedThreadPoolExecutor extends ManagedExecutor {
    void setThreadFactory(@Nonnull ThreadFactory threadFactory);

    ThreadFactory getThreadFactory();

    void setRejectedExecutionHandler(@Nonnull RejectedExecutionHandler handler);

    RejectedExecutionHandler getRejectedExecutionHandler();

    void setCorePoolSize(int corePoolSize);

    int getCorePoolSize();

    boolean prestartCoreThread();

    int prestartAllCoreThreads();

    boolean allowsCoreThreadTimeOut();

    void allowCoreThreadTimeOut(boolean value);

    void setMaximumPoolSize(int maximumPoolSize);

    int getMaximumPoolSize();

    void setKeepAliveTime(long time, TimeUnit unit);

    long getKeepAliveTime(TimeUnit unit);

    BlockingQueue<Runnable> getQueue();

    boolean remove(Runnable task);

    void purge();

    int getPoolSize();

    int getActiveCount();

    int getLargestPoolSize();

    long getTaskCount();

    long getCompletedTaskCount();
}
