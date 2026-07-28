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

package org.gradle.internal.operations;

/**
 * An individual active, single use, queue of build operations.
 * <p>
 * The queue is active in that operations are potentially executed as soon as they are added.
 * The queue is single use in that no further work can be added once {@link #waitForCompletion()} has completed.
 * <p>
 * A queue instance is threadsafe. Build operations can submit further operations to the queue but must not block waiting for them to complete.
 *
 * @param <T> type of build operations to hold
 */
public interface BuildOperationQueue<T extends BuildOperation> {

    /**
     * Adds an operation to be executed, potentially executing it instantly.
     * <p>
     * Execution is constrained by the {@linkplain org.gradle.internal.work.WorkerLimits#getMaxWorkerCount()
     * configured maximum number of workers}: the operation is required to acquire a
     * {@linkplain org.gradle.internal.work.WorkerLeaseRegistry worker lease} before proceeding.
     * Intended for CPU intensive operations.
     *
     * @param operation operation to execute
     * @see #addUnconstrained for IO intensive operations
     */
    void add(T operation);

    /**
     * Adds an operation to be executed without holding a worker lease, potentially executing it instantly.
     * <p>
     * Execution is not constrained by the {@linkplain org.gradle.internal.work.WorkerLimits#getMaxWorkerCount()
     * configured maximum number of workers}, allowing as many threads as required up to a
     * {@linkplain org.gradle.internal.work.WorkerLimits#getMaxUnconstrainedWorkerCount() separate, higher limit}.
     * Intended for IO intensive operations. Such operations must not depend on acquiring a worker lease.
     * <p>
     * Operations added this way are tracked by this queue just like those added via {@link #add}: they are
     * covered by {@link #cancel()} and awaited by {@link #waitForCompletion()}, and their failures are
     * reported together.
     *
     * @param operation operation to execute
     * @see #add for CPU intensive operations
     */
    void addUnconstrained(T operation);

    /**
     * Cancels all queued operations in this queue.  Any operations that have started will be allowed to complete.
     *
     * @throws IllegalStateException if someone is waiting for completion of this queue
     */
    void cancel();

    /**
     * Waits for all previously added operations to complete, or for the queue to finish after cancellation.
     *
     * @throws MultipleBuildOperationFailures if <em>any</em> operation failed
     */
    void waitForCompletion() throws MultipleBuildOperationFailures;

    /**
     * Sets the location of a log file where build operation output can be found.  For use in exceptions.
     */
    void setLogLocation(String logLocation);

    interface QueueWorker<O extends BuildOperation> {
        void execute(O buildOperation);
    }
}
