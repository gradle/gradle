/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.Task;

import java.util.LinkedHashSet;
import java.util.Set;

public class TaskSelection {
    private final String projectPath;
    private final String taskName;
    private final TaskSelectionResult taskSelectionResult;

    public TaskSelection(String projectPath, String taskName, TaskSelectionResult tasks) {
        this.projectPath = projectPath;
        this.taskName = taskName;
        this.taskSelectionResult = tasks;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getTaskName() {
        return taskName;
    }

    public Set<Task> getTasks() {
        LinkedHashSet<Task> result = new LinkedHashSet<Task>();
        taskSelectionResult.collectTasks(result);
        return result;
    }
}
