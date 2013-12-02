/*
 * Copyright 2010 the original author or authors.
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

package org.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.progress.LoggerProvider;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A listener which logs the execution of tasks.
 */
public class TaskExecutionLogger implements TaskExecutionListener {

    private final Map<Task, ProgressLogger> currentTasks = new HashMap<Task, ProgressLogger>();
    private final ProgressLoggerFactory progressLoggerFactory;
    private LoggerProvider parentLoggerPovider;

    public TaskExecutionLogger(ProgressLoggerFactory progressLoggerFactory, LoggerProvider parentLoggerPovider) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.parentLoggerPovider = parentLoggerPovider;
    }

    public void beforeExecute(Task task) {
        assert !currentTasks.containsKey(task);

        ProgressLogger currentTask = progressLoggerFactory.newOperation(TaskExecutionLogger.class, parentLoggerPovider.getLogger());
        String displayName = getDisplayName(task);
        currentTask.setDescription(String.format("Execute %s", displayName));
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

    private String getDisplayName(Task task) {
        Gradle build = task.getProject().getGradle();
        if (build.getParent() == null) {
            // The main build, use the task path
            return task.getPath();
        }
        // A nested build, use a discriminator
        return Project.PATH_SEPARATOR + build.getRootProject().getName() + task.getPath();
    }
}
