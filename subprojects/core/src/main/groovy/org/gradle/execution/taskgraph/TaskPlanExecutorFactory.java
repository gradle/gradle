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
import org.gradle.internal.progress.BuildOperationExecutor;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {
    private final int parallelThreads;
    private final ExecutorFactory executorFactory;
    private final BuildOperationExecutor buildOperationExecutor;

    public TaskPlanExecutorFactory(int parallelThreads, ExecutorFactory executorFactory, BuildOperationExecutor buildOperationExecutor) {
        this.parallelThreads = parallelThreads;
        this.executorFactory = executorFactory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public TaskPlanExecutor create() {
        if (executeProjectsInParallel()) {
            return new ParallelTaskPlanExecutor(numberOfParallelThreads(), executorFactory, buildOperationExecutor);
        }
        return new DefaultTaskPlanExecutor(buildOperationExecutor);
    }

    private boolean executeProjectsInParallel() {
        return parallelThreads != 0;
    }

    private int numberOfParallelThreads() {
        if (parallelThreads == -1) {
            return Runtime.getRuntime().availableProcessors();
        }
        return parallelThreads;
    }
}
