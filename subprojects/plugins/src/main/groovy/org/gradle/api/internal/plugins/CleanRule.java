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

package org.gradle.api.internal.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;

public class CleanRule extends AbstractRule {

    public static final String PREFIX = "clean";

    private final TaskContainer tasks;

    public CleanRule(TaskContainer tasks) {
        this.tasks = tasks;
    }

    public String getDescription() {
        return String.format("Pattern: %s<TaskName>: Cleans the output files of a task.", PREFIX);
    }

    public void apply(String taskName) {
        if (!taskName.startsWith(PREFIX)) {
            return;
        }

        String targetTaskName = taskName.substring(PREFIX.length());
        if (Character.isLowerCase(targetTaskName.charAt(0))) {
            return;
        }

        Task task = tasks.findByName(StringUtils.uncapitalize(targetTaskName));
        if (task == null) {
            return;
        }

        Delete clean = tasks.create(taskName, Delete.class);
        clean.delete(task.getOutputs().getFiles());
    }
}
