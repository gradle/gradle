/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;

import java.util.List;

/**
 * Represents a graph of dependent tasks, returned in execution order.
 */
public interface TaskExecutionPlan {
    /**
     * Provides a ready-to-execute task that matches the specified criteria. A task is ready-to-execute if all of it's dependencies have been completed successfully.
     * If the next matching task is not ready-to-execute, this method will block until it is ready.
     * If no tasks remain that match the criteria, null will be returned.
     * @param criteria Only tasks matching this Spec will be returned.
     * @return The next matching task, or null if no matching tasks remain.
     */
    TaskInfo getTaskToExecute(Spec<TaskInfo> criteria);

    /**
     * Signals to the plan that execution of this task has completed. Execution is complete if the task succeeds, fails, or an exception is thrown during execution.
     * @param task the completed task.
     */
    void taskComplete(TaskInfo task);

    /**
     * Blocks until all tasks in the plan have been processed. This method will only return when every task in the plan has either completed, failed or been skipped.
     */
    void awaitCompletion();

    /**
     * @return The list of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    List<Task> getTasks();

    //TODO SF this should replace completely getTaskToExecute(), inherit and expand existing unit test coverage
    TaskInfo getTaskToExecute();
}
