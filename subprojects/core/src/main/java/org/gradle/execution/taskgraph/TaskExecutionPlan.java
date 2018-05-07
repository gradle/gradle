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

import org.gradle.api.Describable;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.work.WorkerLeaseRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Represents a graph of dependent tasks, returned in execution order.
 */
public interface TaskExecutionPlan extends Describable {
    @Nullable
    TaskInfo selectNextTask(WorkerLeaseRegistry.WorkerLease workerLease, ResourceLockState resourceLockState);

    void taskComplete(TaskInfo taskInfo);

    void abortAllAndFail(Throwable t);

    void cancelExecution();

    /**
     * <p>Returns the dependencies of a task which are part of the execution plan.</p>
     *
     * @return The tasks. Returns an empty set if there are no dependent tasks.
     * @throws IllegalStateException When the task is not part of the execution plan.
     *
     * @since 4.5
     */
    @Incubating
    Set<Task> getDependencies(Task task);

    /**
     * @return The list of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    List<Task> getTasks();

    /**
     * @return The set of all filtered tasks that don't get executed.
     */
    Set<Task> getFilteredTasks();

    void rethrowFailures();

    boolean allTasksComplete();

    boolean hasWorkRemaining();

}
