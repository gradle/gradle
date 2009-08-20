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
import org.gradle.api.internal.BuildInternal;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * A {@link BuildExecuter} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildExecuter implements BuildExecuter {
    private final List<String> names;
    private final String description;
    private TaskExecuter executer;

    public TaskNameResolvingBuildExecuter(Collection<String> names) {
        this.names = new ArrayList<String>(names);
        if (names.size() == 1) {
            description = String.format("primary task %s", GUtil.toString(names));
        } else {
            description = String.format("primary tasks %s", GUtil.toString(names));
        }
    }

    public void select(BuildInternal build) {
        this.executer = build.getTaskGraph();
        for (Collection<Task> tasksForName : select(build, names)) {
            executer.addTasks(tasksForName);
        }
    }

    static List<Collection<Task>> select(BuildInternal build, Iterable<String> names) {
        Set<String> unknownTasks = new LinkedHashSet<String>();
        Project project = build.getDefaultProject();
        List<Collection<Task>> matches = new ArrayList<Collection<Task>>();
        for (String taskName : names) {
            Set<Task> tasksForName = findTasks(project, taskName);
            if (tasksForName.size() == 0) {
                unknownTasks.add(taskName);
            }
            else {
                matches.add(tasksForName);
            }
        }

        if (unknownTasks.size() == 0) {
            return matches;
        }
        if (unknownTasks.size() == 1) {
            throw new UnknownTaskException(String.format("Task %s not found in %s.", GUtil.toString(unknownTasks), project));
        } else {
            throw new UnknownTaskException(String.format("Tasks %s not found in %s.", GUtil.toString(unknownTasks), project));
        }
    }

    private static Set<Task> findTasks(Project project, String taskName) {
        if (!taskName.contains(Project.PATH_SEPARATOR)) {
            return project.getTasksByName(taskName, true);
        }

        Task task = project.getTasks().findByPath(taskName);
        if (task != null) {
            return Collections.singleton(task);
        } else {
            return Collections.emptySet();
        }
    }

    public String getDisplayName() {
        return description;
    }

    public void execute() {
        executer.execute();
    }
}
