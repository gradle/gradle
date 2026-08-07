/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Task;

import java.util.Set;

/**
 * An execution plan that has been finalized and can no longer be mutated.
 *
 * <p>Implementations may or may not be thread-safe.</p>
 */
public interface QueryableExecutionPlan {

    /**
     * @return The set of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    Set<Task> getTasks();

    /**
     * Returns a snapshot of the requested tasks for this plan.
     */
    Set<Task> getRequestedTasks();

    /**
     * Returns a snapshot of the filtered tasks for this plan.
     */
    Set<Task> getFilteredTasks();

    /**
     * Returns a snapshot of the current set of scheduled nodes, which can later be visited.
     */
    ScheduledWork getScheduledNodes();

    /**
     * Returns the node for the supplied task that is part of this execution plan.
     *
     * @throws IllegalStateException When no node for the supplied task is part of this execution plan.
     */
    TaskNode getNode(Task task);

    /**
     * Returns the number of work items in the plan.
     */
    int size();

}
