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

import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import static org.gradle.util.Clock.prettyTime;

abstract class AbstractTaskPlanExecutor implements TaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(AbstractTaskPlanExecutor.class);

    protected Runnable taskWorker(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
        return new TaskExecutorWorker(taskExecutionPlan, taskListener);
    }

    private class TaskExecutorWorker implements Runnable {
        private final TaskExecutionPlan taskExecutionPlan;
        private final TaskExecutionListener taskListener;
        private long busyMs;

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskListener = taskListener;
        }

        public void run() {
            long start = System.currentTimeMillis();
            TaskInfo task;
            while ((task = taskExecutionPlan.getTaskToExecute()) != null) {
                executeTaskWithCacheLock(task);
            }
            long total = System.currentTimeMillis() - start;
            //TODO SF it would be nice to print one-line statement that concludes the utilisation of the worker threads
            LOGGER.debug("Task worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), prettyTime(busyMs), prettyTime(total - busyMs));
        }

        private void executeTaskWithCacheLock(final TaskInfo taskInfo) {
            final String taskPath = taskInfo.getTask().getPath();
            LOGGER.info(taskPath + " (" + Thread.currentThread() + " - start");
            final long start = System.currentTimeMillis();
            processTask(taskInfo);
            busyMs += System.currentTimeMillis() - start;

            LOGGER.info(taskPath + " (" + Thread.currentThread() + ") - complete");
        }

        protected void processTask(TaskInfo taskInfo) {
            try {
                executeTask(taskInfo);
            } catch (Throwable e) {
                taskInfo.setExecutionFailure(e);
            } finally {
                taskExecutionPlan.taskComplete(taskInfo);
            }
        }

        // TODO:PARALLEL It would be good to move this logic into a TaskExecuter wrapper, but we'd need a way to give it a TaskExecutionListener that
        // is wired to the various add/remove listener methods on TaskExecutionGraph
        private void executeTask(TaskInfo taskInfo) {
            TaskInternal task = taskInfo.getTask();
            taskListener.beforeExecute(task);
            try {
                task.executeWithoutThrowingTaskFailure();
            } finally {
                taskListener.afterExecute(task, task.getState());
            }
        }
    }
}
