/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;

import java.util.*;

/**
 * A {@link TaskSelector} which selects tasks which match the provided names. For each name, selects all tasks in
 * all projects whose name is the given name.
 */
public class NameResolvingTaskSelector implements TaskSelector {
    private final Iterable<String> names;
    private Iterator<String> current;
    private Set<Task> tasks;
    private String description;

    public NameResolvingTaskSelector(Iterable<String> names) {
        this.names = names;
    }

    public boolean hasNext() {
        return current == null || current.hasNext();
    }

    public void select(Project project) {
        if (current == null) {
            // First group, check for unknown tasks
            current = names.iterator();
            checkForUnknownTasks(project);
        }

        if (!current.hasNext()) {
            tasks = Collections.emptySet();
            description = "";
            return;
        }

        String taskName = current.next();
        description = String.format("primary task '%s'", taskName);
        tasks = findTasks(project, taskName);
    }

    private void checkForUnknownTasks(Project project) {
        Set<String> unknownTasks = new LinkedHashSet<String>();
        for (String taskName : names) {
            if (findTasks(project, taskName).size() == 0) {
                unknownTasks.add(String.format("'%s'", taskName));
            }
        }
        if (unknownTasks.size() == 0) {
            return;
        }
        if (unknownTasks.size() == 1) {
            throw new UnknownTaskException(String.format("Task %s not found in this project.", unknownTasks.iterator().next()));
        } else {
            throw new UnknownTaskException(String.format("Tasks %s not found in this project.", unknownTasks));
        }
    }

    private Set<Task> findTasks(Project project, String taskName) {
        if (taskName.contains(Project.PATH_SEPARATOR)) {
            taskName = project.absolutePath(taskName);
            // todo Refactor: first find the project than the task.
            Map<Project, Set<Task>> allTasks = project.getRootProject().getAllTasks(true);
            for (Collection<Task> projectTasks : allTasks.values()) {
                for (Task task : projectTasks) {
                    if (task.getPath().equals(taskName)) {
                        return Collections.singleton(task);
                    }
                }
            }
            return Collections.emptySet();
        } else {
            return project.getTasksByName(taskName, true);
        }
    }

    public Iterable<Task> getTasks() {
        return tasks;
    }

    public String getDescription() {
        return description;
    }
}
