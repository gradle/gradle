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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GUtil;
import org.gradle.util.NameMatcher;

import java.util.*;

/**
 * A {@link BuildExecuter} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildExecuter implements BuildExecuter {
    private final List<String> names;
    private String description;
    private TaskGraphExecuter executer;
    private final TaskNameResolver taskNameResolver;

    public TaskNameResolvingBuildExecuter(Collection<String> names) {
        this(names, new TaskNameResolver());
    }

    TaskNameResolvingBuildExecuter(Collection<String> names, TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
        this.names = new ArrayList<String>(names);
    }

    public List<String> getNames() {
        return names;
    }

    public void select(GradleInternal gradle) {
        SetMultimap<String, Task> selectedTasks = doSelect(gradle, names, taskNameResolver);

        this.executer = gradle.getTaskGraph();
        for (String name : selectedTasks.keySet()) {
            executer.addTasks(selectedTasks.get(name));
        }
        if (selectedTasks.keySet().size() == 1) {
            description = String.format("primary task %s", GUtil.toString(selectedTasks.keySet()));
        } else {
            description = String.format("primary tasks %s", GUtil.toString(selectedTasks.keySet()));
        }
    }

    static Set<Task> select(GradleInternal gradle, Iterable<String> names) {
        return new LinkedHashSet<Task>(doSelect(gradle, names, new TaskNameResolver()).values());
    }

    private static SetMultimap<String, Task> doSelect(GradleInternal gradle, Iterable<String> paths, TaskNameResolver taskNameResolver) {

        SetMultimap<String, Task> matches = LinkedHashMultimap.create();
        for (String path : paths) {
            Multimap<String, Task> tasksByName;
            String baseName;
            String prefix;

            ProjectInternal project = gradle.getDefaultProject();

            if (path.contains(Project.PATH_SEPARATOR)) {
                String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
                projectPath = projectPath.length() == 0 ? Project.PATH_SEPARATOR : projectPath;
                project = findProject(project, projectPath);
                baseName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
                prefix = project.getPath() + Project.PATH_SEPARATOR;

                tasksByName = taskNameResolver.select(baseName, project);
            }
            else {
                baseName = path;
                prefix = "";

                tasksByName = taskNameResolver.selectAll(path, project);
            }

            Collection<Task> tasks = tasksByName.get(baseName);
            if (!tasks.isEmpty()) {
                matches.putAll(path, tasks);
                continue;
            }

            NameMatcher matcher = new NameMatcher();
            String actualName = matcher.find(baseName, tasksByName.keySet());

            if (actualName != null) {
                matches.putAll(prefix + actualName, tasksByName.get(actualName));
                continue;
            }

            throw new TaskSelectionException(matcher.formatErrorMessage("task", project));
        }

        return matches;
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

    public String getDisplayName() {
        return description;
    }

    public void execute() {
        executer.execute();
    }
}
