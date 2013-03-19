/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;

import java.util.Collection;

public interface UpToDateRule {
    /**
     * Creates the transient state for the given task.
     *
     * @param task The task to be executed.
     * @param previousExecution The previous execution for this task, if any. May be null.
     * @param currentExecution The current execution. The rule may mutate this.
     * @return The state.
     */
    TaskUpToDateState create(TaskInternal task, TaskExecution previousExecution, TaskExecution currentExecution);

    interface TaskUpToDateState {
        /**
         * Checks if the task is up-to-date. If not, this method must add at least 1 message explaining why the task is out-of-date to the given collection. Note that this method may not be called for
         * a given execution. Also note, this method is called only when the previous execution is not null.
         *
         * @param messages The out-of-date messages.
         */
        void checkUpToDate(Collection<String> messages);

        /**
         * Snapshot any final state after the task has executed. This method is executed only if the task is to be executed. Any persistent state should be added to the {@link TaskExecution} object
         * passed to {@link UpToDateRule#create}.
         */
        void snapshotAfterTask();
    }
}
