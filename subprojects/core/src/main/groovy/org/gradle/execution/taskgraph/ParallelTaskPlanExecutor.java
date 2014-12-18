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

import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.util.concurrent.Executor;

class ParallelTaskPlanExecutor extends AbstractTaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(ParallelTaskPlanExecutor.class);
    private final int executorCount;
    private final ExecutorFactory executorFactory;

    public ParallelTaskPlanExecutor(int numberOfParallelExecutors, ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }

        this.executorCount = numberOfParallelExecutors;
    }

    public void process(final TaskExecutionPlan taskExecutionPlan, final TaskExecutionListener taskListener) {
        StoppableExecutor executor = executorFactory.create("Task worker");
        try {
            startAdditionalWorkers(taskExecutionPlan, taskListener, executor);
            taskWorker(taskExecutionPlan, taskListener).run();
            taskExecutionPlan.awaitCompletion();
        } finally {
            executor.stop();
        }
    }

    private void startAdditionalWorkers(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener, Executor executor) {
        LOGGER.info("Using {} parallel executor threads", executorCount);

        for (int i = 1; i < executorCount; i++) {
            Runnable worker = taskWorker(taskExecutionPlan, taskListener);
            executor.execute(worker);
        }
    }
}
