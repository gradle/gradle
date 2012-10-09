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

import java.util.Map;
import java.util.Set;

public class TaskSelector {
    private final TaskNameResolver taskNameResolver;
    private GradleInternal gradle;

    public TaskSelector() {
        this(new TaskNameResolver());
    }

    public TaskSelector(TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
    }

    public TaskSelection getSelection(String path) {
        SetMultimap<String, Task> tasksByName;
        String baseName;
        String prefix;

        assert gradle != null : "selector should have been earlier initialized with Gradle instance";
        ProjectInternal project = gradle.getDefaultProject();

        if (path.contains(Project.PATH_SEPARATOR)) {
            String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
            projectPath = projectPath.length() == 0 ? Project.PATH_SEPARATOR : projectPath;
            project = findProject(project, projectPath);
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

    private static ProjectInternal findProject(ProjectInternal startFrom, String path) {
        if (path.equals(Project.PATH_SEPARATOR)) {
            return startFrom.getRootProject();
        }
        Project current = startFrom;
        if (path.startsWith(Project.PATH_SEPARATOR)) {
            current = current.getRootProject();
            path = path.substring(1);
        }
        for (String pattern : path.split(Project.PATH_SEPARATOR)) {
            Map<String, Project> children = current.getChildProjects();

            NameMatcher matcher = new NameMatcher();
            Project child = matcher.find(pattern, children);
            if (child != null) {
                current = child;
                continue;
            }

            throw new TaskSelectionException(matcher.formatErrorMessage("project", current));
        }

        return (ProjectInternal) current;
    }

    public void init(GradleInternal gradle) {
        this.gradle = gradle;
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
