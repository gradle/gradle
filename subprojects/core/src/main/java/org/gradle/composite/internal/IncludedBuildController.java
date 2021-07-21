/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.composite.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;

import java.util.concurrent.ExecutorService;

public interface IncludedBuildController {
    /**
     * Locates a task node in this build's work graph for use in another build's work graph.
     * Does not schedule the task for execution, use {@link #queueForExecution(ExportedTaskNode)} to queue the task for execution.
     */
    ExportedTaskNode locateTask(TaskInternal task);

    /**
     * Locates a task node in this build's work graph for use in another build's work graph.
     * Does not schedule the task for execution, use {@link #queueForExecution(ExportedTaskNode)} to queue the task for execution.
     */
    ExportedTaskNode locateTask(String taskPath);

    /**
     * Schedules any queued tasks. When this method returns true, then some tasks where scheduled for this build and
     * this method should be called for all other builds in the tree as they may now have queued tasks.
     *
     * @return true if any tasks were scheduled, false if not.
     */
    boolean populateTaskGraph();

    /**
     * Prepares the work graph, once all tasks have been scheduled.
     */
    void prepareForExecution();

    /**
     * Must call {@link #populateTaskGraph()} prior to calling this method.
     */
    void startTaskExecution(ExecutorService executorService);

    /**
     * Awaits completion of task execution, collecting any task failures into the given collection.
     */
    ExecutionResult<Void> awaitTaskCompletion();

    void queueForExecution(ExportedTaskNode taskNode);
}
