/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.operations.BuildOperationRef;

import java.util.List;

/**
 * Allows asynchronous work to be tracked based on the build operation it is associated with.
 */
public interface AsyncWorkTracker {
    enum ProjectLockRetention {
        RETAIN_PROJECT_LOCKS, RELEASE_PROJECT_LOCKS, RELEASE_AND_REACQUIRE_PROJECT_LOCKS
    }
    /**
     * Register a new item of asynchronous work with the provided build operation.
     *
     * @param operation - The build operation to associate the asynchronous work with
     * @param completion - The completion of the asynchronous work
     * @throws IllegalStateException when new work is submitted for an operation while another thread is waiting in {@link #waitForCompletion(BuildOperationRef, boolean)} for the same operation.
     */
    void registerWork(BuildOperationRef operation, AsyncWorkCompletion completion);

    /**
     * Blocks waiting for the completion of all items of asynchronous work associated with the provided build operation.
     * Only waits for work that has been registered at the moment the method is called.  In the event that there are failures in
     * the asynchronous work, a {@link org.gradle.internal.exceptions.MultiCauseException} will be thrown with any exceptions
     * thrown.
     *
     * @param operation - The build operation whose asynchronous work should be completed
     * @param lockRetention - How project locks should be treated while waiting on work
     */
    void waitForCompletion(BuildOperationRef operation, ProjectLockRetention lockRetention);

    /**
     * Blocks waiting for the completion of the specified items of asynchronous work.
     * Only waits for work in the list at the moment the method is called.  In the event that there are failures in
     * the asynchronous work, a {@link org.gradle.internal.exceptions.MultiCauseException} will be thrown with any exceptions
     * thrown.
     *
     * @param workCompletions - The items of work that should be waited on
     * @param lockRetention - How project locks should be treated while waiting on work
     */
    void waitForCompletion(BuildOperationRef operation, List<AsyncWorkCompletion> workCompletions, ProjectLockRetention lockRetention);

    /**
     * Returns true if the given operation has work registered that has not completed.
     */
    boolean hasUncompletedWork(BuildOperationRef operation);
}
