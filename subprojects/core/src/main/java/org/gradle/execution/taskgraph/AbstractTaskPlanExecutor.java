/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;

import static org.gradle.internal.time.Clock.prettyTime;

abstract class AbstractTaskPlanExecutor implements TaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(AbstractTaskPlanExecutor.class);

    protected Runnable taskWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, BuildOperationWorkerRegistry buildOperationWorkerRegistry) {
        return new TaskExecutorWorker(taskExecutionPlan, taskWorker, buildOperationWorkerRegistry);
    }

    private static class TaskExecutorWorker implements Runnable {
        private final TaskExecutionPlan taskExecutionPlan;
        private final Action<? super TaskInternal> taskWorker;
        private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, BuildOperationWorkerRegistry buildOperationWorkerRegistry) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskWorker = taskWorker;
            this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        }

        public void run() {
            long busy = 0;
            Timer totalTimer = Timers.startTimer();
            Timer taskTimer = Timers.startTimer();
            TaskInfo task;
            while ((task = taskExecutionPlan.getTaskToExecute()) != null) {
                BuildOperationWorkerRegistry.Completion completion = buildOperationWorkerRegistry.operationStart();
                try {
                    final String taskPath = task.getTask().getPath();
                    LOGGER.info("{} ({}) started.", taskPath, Thread.currentThread());
                    taskTimer.reset();
                    processTask(task);
                    long taskDuration = taskTimer.getElapsedMillis();
                    busy += taskDuration;
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("{} ({}) completed. Took {}.", taskPath, Thread.currentThread(), prettyTime(taskDuration));
                    }
                } finally {
                    completion.operationFinish();
                }
            }
            long total = totalTimer.getElapsedMillis();
            //TODO SF it would be nice to print one-line statement that concludes the utilisation of the worker threads
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), prettyTime(busy), prettyTime(total - busy));
            }
        }

        protected void processTask(TaskInfo taskInfo) {
            try {
                taskWorker.execute(taskInfo.getTask());
            } catch (Throwable e) {
                taskInfo.setExecutionFailure(e);
            } finally {
                taskExecutionPlan.taskComplete(taskInfo);
            }
        }
    }
}
