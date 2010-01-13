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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
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

    public TaskNameResolvingBuildExecuter(Collection<String> names) {
        this.names = new ArrayList<String>(names);
    }

    public void select(GradleInternal gradle) {
        Map<String, Collection<Task>> selectedTasks = doSelect(gradle, names);

        this.executer = gradle.getTaskGraph();
        for (Collection<Task> tasksForName : selectedTasks.values()) {
            executer.addTasks(tasksForName);
        }
        if (selectedTasks.size() == 1) {
            description = String.format("primary task %s", GUtil.toString(selectedTasks.keySet()));
        } else {
            description = String.format("primary tasks %s", GUtil.toString(selectedTasks.keySet()));
        }
    }

    static List<Collection<Task>> select(GradleInternal gradle, Iterable<String> names) {
        return new ArrayList<Collection<Task>>(doSelect(gradle, names).values());
    }

    private static Map<String, Collection<Task>> doSelect(GradleInternal gradle, Iterable<String> paths) {

        Map<String, Collection<Task>> allProjectsTasksByName = null;

        Map<String, Collection<Task>> matches = new LinkedHashMap<String, Collection<Task>>();
        for (String path : paths) {
            Map<String, Collection<Task>> tasksByName;
            String baseName;
            String prefix;

            Project project = gradle.getDefaultProject();

            if (path.contains(Project.PATH_SEPARATOR)) {
                String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
                projectPath = projectPath.length() == 0 ? Project.PATH_SEPARATOR : projectPath;
                project = findProject(project, projectPath);
                baseName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
                Task match = project.getTasks().findByName(baseName);
                if (match != null) {
                    matches.put(path, Collections.singleton(match));
                    continue;
                }

                tasksByName = new HashMap<String, Collection<Task>>();
                for (Task task : project.getTasks().getAll()) {
                    tasksByName.put(task.getName(), Collections.singleton(task));
                }

                prefix = project.getPath() + Project.PATH_SEPARATOR;
            }
            else {
                Set<Task> tasks = project.getTasksByName(path, true);
                if (!tasks.isEmpty()) {
                    matches.put(path, tasks);
                    continue;
                }
                if (allProjectsTasksByName == null) {
                    allProjectsTasksByName = buildTaskMap(project);
                }
                tasksByName = allProjectsTasksByName;
                baseName = path;
                prefix = "";
            }

            NameMatcher matcher = new NameMatcher();
            String actualName = matcher.find(baseName, tasksByName.keySet());

            if (actualName != null) {
                matches.put(prefix + actualName, tasksByName.get(actualName));
                continue;
            }

            throw new TaskSelectionException(matcher.formatErrorMessage("task", project));
        }

        return matches;
    }

    private static Project findProject(Project startFrom, String path) {
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

        return current;
    }

    private static Map<String, Collection<Task>> buildTaskMap(Project defaultProject) {
        Map<String, Collection<Task>> tasksByName = new HashMap<String, Collection<Task>>();
        for (Project project : defaultProject.getAllprojects()) {
            for (Task task : project.getTasks().getAll()) {
                Collection<Task> tasksForName = tasksByName.get(task.getName());
                if (tasksForName == null) {
                    tasksForName = new HashSet<Task>();
                    tasksByName.put(task.getName(), tasksForName);
                }
                tasksForName.add(task);
            }
        }
        return tasksByName;
    }

    public String getDisplayName() {
        return description;
    }

    public void execute() {
        executer.execute();
    }
}
