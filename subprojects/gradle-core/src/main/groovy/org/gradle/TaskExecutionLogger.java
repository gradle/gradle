/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;

/**
 * A listener which logs the execution of tasks.
 */
public class TaskExecutionLogger implements TaskExecutionListener, TaskActionListener {
    private final Logger logger;

    public TaskExecutionLogger(Logger logger) {
        this.logger = logger;
    }

    public void beforeExecute(Task task) {
    }

    public void beforeActions(Task task) {
        logger.lifecycle(getDisplayName(task));
    }

    public void afterActions(Task task) {
    }

    public void afterExecute(Task task, TaskExecutionResult result) {
        if (result.getSkipMessage() != null) {
            logger.lifecycle("{} {}", getDisplayName(task), result.getSkipMessage());
        }
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
