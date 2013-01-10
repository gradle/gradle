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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.NameMatcher;

import java.util.Set;

public class TaskSelector {
    private final TaskNameResolver taskNameResolver;
    private final GradleInternal gradle;
    private final ProjectFinderByTaskPath projectFinder = new ProjectFinderByTaskPath();

    public TaskSelector(GradleInternal gradle) {
        this(gradle, new TaskNameResolver());
    }

    public TaskSelector(GradleInternal gradle, TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
        this.gradle = gradle;
    }

    public TaskSelection getSelection(String path) {
        SetMultimap<String, Task> tasksByName;
        String baseName;
        String prefix;

        ProjectInternal project = gradle.getDefaultProject();

        if (path.contains(Project.PATH_SEPARATOR)) {
            project = projectFinder.findProject(path, project);
            baseName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
            prefix = project.getPath() + Project.PATH_SEPARATOR;

            tasksByName = taskNameResolver.select(baseName, project);
        } else {
            baseName = path;
            prefix = "";

            tasksByName = taskNameResolver.selectAll(path, project);
        }

        Set<Task> tasks = tasksByName.get(baseName);
        if (!tasks.isEmpty()) {
            // An exact match
            return new TaskSelection(path, tasks);
        }

        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(baseName, tasksByName.keySet());

        if (actualName != null) {
            // A partial match
            return new TaskSelection(prefix + actualName, tasksByName.get(actualName));
        }

        throw new TaskSelectionException(matcher.formatErrorMessage("task", project));
    }

    public static class TaskSelection {
        private String taskName;
        private Set<Task> tasks;

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
