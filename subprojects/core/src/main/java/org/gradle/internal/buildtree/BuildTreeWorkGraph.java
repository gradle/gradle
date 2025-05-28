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

package org.gradle.internal.buildtree;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.ExecutionResult;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a set of work to be executed across a build tree.
 */
public interface BuildTreeWorkGraph {
    /**
     * Schedules work using the given action and then prepares this work graphs for execution. Does not run any work until {@link FinalizedGraph#runWork()} is called.
     *
     * <p>This can be called only once for a given graph.</p>
     */
    FinalizedGraph scheduleWork(Consumer<? super Builder> action);

    interface FinalizedGraph {
        /**
         * Runs any scheduled work, blocking until complete. Does nothing when {@link #scheduleWork(Consumer)} has not been called to schedule the work.
         *
         * <p>This can be called only once for a given graph.</p>
         */
        ExecutionResult<Void> runWork();
    }

    interface Builder {
        /**
         * Adds nodes to the work graph for the given build.
         */
        void withWorkGraph(BuildState target, Consumer<? super BuildLifecycleController.WorkGraphBuilder> action);

        /**
         * Adds the given tasks and their dependencies to the work graph.
         */
        void scheduleTasks(Collection<TaskIdentifier.TaskBasedTaskIdentifier> tasksToBuild);

        /**
         * Adds add task filter to the given build.
         */
        void addFilter(BuildState target, Spec<Task> filter);

        /**
         * Adds a {@link ExecutionPlan} finalization step to the given build.
         */
        void addFinalization(BuildState target, BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization);
    }
}
