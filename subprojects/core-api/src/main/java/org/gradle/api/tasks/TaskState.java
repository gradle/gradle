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

package org.gradle.api.tasks;

import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * {@code TaskState} provides information about the execution state of a {@link org.gradle.api.Task}.
 * <p>
 * You can obtain a {@code TaskState} instance by calling {@link org.gradle.api.Task#getState()}.
 * <p>
 * Accessing the details of the task state is only meaningful after the task has been
 * {@link #getExecuted() executed}.
 */
@HasInternalProtocol
public interface TaskState {
    /**
     * Returns true if this task has been executed.
     * <p>
     * The task is considered executed if an attempt of the execution was made,
     * and it has a definite outcome.
     * The outcome itself does not affect the execution status.
     * For instance, failed or skipped tasks will also be considered executed.
     *
     * @return true if this task has been executed.
     */
    boolean getExecuted();

    /**
     * Returns the exception describing the task failure, if any.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return The exception, or null if the task did not fail.
     */
    @Nullable
    Throwable getFailure();

    /**
     * Throws the task failure, if any. Does nothing if the task did not fail.
     * <p>
     * Calling this method is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @see #getFailure()
     */
    void rethrowFailure();

    /**
     * Checks if the task actually did any work.
     * <p>
     * Even if a task executes, it may determine that it has nothing to do.
     * For example, a compilation task may determine that source files have not changed
     * since the last time the task was run.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return true if this task has been executed and did any work.
     */
    boolean getDidWork();

    /**
     * Returns true if the execution of this task was skipped for some reason.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return true if this task has been executed and skipped.
     */
    boolean getSkipped();

    /**
     * Returns a message describing why the task was skipped.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return the message. returns null if the task was not skipped.
     */
    @Nullable
    String getSkipMessage();

    /**
     * Returns true if the execution of this task was skipped because the task was up-to-date.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return true if this task has been considered up-to-date
     * @since 2.5
     */
    boolean getUpToDate();

    /**
     * Returns true if the execution of this task was skipped due to task inputs are empty.
     * <p>
     * Checking this value is meaningful only after the task has been {@link #getExecuted() executed}.
     *
     * @return true if this task has no input files assigned
     * @since 3.4
     */
    boolean getNoSource();
}
