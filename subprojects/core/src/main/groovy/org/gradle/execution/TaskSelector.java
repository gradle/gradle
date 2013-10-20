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

import com.google.common.collect.SetMultimap;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.taskpath.ResolvedTaskPath;
import org.gradle.execution.taskpath.TaskPathResolver;
import org.gradle.util.CollectionUtils;
import org.gradle.util.NameMatcher;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class TaskSelector {
    private final TaskNameResolver taskNameResolver;
    private final GradleInternal gradle;
    private final TaskPathResolver taskPathResolver = new TaskPathResolver();

    public TaskSelector(GradleInternal gradle) {
        this(gradle, new TaskNameResolver());
    }

    public TaskSelector(GradleInternal gradle, TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
        this.gradle = gradle;
    }

    public TaskSelection getSelection(String path) {
        SetMultimap<String, TaskSelectionResult> tasksByName;
        ProjectInternal project = gradle.getDefaultProject();
        ResolvedTaskPath taskPath = taskPathResolver.resolvePath(path, project);

        if (taskPath.isQualified()) {
            tasksByName = taskNameResolver.select(taskPath.getTaskName(), taskPath.getProject());
        } else {
            tasksByName = taskNameResolver.selectAll(taskPath.getTaskName(), taskPath.getProject());
        }

        Set<TaskSelectionResult> tasks = tasksByName.get(taskPath.getTaskName());
        if (!tasks.isEmpty()) {
            // An exact match
            return new TaskSelection(path, tasks);
        }

        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(taskPath.getTaskName(), tasksByName.keySet());
        if (actualName != null) {
            return new TaskSelection(taskPath.getPrefix() + actualName, tasksByName.get(actualName));
        }

        throw new TaskSelectionException(matcher.formatErrorMessage("task", taskPath.getProject()));
    }

    public static class TaskSelection {
        private String taskName;
        private Collection<TaskSelectionResult> taskSelectionResult;
        public TaskSelection(String taskName, Set<TaskSelectionResult> tasks) {
            this.taskName = taskName;
            taskSelectionResult = tasks;
        }

        public String getTaskName() {
            return taskName;
        }

        public Set<Task> getTasks() {
            return CollectionUtils.collect(taskSelectionResult, new LinkedHashSet<Task>(), new Transformer<Task, TaskSelectionResult>() {
                public Task transform(TaskSelectionResult original) {
                    return original.getTask();
                }
            });
        }
    }
}
