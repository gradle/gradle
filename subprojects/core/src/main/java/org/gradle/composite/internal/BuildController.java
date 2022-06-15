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

import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface BuildController extends Stoppable {
    /**
     * Adds tasks and nodes to the work graph of this build.
     */
    void populateWorkGraph(Consumer<? super BuildLifecycleController.WorkGraphBuilder> action);

    /**
     * Queues the given task for execution. Does not schedule the task, use {@link #scheduleQueuedTasks()} for this.
     */
    void queueForExecution(ExportedTaskNode taskNode);

    /**
     * Schedules any queued tasks. When this method returns true, then some tasks where scheduled for this build and
     * this method should be called for all other builds in the tree as they may now have queued tasks.
     *
     * @return true if any tasks were scheduled, false if not.
     */
    boolean scheduleQueuedTasks();

    /**
     * Prepares the work graph, once all tasks have been scheduled.
     */
    void finalizeWorkGraph();

    /**
     * Must call {@link #scheduleQueuedTasks()} and {@link #finalizeWorkGraph()} prior to calling this method.
     */
    void startExecution(ExecutorService executorService, Consumer<ExecutionResult<Void>> completionHandler);
}
