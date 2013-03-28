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

package org.gradle.api.internal.changedetection;

import org.gradle.api.Action;

public interface TaskUpToDateState {
    /**
     * Executes the provided action for every change that makes this task out-of-date.
     */
    void findChanges(Action<? super TaskUpToDateStateChange> action);

    /**
     * Returns if the state is up-to-date. If this method returns true, then {@link #findChanges} will not execute the action.
     * Implementations should ensure that this method is cheap to execute after {@link #findChanges} has already been executed.
     */
    boolean isUpToDate();

    /**
     * Snapshot any final state after the task has executed. This method is executed only if the task is to be executed.
     * Any persistent state should be added to the {@link org.gradle.api.internal.changedetection.TaskExecution} object for the current execution.
     */
    void snapshotAfterTask();
}
