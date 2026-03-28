/*
 * Copyright 2025 the original author or authors.
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

import java.util.concurrent.ForkJoinTask;

/**
 * A managed executor backed by a {@link java.util.concurrent.ForkJoinPool} that supports
 * direct {@link ForkJoinTask} invocation.
 *
 * <p>Unlike {@link ManagedExecutor}, which wraps tasks in {@code FutureTask} via
 * {@code AbstractExecutorService.submit()}, this interface provides direct access to
 * {@link java.util.concurrent.ForkJoinPool#invoke(ForkJoinTask)} so that
 * {@link java.util.concurrent.RecursiveTask} and {@link java.util.concurrent.RecursiveAction}
 * work correctly with fork/join work-stealing and managed blocking.</p>
 */
public interface ManagedForkJoinPool extends ManagedExecutor {

    /**
     * Submits the given task and blocks until it completes, returning its result.
     *
     * <p>This delegates directly to {@link java.util.concurrent.ForkJoinPool#invoke(ForkJoinTask)},
     * preserving fork/join semantics including work-stealing and compensation for blocked threads.</p>
     *
     * @param task the task to execute
     * @param <T> the result type
     * @return the task's result
     */
    <T> T invoke(ForkJoinTask<T> task);
}
