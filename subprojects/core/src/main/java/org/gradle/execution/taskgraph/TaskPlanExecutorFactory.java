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

import com.google.common.collect.Lists;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.List;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {
    private final ParallelismConfigurationManager parallelismConfigurationManager;
    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final List<TaskPlanExecutor> taskPlanExecutors = Lists.newArrayList();

    public TaskPlanExecutorFactory(ParallelismConfigurationManager parallelismConfigurationManager, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService) {
        this.parallelismConfigurationManager = parallelismConfigurationManager;
        this.executorFactory = executorFactory;
        this.workerLeaseService = workerLeaseService;
    }

    public TaskPlanExecutor create() {
        int parallelThreads = parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount();
        if (parallelThreads < 1) {
            throw new IllegalStateException(String.format("Cannot create executor for requested number of worker threads: %s.", parallelThreads));
        }

        // TODO: Make task plan executor respond to changes in parallelism configuration
        TaskPlanExecutor taskPlanExecutor = new DefaultTaskPlanExecutor(parallelismConfigurationManager.getParallelismConfiguration(), executorFactory, workerLeaseService);
        taskPlanExecutors.add(taskPlanExecutor);
        return taskPlanExecutor;
    }
}
