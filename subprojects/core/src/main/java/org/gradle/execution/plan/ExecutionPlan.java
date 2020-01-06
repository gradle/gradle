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

package org.gradle.execution.plan;

import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.work.WorkerLeaseRegistry;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * Represents a graph of dependent work items, returned in execution order.
 */
public interface ExecutionPlan extends Describable {
    /**
     * Selects a work item to run, returns null if there is no work remaining _or_ if no queued work is ready to run.
     */
    @Nullable
    Node selectNext(WorkerLeaseRegistry.WorkerLease workerLease, ResourceLockState resourceLockState);

    void finishedExecuting(Node node);

    void abortAllAndFail(Throwable t);

    void cancelExecution();

    /**
     * Returns the node for the supplied task that is part of this execution plan.
     *
     * @throws IllegalStateException When no node for the supplied task is part of this execution plan.
     */
    TaskNode getNode(Task task);

    /**
     * @return The set of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    Set<Task> getTasks();

    /**
     * @return The set of all filtered tasks that don't get executed.
     */
    Set<Task> getFilteredTasks();

    /**
     * Collects the current set of task failures into the given collection.
     */
    void collectFailures(Collection<? super Throwable> failures);

    boolean allNodesComplete();

    boolean hasNodesRemaining();

    /**
     * Returns the number of work items in the plan.
     */
    int size();
}
