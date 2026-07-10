/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.work;

/**
 * Represents a pool of threads that are used for executing workers.
 */
public interface WorkerThreadPool {
    /**
     * Called from a worker thread to indicate that it is about to start doing some work that may block.
     * This allows the thread pool to adjust the number of threads in the pool to keep full parallelism of work.
     *
     * <p>
     * "Blocking" work is any work that consumes very little CPU time, and a lot of wall clock time,
     * like sending or receiving data over the disk or network. Without increasing the number of available threads,
     * blocking a worker thread prevents full utilization of all CPU cores.
     * </p>
     */
    void notifyBlockingWorkStarting();

    /**
     * Called from a worker thread to indicate that it has finished doing some work that may block.
     * This is called after {@link #notifyBlockingWorkStarting()} to restore the thread pool to its normal size.
     */
    void notifyBlockingWorkFinished();
}
