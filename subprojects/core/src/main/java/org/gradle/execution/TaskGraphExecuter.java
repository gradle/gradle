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
package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.specs.Spec;

import java.util.Set;

public interface TaskGraphExecuter extends TaskExecutionGraph {
    /**
     * Sets the filter to use when adding tasks to this graph. Only those tasks which are accepted by the given filter
     * will be added to this graph.
     */
    void useFilter(Spec<? super Task> filter);

    /**
     * Adds the given tasks and their dependencies to this graph. Tasks are executed in an arbitrary order. The tasks
     * are executed before any tasks from a subsequent call to this method are executed.
     */
    void addTasks(Iterable<? extends Task> tasks);

    /**
     * Does the work to populate the task graph based on tasks that have been added. Does not fire events.
     */
    void populate();

    /**
     * Executes the tasks in this graph. Discards the contents of this graph when completed.
     */
    void execute();

    /**
     * Sets the handler to use when a task fails.
     */
    void useFailureHandler(TaskFailureHandler handler);

    /**
     * Set of requested tasks.
     */
    Set<Task> getRequestedTasks();


    /**
     * Set of requested tasks.
     */
    Set<Task> getFilteredTasks();
}
