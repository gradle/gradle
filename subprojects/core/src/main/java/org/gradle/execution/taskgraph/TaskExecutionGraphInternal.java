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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

// Public `TaskExecutionGraph` service shadowed at the project scope by the IP reporting wrapper
@ServiceScope({Scope.Build.class, Scope.Project.class})
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
     * Get an aggregate view of all listeners registered by {@link #addExecutionListener(TaskExecutionGraphExecutionListener)}.
     */
    TaskExecutionGraphExecutionListener getGraphExecutionListeners();

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
     * Detaches the work attached by {@link #populate(FinalizedExecutionPlan)}, so that queries against this
     * graph no longer see it.
     */
    void depopulate();

    /**
     * Get the execution plan that Configuration Cache should serialize.
     *
     * @return null if the graph is not populated
     */
    // TODO: We should find another way for CC to access this work graph rather
    // than routing it through an internal interface of a public API.
    @Nullable FinalizedExecutionPlan getExecutionPlan();

    /**
     * Resets the lifecycle for this graph.
     */
    void resetState();

    @SuppressWarnings("deprecation")
    org.gradle.api.execution.TaskExecutionListener getLegacyTaskListenerBroadcast();

}
