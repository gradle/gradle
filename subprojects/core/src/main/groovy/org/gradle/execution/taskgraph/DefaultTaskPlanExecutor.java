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
import org.gradle.api.internal.TaskInternal;

class DefaultTaskPlanExecutor implements TaskPlanExecutor {

    public void process(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
        TaskInfo taskInfo;
        while ((taskInfo = taskExecutionPlan.getTaskToExecute()) != null) {
            processTask(taskInfo, taskExecutionPlan, taskListener);
        }
        taskExecutionPlan.awaitCompletion();
    }

    protected void processTask(TaskInfo taskInfo, TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
        try {
            executeTask(taskInfo, taskListener);
        } catch (Throwable e) {
            taskInfo.setExecutionFailure(e);
        } finally {
            taskExecutionPlan.taskComplete(taskInfo);
        }
    }

    // TODO:PARALLEL It would be good to move this logic into a TaskExecuter wrapper, but we'd need a way to give it a TaskExecutionListener that
    // is wired to the various add/remove listener methods on TaskExecutionGraph
    private void executeTask(TaskInfo taskInfo, TaskExecutionListener taskListener) {
        TaskInternal task = taskInfo.getTask();
        taskListener.beforeExecute(task);
        try {
            task.executeWithoutThrowingTaskFailure();
        } finally {
            taskListener.afterExecute(task, task.getState());
        }
    }
}
