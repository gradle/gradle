/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.execution;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.io.File;

/**
 * Provides changes to task input files to incremental task implementations.
 * Note that this is a stateful API:
 * <ul>
 *     <li>{@link #outOfDate} and {@link #removed} can each only be executed a single time per {@link TaskInputChanges} instance.</li>
 *     <li>{@link #outOfDate} must be executed before {@link #removed} is called.</li>
 * </ul>
 */
@Incubating
public interface TaskInputChanges {
    /**
     * Specifies if incremental build is not possible due to changed Input Properties, Output Files, etc.
     * In this case, every file will be considered to be 'out-of-date'.
     */
    boolean isAllOutOfDate();

    /**
     * Executes the action for all of the input files that are out-of-date since the previous task execution.
     * This method may only be executed a single time for a single {@link TaskInputChanges} instance.
     *
     * @throws IllegalStateException on second and subsequent invocations.
     */
    void outOfDate(Action<? super InputFileChange> outOfDateAction);

    /**
     * Executes the action for all of the input files that were removed since the previous task execution.
     * This method may only be executed a single time for a single {@link TaskInputChanges} instance.
     * This method may only be called after {@link #outOfDate} has executed.
     *
     * @throws IllegalStateException if invoked prior to {@link #outOfDate}, or if invoked more than once.
     */
    void removed(Action<? super InputFileChange> removedAction);

    /**
     * A change to an input file.
     */
    interface InputFileChange {
        /**
         * Was the file added?
         * @return true if the file was added since the last execution
         */
        boolean isAdded();

        /**
         * Was the file modified?
         * @return if the file was modified
         */
        boolean isModified();

        /**
         * Was the file removed?
         * @return true if the file was removed since the last execution
         */
        boolean isRemoved();

        /**
         * The input file, which may no longer exist.
         * @return the input file
         */
        File getFile();
    }
}
