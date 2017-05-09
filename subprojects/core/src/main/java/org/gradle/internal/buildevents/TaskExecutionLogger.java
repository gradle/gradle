/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.buildevents;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.progress.LoggerProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * A listener which logs the execution of tasks.
 */
public class TaskExecutionLogger implements TaskExecutionListener {

    private final Map<Task, ProgressLogger> currentTasks = new HashMap<Task, ProgressLogger>();
    private final ProgressLoggerFactory progressLoggerFactory;
    private LoggerProvider parentLoggerProvider;

    public TaskExecutionLogger(ProgressLoggerFactory progressLoggerFactory, LoggerProvider parentLoggerProvider) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.parentLoggerProvider = parentLoggerProvider;
    }

    public void beforeExecute(Task task) {
        assert !currentTasks.containsKey(task);

        ProgressLogger currentTask = progressLoggerFactory.newOperation(TaskExecutionLogger.class, parentLoggerProvider.getLogger());
        String displayName = getDisplayName((TaskInternal) task);
        currentTask.setDescription("Execute ".concat(displayName));
        currentTask.setShortDescription(displayName);
        currentTask.setLoggingHeader(displayName);
        currentTask.started();
        currentTasks.put(task, currentTask);
    }

    public void afterExecute(Task task, TaskState state) {
        ProgressLogger currentTask = currentTasks.remove(task);
        String taskMessage = state.getFailure() != null ? "FAILED" : state.getSkipMessage();
        currentTask.completed(taskMessage);
    }

    private String getDisplayName(TaskInternal task) {
        return task.getIdentityPath().toString();
    }
}
