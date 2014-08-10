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

import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.taskpath.ResolvedTaskPath;
import org.gradle.execution.taskpath.TaskPathResolver;
import org.gradle.util.NameMatcher;

import java.util.LinkedHashSet;
import java.util.Map;
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
        return getSelection(path, gradle.getDefaultProject());
    }

    public TaskSelection getSelection(@Nullable String projectPath, String path) {
        ProjectInternal project = projectPath != null
                ? gradle.getRootProject().findProject(projectPath)
                : gradle.getDefaultProject();
        return getSelection(path, project);
    }

    private TaskSelection getSelection(String path, ProjectInternal project) {
        ResolvedTaskPath taskPath = taskPathResolver.resolvePath(path, project);

        TaskSelectionResult tasks = taskNameResolver.selectWithName(taskPath.getTaskName(), taskPath.getProject(), !taskPath.isQualified());
        if (tasks != null) {
            // An exact match
            return new TaskSelection(taskPath.getProject().getPath(), path, tasks);
        }

        Map<String, TaskSelectionResult> tasksByName = taskNameResolver.selectAll(taskPath.getProject(), !taskPath.isQualified());
        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(taskPath.getTaskName(), tasksByName.keySet());
        if (actualName != null) {
            return new TaskSelection(taskPath.getProject().getPath(), taskPath.getPrefix() + actualName, tasksByName.get(actualName));
        }

        throw new TaskSelectionException(matcher.formatErrorMessage("task", taskPath.getProject()));
    }

    public static class TaskSelection {
        private final String projectPath;
        private final String taskName;
        private final TaskSelectionResult taskSelectionResult;

        public TaskSelection(String projectPath, String taskName, TaskSelectionResult tasks) {
            this.projectPath = projectPath;
            this.taskName = taskName;
            taskSelectionResult = tasks;
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
}
