/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.TaskInternal;

import java.util.Collection;

public interface BuildWorkGraph {
    /**
     * Locates a future task node in this build's work graph, for use from some other build's work graph.
     *
     * <p>This method does not schedule the task for execution, use {@link #schedule(Collection)} to schedule the task.
     */
    ExportedTaskNode locateTask(TaskInternal task);

    /**
     * Locates a future task node in this build's work graph, for use from some other build's work graph.
     *
     * <p>This method does not schedule the task for execution, use {@link #schedule(Collection)} to schedule the task.
     */
    ExportedTaskNode locateTask(String taskPath);

    /**
     * Schedules the given tasks and all of their dependencies in this build's work graph.
     */
    void schedule(Collection<ExportedTaskNode> taskNodes);

    /**
     * Finalize the work graph for execution, after all work has been scheduled. This method should not schedule any additional work.
     */
    void prepareForExecution(boolean alwaysPopulateWorkGraph);

    /**
     * Runs all currently scheduled tasks.
     */
    ExecutionResult<Void> execute();
}
