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

import java.util.concurrent.TimeUnit;

public interface ExecutorFactory {
    /**
     * Creates an executor which can run multiple actions concurrently. It is the caller's responsibility to stop the executor.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for the this executor. Used for thread names, logging and error message.
     * @return The executor.
     */
    ManagedExecutor create(String displayName);

    /**
     * Creates an executor which can run multiple tasks concurrently. It is the caller's responsibility to stop the executor.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for the this executor. Used for thread names, logging and error message.
     * @param fixedSize The maximum number of threads allowed
     * @return The executor.
     */
    ManagedExecutor create(String displayName, int fixedSize);

    /**
     * Creates an executor which can run multiple tasks concurrently. It is the caller's responsibility to stop the executor.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for this executor. Used for thread names, logging and error message.
     * @param corePoolSize The number of threads to keep in the pool
     * @param maximumPoolSize The maximum number of threads allowed
     * @param keepAliveTime  when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param timeUnit the time unit for the {@code keepAliveTime} argument
     * @return The executor.
     */
    ManagedThreadPoolExecutor createThreadPool(String displayName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit);

    /**
     * Creates a scheduled executor which can run tasks periodically. It is the caller's responsibility to stop the executor.
     *
     * The created scheduled executor has a fixed pool size of {@literal fixedSize}.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for the this executor. Used for thread names, logging and error message.
     * @param fixedSize The maximum number of threads allowed
     * @return The executor
     * @see java.util.concurrent.ScheduledExecutorService
     */
    ManagedScheduledExecutor createScheduled(String displayName, int fixedSize);
}
