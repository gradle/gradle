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

import static org.gradle.util.Clock.prettyTime;

abstract class AbstractTaskPlanExecutor implements TaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(AbstractTaskPlanExecutor.class);

    protected Runnable taskWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker) {
        return new TaskExecutorWorker(taskExecutionPlan, taskWorker);
    }

    private static class TaskExecutorWorker implements Runnable {
        private final TaskExecutionPlan taskExecutionPlan;
        private final Action<? super TaskInternal> taskWorker;

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskWorker = taskWorker;
        }

        public void run() {
            long busy = 0;
            long start = System.currentTimeMillis();
            TaskInfo task;
            while ((task = taskExecutionPlan.getTaskToExecute()) != null) {
                final String taskPath = task.getTask().getPath();
                LOGGER.info("{} ({}) started.", taskPath, Thread.currentThread());
                long startTask = System.currentTimeMillis();
                processTask(task);
                long taskDuration = System.currentTimeMillis() - startTask;
                busy += taskDuration;
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("{} ({}) completed. Took {}.", taskPath, Thread.currentThread(), prettyTime(taskDuration));
                }
            }
            long total = System.currentTimeMillis() - start;
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
