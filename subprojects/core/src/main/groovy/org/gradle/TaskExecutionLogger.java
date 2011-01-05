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
import org.gradle.logging.ProgressLogger;
import org.gradle.api.tasks.TaskState;
import org.gradle.logging.ProgressLoggerFactory;

/**
 * A listener which logs the execution of tasks.
 */
public class TaskExecutionLogger implements TaskExecutionListener {
    private ProgressLogger currentTask;
    private final ProgressLoggerFactory progressLoggerFactory;

    public TaskExecutionLogger(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public void beforeExecute(Task task) {
        assert currentTask == null;
        currentTask = progressLoggerFactory.start(TaskExecutionLogger.class.getName(), getDisplayName(task));
        currentTask.progress(getDisplayName(task));
    }

    public void afterExecute(Task task, TaskState state) {
        if (state.getSkipMessage() != null) {
            currentTask.completed(state.getSkipMessage());
        } else {
            currentTask.completed();
        }
        currentTask = null;
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
