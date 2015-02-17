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
 * Queue for holding build operations and submitting them to an underlying executor.
 *
 * <p>
 * Once an OperationQueue has started to wait for the completion of previously added operations,
 * no new operations may be added to the queue. OperationQueues are not thread safe, so you
 * cannot add operations from multiple threads.
 * </p>
 *
 * @param <T> Type of build operations to hold.
 */
public interface OperationQueue<T extends BuildOperation> {
    /**
     * Adds an operation to be executed.
     *
     * @param operation operation to execute.
     */
    public void add(T operation);

    /**
     * Waits for all previously added operations to complete.
     * <p>
     * On failure, some effort is made to cancel any operations
     * that have not started.
     * </p>
     * @throws MultipleBuildOperationFailures if <em>any</em> operation failed.
     */
    public void waitForCompletion() throws MultipleBuildOperationFailures;
}
