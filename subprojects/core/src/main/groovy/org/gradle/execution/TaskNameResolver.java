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
import com.google.common.collect.SetMultimap;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;

import java.util.Collections;
import java.util.Map;

public class TaskNameResolver {

    public SetMultimap<String, TaskSelectionResult> select(String name, Project project) {
        return select(name, (ProjectInternal) project, Collections.<Project>emptySet());
    }

    public SetMultimap<String, TaskSelectionResult> selectAll(String name, Project project) {
        return select(name, (ProjectInternal) project, project.getSubprojects());
    }

    private SetMultimap<String, TaskSelectionResult> select(String name, ProjectInternal project, Iterable<Project> additionalProjects) {
        SetMultimap<String, TaskSelectionResult> selected = LinkedHashMultimap.create();
        Task task = project.getTasks().findByName(name);
        if (task != null) {
            selected.put(task.getName(), new SimpleTaskSelectionResult(task));
        } else {
            task = project.getImplicitTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), new SimpleTaskSelectionResult(task));
            }
        }
        for (Project additionalProject : additionalProjects) {
            task = additionalProject.getTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), new SimpleTaskSelectionResult(task));
            }
        }
        if (!selected.isEmpty()) {
            return selected;
        }

        for (Task t : project.getTasks()) {
            selected.put(t.getName(), new SimpleTaskSelectionResult(t));
        }
        for (Task t : project.getImplicitTasks()) {
            if (!selected.containsKey(t.getName())) {
                selected.put(t.getName(), new SimpleTaskSelectionResult(t));
            }
        }
        Map<String, Runnable> placeholderActions = project.getTasks().getPlaceholderActions();
        for (Map.Entry<String, Runnable> placeholderAction : placeholderActions.entrySet()) {
            selected.put(placeholderAction.getKey(), new PlaceholderTaskSelectionResult(project.getTasks(), placeholderAction.getKey(), placeholderAction.getValue()));

        }
        for (Project additionalProject : additionalProjects) {
            for (Task t : additionalProject.getTasks()) {
                selected.put(t.getName(), new SimpleTaskSelectionResult(t));
            }

            final ProjectInternal additionalProjectInternal = (ProjectInternal) additionalProject;
            placeholderActions = additionalProjectInternal.getTasks().getPlaceholderActions();
            for (Map.Entry<String, Runnable> placeholderAction : placeholderActions.entrySet()) {
                selected.put(placeholderAction.getKey(), new PlaceholderTaskSelectionResult(additionalProjectInternal.getTasks(), placeholderAction.getKey(), placeholderAction.getValue()));

            }
        }

        return selected;
    }

    public static class SimpleTaskSelectionResult implements TaskSelectionResult {
        private final Task task;

        public SimpleTaskSelectionResult(Task task) {
            this.task = task;
        }

        public Task getTask() {
            return task;
        }
    }

    private static class PlaceholderTaskSelectionResult implements TaskSelectionResult {
        private final TaskContainerInternal taskContainerInternal;
        private final String taskName;
        private final Runnable placeholderAction;

        public PlaceholderTaskSelectionResult(TaskContainerInternal taskContainerInternal, String taskName, Runnable placeholderAction) {

            this.taskContainerInternal = taskContainerInternal;
            this.taskName = taskName;
            this.placeholderAction = placeholderAction;
        }

        public Task getTask() {
            placeholderAction.run();
            return taskContainerInternal.findByName(taskName);
        }
    }
}
