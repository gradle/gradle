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

import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.tasks.TaskContainer;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TaskNameResolver {

    /**
     * Non-exhaustively searches for at least one task with the given name, by not evaluating projects before searching.
     */
    public boolean tryFindUnqualifiedTaskCheaply(String name, ProjectInternal project) {
        // don't evaluate children, see if we know it's without validating it
        for (Project project1 : project.getAllprojects()) {
            if (project1.getTasks().getNames().contains(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds tasks that will have exactly the given name, without necessarily creating or configuring the tasks. Returns null if no such match found.
     */
    @Nullable
    public TaskSelectionResult selectWithName(final String taskName, final ProjectState project, boolean includeSubProjects) {
        if (includeSubProjects) {
            Set<Task> tasks = new LinkedHashSet<>();
            new MultiProjectTaskSelectionResult(taskName, project, false).collectTasks(tasks);
            if (!tasks.isEmpty()) {
                return new FixedTaskSelectionResult(tasks);
            }
        } else {
            ProjectInternal mutableProject = project.getMutableModel();
            discoverTasks(mutableProject);
            if (hasTask(taskName, mutableProject)) {
                return new TaskSelectionResult() {
                    @Override
                    public void collectTasks(Collection<? super Task> tasks) {
                        tasks.add(getExistingTask(mutableProject, taskName));
                    }
                };
            }
        }

        return null;
    }

    /**
     * Finds the names of all tasks, without necessarily creating or configuring the tasks. Returns an empty map when none are found.
     */
    public Map<String, TaskSelectionResult> selectAll(ProjectState project, boolean includeSubProjects) {
        Map<String, TaskSelectionResult> selected = new LinkedHashMap<>();

        if (includeSubProjects) {
            Set<String> taskNames = new LinkedHashSet<>();
            collectTaskNames(project, taskNames);
            for (String taskName : taskNames) {
                selected.put(taskName, new MultiProjectTaskSelectionResult(taskName, project, true));
            }
        } else {
            ProjectInternal mutableProject = project.getMutableModel();
            discoverTasks(mutableProject);
            for (String taskName : mutableProject.getTasks().getNames()) {
                selected.put(taskName, new SingleProjectTaskSelectionResult(taskName, mutableProject.getTasks()));
            }
        }

        return selected;
    }

    private static void discoverTasks(ProjectInternal project) {
        try {
            project.getTasks().discoverTasks();
        } catch (Throwable e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project.getDisplayName()), e);
        }
    }

    private static boolean hasTask(String taskName, ProjectInternal project) {
        return project.getTasks().getNames().contains(taskName) || project.getTasks().findByName(taskName) != null;
    }

    private static TaskInternal getExistingTask(ProjectInternal project, String taskName) {
        try {
            return (TaskInternal) project.getTasks().getByName(taskName);
        } catch (Throwable e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project.getDisplayName()), e);
        }
    }

    private static void collectTaskNames(ProjectState project, Set<String> result) {
        ProjectInternal mutableProject = project.getMutableModel();
        discoverTasks(mutableProject);
        result.addAll(mutableProject.getTasks().getNames());
        for (ProjectState subProject : project.getChildProjects()) {
            collectTaskNames(subProject, result);
        }
    }

    private static class FixedTaskSelectionResult implements TaskSelectionResult {
        private final Collection<Task> tasks;

        FixedTaskSelectionResult(Collection<Task> tasks) {
            this.tasks = tasks;
        }

        @Override
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

        @Override
        public void collectTasks(Collection<? super Task> tasks) {
            tasks.add(taskContainer.getByName(taskName));
        }
    }

    private static class MultiProjectTaskSelectionResult implements TaskSelectionResult {
        private final ProjectState project;
        private final String taskName;
        private final boolean discovered;

        MultiProjectTaskSelectionResult(String taskName, ProjectState project, boolean discovered) {
            this.project = project;
            this.taskName = taskName;
            this.discovered = discovered;
        }

        @Override
        public void collectTasks(Collection<? super Task> tasks) {
            collect(project, tasks);
        }

        private void collect(ProjectState project, Collection<? super Task> tasks) {
            ProjectInternal mutableProject = project.getMutableModel();
            if (!discovered) {
                discoverTasks(mutableProject);
            }
            if (hasTask(taskName, mutableProject)) {
                TaskInternal task = getExistingTask(mutableProject, taskName);
                tasks.add(task);
                if (task.getImpliesSubProjects()) {
                    return;
                }
            }
            for (ProjectState subProject : project.getChildProjects()) {
                collect(subProject, tasks);
            }
        }
    }
}
