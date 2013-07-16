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
package org.gradle.execution;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.taskpath.ResolvedTaskPath;
import org.gradle.execution.taskpath.TaskPathResolver;
import org.gradle.util.NameMatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TaskSelector {
    private final TaskNameResolver taskNameResolver;
    private final GradleInternal gradle;
    private final TaskPathResolver taskPathResolver = new TaskPathResolver();
    private final Function<TaskSelectionResult, Task> resolableTaskToTaskFunction;

    public TaskSelector(GradleInternal gradle) {
        this(gradle, new TaskNameResolver());

    }

    public TaskSelector(GradleInternal gradle, TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
        this.gradle = gradle;
        resolableTaskToTaskFunction = new Function<TaskSelectionResult, Task>() {
            public Task apply(TaskSelectionResult selectionResult) {
                return selectionResult.getTask();
            }
        };
    }

    public TaskSelection getSelection(String path) {
        SetMultimap<String, TaskSelectionResult> tasksByName;
        ProjectInternal project = gradle.getDefaultProject();
        ResolvedTaskPath taskPath = taskPathResolver.resolvePath(path, project);

        if (taskPath.isQualified()) {
            tasksByName = taskNameResolver.select(taskPath.getTaskName(), taskPath.getProject());
        } else {
            tasksByName = taskNameResolver.selectAll(path, project);
        }

        Set<TaskSelectionResult> tasks = tasksByName.get(taskPath.getTaskName());
        if (!tasks.isEmpty()) {
            // An exact match
            return new TaskSelection(path, new HashSet<Task>(Collections2.transform(tasks, resolableTaskToTaskFunction)));
        }

        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(taskPath.getTaskName(), tasksByName.keySet());
        if (actualName != null) {
            final Collection<Task> transform1 = Collections2.transform(tasksByName.get(actualName), resolableTaskToTaskFunction);
            return new TaskSelection(taskPath.getPrefix() + actualName, new HashSet<Task>(transform1));
        }

        throw new TaskSelectionException(matcher.formatErrorMessage("task", project));
    }

    public static class TaskSelection {
        private String taskName;
        private Set tasks;

        public TaskSelection(String taskName, Set<Task> tasks) {
            this.taskName = taskName;
            this.tasks = tasks;
        }

        public String getTaskName() {
            return taskName;
        }

        public Set<Task> getTasks() {
            return tasks;
        }
    }
}
