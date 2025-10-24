/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.ScheduledWork;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@ServiceScope(Scope.Build.class)
public interface TaskExecutionGraphInternal extends TaskExecutionGraph {
    /**
     * Adds the internal listener for task execution graph events.
     * These listeners are not persisted through the configuration cache, beware if you want to receive graph execution events with CC enabled.
     *
     * @param listener the listener
     */
    void addExecutionListener(TaskExecutionGraphExecutionListener listener);

    /**
     * Removes the previously registered internal listener.
     *
     * @param listener the listener
     */
    void removeExecutionListener(TaskExecutionGraphExecutionListener listener);

    /**
     * Find a task with the given path in the task graph.
     *
     * @param path the path of the task to find in the task graph
     * @return the task with the given path if it is present in the task graph, null otherwise
     */
    @Nullable
    Task findTask(String path);

    /**
     * Attaches the work that this graph will run. Fires events and no further tasks should be added.
     */
    void populate(FinalizedExecutionPlan plan);

    /**
     * Executes the given work. Discards the contents of this graph when completed. Should call {@link #populate(FinalizedExecutionPlan)} prior to
     * calling this method.
     */
    ExecutionResult<Void> execute(FinalizedExecutionPlan plan);

    /**
     * Set of requested tasks.
     */
    Set<Task> getFilteredTasks();

    /**
     * Returns the number of work items in this graph.
     */
    int size();

    default boolean hasScheduledWork() {
        return size() > 0;
    }

    /**
     * Returns a snapshot of currently scheduled nodes.
     */
    ScheduledWork collectScheduledWork();

    /**
     * Resets the lifecycle for this graph.
     */
    void resetState();

    @SuppressWarnings("deprecation")
    org.gradle.api.execution.TaskExecutionListener getLegacyTaskListenerBroadcast();
}
