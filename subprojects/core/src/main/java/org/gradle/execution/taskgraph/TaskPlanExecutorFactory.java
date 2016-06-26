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

package org.gradle.execution.taskgraph;

import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {
    private final int parallelThreads;
    private final ExecutorFactory executorFactory;
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;

    public TaskPlanExecutorFactory(int parallelThreads, ExecutorFactory executorFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry) {
        this.parallelThreads = parallelThreads;
        this.executorFactory = executorFactory;
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
    }

    public TaskPlanExecutor create() {
        if (parallelThreads < 1) {
            throw new IllegalStateException(String.format("Cannot create executor for requested number of worker threads: %s.", parallelThreads));
        }
        if (parallelThreads > 1) {
            return new ParallelTaskPlanExecutor(parallelThreads, executorFactory, buildOperationWorkerRegistry);
        }
        return new DefaultTaskPlanExecutor(buildOperationWorkerRegistry);
    }
}
