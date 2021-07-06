/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.Node;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface TaskExecutionGraphInternal extends TaskExecutionGraph {
    /**
     * Sets the filter to use when adding tasks to this graph. Only those tasks which are accepted by the given filter
     * will be added to this graph.
     */
    void useFilter(Spec<? super Task> filter);

    /**
     * Adds the given tasks to this graph. Tasks are executed in an arbitrary order. The tasks
     * are executed before any tasks from a subsequent call to this method are executed.
     *
     * <p>Does not add the dependencies of the tasks to this graph.</p>
     */
    void addEntryTasks(Iterable<? extends Task> tasks);

    /**
     * Adds the given nodes to this graph.
     *
     * <p>Does not add the dependencies of the nodes to this graph.</p>
     */
    void addNodes(Collection<? extends Node> nodes);

    /**
     * Discovers and adds the dependencies for the tasks that have been added to this graph.
     */
    void discoverDependencies();

    /**
     * Does the work to populate the task graph based on tasks that have been added. Fires events and no further tasks should be added.
     */
    void populate();

    /**
     * Executes the tasks in this graph. Discards the contents of this graph when completed.
     *
     * @param taskFailures collection to collect task execution failures into. Does not need to be thread-safe
     */
    void execute(Collection<? super Throwable> taskFailures);

    /**
     * Sets whether execution should continue if a task fails.
     */
    void setContinueOnFailure(boolean continueOnFailure);

    /**
     * Set of requested tasks.
     */
    Set<Task> getRequestedTasks();

    /**
     * Set of requested tasks.
     */
    Set<Task> getFilteredTasks();

    /**
     * Returns the number of work items in this graph.
     */
    int size();

    /**
     * Returns all of the work items in this graph scheduled for execution.
     */
    List<Node> getScheduledWork();

    /**
     * Returns all of the work items in this graph scheduled for execution plus all
     * dependencies from other builds.
     */
    List<Node> getScheduledWorkPlusDependencies();
}
