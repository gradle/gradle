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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskContainer;

import java.util.*;

public class TaskNameResolver {
    /**
     * Finds tasks that will have exactly the given name, without necessarily creating or configuring the tasks. Returns null if no such match found.
     */
    @Nullable
    public TaskSelectionResult selectWithName(String name, Project project, boolean includeSubProjects) {
        if (!includeSubProjects) {
            TaskInternal task = (TaskInternal) project.getTasks().findByName(name);
            if (task != null) {
                return new FixedTaskSelectionResult(Collections.<Task>singleton(task));
            }
        } else {
            LinkedHashSet<Task> tasks = new LinkedHashSet<Task>();
            new MultiProjectTaskSelectionResult(name, project).collectTasks(tasks);
            if (!tasks.isEmpty()) {
                return new FixedTaskSelectionResult(tasks);
            }
        }

        return null;
    }

    /**
     * Finds the names of all tasks, without necessarily creating or configuring the tasks. Returns an empty map when none are found.
     */
    public Map<String, TaskSelectionResult> selectAll(Project project, boolean includeSubProjects) {
        Map<String, TaskSelectionResult> selected = new LinkedHashMap<String, TaskSelectionResult>();

        if (!includeSubProjects) {
            for (String taskName : project.getTasks().getNames()) {
                selected.put(taskName, new SingleProjectTaskSelectionResult(taskName, project.getTasks()));
            }
        } else {
            LinkedHashSet<String> taskNames = new LinkedHashSet<String>();
            collectTaskNames(project, taskNames);
            for (String taskName : taskNames) {
                selected.put(taskName, new MultiProjectTaskSelectionResult(taskName, project));
            }
        }

        return selected;
    }

    private void collectTaskNames(Project project, Set<String> result) {
        result.addAll(project.getTasks().getNames());
        for (Project subProject : project.getChildProjects().values()) {
            collectTaskNames(subProject, result);
        }
    }

    private static class FixedTaskSelectionResult implements TaskSelectionResult {
        private final Collection<Task> tasks;

        FixedTaskSelectionResult(Collection<Task> tasks) {
            this.tasks = tasks;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            tasks.addAll(this.tasks);
        }
    }

    private static class SingleProjectTaskSelectionResult implements TaskSelectionResult {
        private final TaskContainer taskContainer;
        private final String taskName;

        SingleProjectTaskSelectionResult(String taskName, TaskContainer tasksContainer) {
            this.taskContainer = tasksContainer;
            this.taskName = taskName;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            tasks.add(taskContainer.getByName(taskName));
        }
    }

    private static class MultiProjectTaskSelectionResult implements TaskSelectionResult {
        private final Project project;
        private final String taskName;

        MultiProjectTaskSelectionResult(String taskName, Project project) {
            this.project = project;
            this.taskName = taskName;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            collect(project, tasks);
        }

        private void collect(Project project, Collection<? super Task> tasks) {
            TaskInternal task = (TaskInternal) project.getTasks().findByName(taskName);
            if (task != null) {
                tasks.add(task);
                if (task.getImpliesSubProjects()) {
                    return;
                }
            }
            for (Project subProject : project.getChildProjects().values()) {
                collect(subProject, tasks);
            }
        }
    }
}
