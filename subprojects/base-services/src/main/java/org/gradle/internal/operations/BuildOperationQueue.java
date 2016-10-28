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
 * The queue is single use in that no further work can be added once {@link #waitForCompletion()} is called.
 * <p>
 * A queue instance is not threadsafe and MUST only be used from a single thread.
 *
 * @param <T> type of build operations to hold
 */
public interface BuildOperationQueue<T extends BuildOperation> {

    /**
     * Adds an operation to be executed, potentially executing it instantly.
     *
     * @param operation operation to execute
     */
    void add(T operation);

    /**
     * Cancels all queued operations in this queue.  Any operations that have started will be allowed to complete.
     */
    void cancel();

    /**
     * Waits for all previously added operations to complete.
     * <p>
     * On failure, some effort is made to cancel any operations that have not started.
     *
     * @throws MultipleBuildOperationFailures if <em>any</em> operation failed
     */
    void waitForCompletion() throws MultipleBuildOperationFailures;

    /**
     * Sets the location of a log file where build operation output can be found.  For use in exceptions.
     */
    void setLogLocation(String logLocation);
}
