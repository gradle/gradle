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

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.internal.work.WorkerLeaseRegistry;

import java.util.List;
import java.util.Set;

/**
 * Represents a graph of dependent tasks, returned in execution order.
 */
public interface TaskExecutionPlan extends Describable {
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

    /**
     * @return The set of all filtered tasks that don't get executed.
     */
    Set<Task> getFilteredTasks();

    /**
     * Selects a task that's ready to execute and executes the provided action against it.  If no tasks are ready, blocks until one
     * can be executed.  If all tasks have been executed, returns false.
     *
     * @param parentWorkerLease
     * @param taskExecution
     * @return true if there are more tasks waiting to execute, false if all tasks have executed.
     */
    boolean executeWithTask(WorkerLeaseRegistry.WorkerLease parentWorkerLease, Action<TaskInfo> taskExecution);
}
