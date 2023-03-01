/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.execution.TaskSelectionException;
import org.gradle.execution.selection.BuildTaskSelector;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A configuration-friendly view of a task selection.
 */
class TaskDetailsModel {
    private final String taskPath;
    private final List<TaskDetails> tasks;
    private final TaskSelectionException failure;

    private TaskDetailsModel(String taskPath, List<TaskDetails> tasks) {
        this.taskPath = taskPath;
        this.failure = null;
        this.tasks = tasks;
    }

    private TaskDetailsModel(String taskPath, TaskSelectionException failure) {
        this.taskPath = taskPath;
        this.tasks = Collections.emptyList();
        this.failure = failure;
    }

    public List<TaskDetails> getTasks() {
        if (failure != null) {
            // rethrow the original failure
            throw failure;
        }
        return tasks;
    }

    public String getTaskPath() {
        return taskPath;
    }

    public static TaskDetailsModel from(String taskPath, BuildTaskSelector.BuildSpecificSelector taskSelector, OptionReader optionReader) {
        try {
            Stream<Task> selectedTasks = taskSelector.resolveTaskName(taskPath).getTasks().stream();
            List<TaskDetails> tasks = selectedTasks.map(t -> TaskDetails.from(t, optionReader))
                .sorted(TaskDetails.DEFAULT_COMPARATOR).collect(Collectors.toList());
            return new TaskDetailsModel(taskPath, tasks);
        } catch (TaskSelectionException exception) {
            // collect exception so we can rethrow it during task execution
            return new TaskDetailsModel(taskPath, exception);
        }
    }
}
