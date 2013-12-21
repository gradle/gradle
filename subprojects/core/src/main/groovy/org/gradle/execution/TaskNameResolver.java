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
import org.gradle.api.tasks.TaskContainer;

import java.util.Collections;

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
            selected.put(task.getName(), new LazyTaskSelectionResult(task.getName(), project.getTasks()));
        } else {
            task = project.getImplicitTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), new LazyTaskSelectionResult(task.getName(), project.getImplicitTasks()));
            }
        }
        for (Project additionalProject : additionalProjects) {
            task = additionalProject.getTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), new LazyTaskSelectionResult(task.getName(), additionalProject.getTasks()));
            }
        }
        if (!selected.isEmpty()) {
            return selected;
        }

        for (String taskName : project.getTasks().getNames()) {
            selected.put(taskName, new LazyTaskSelectionResult(taskName, project.getTasks()));
        }
        for (String taskName : project.getImplicitTasks().getNames()) {
            if (!selected.containsKey(taskName)) {
                selected.put(taskName, new LazyTaskSelectionResult(taskName, project.getImplicitTasks()));
            }
        }

        for (Project additionalProject : additionalProjects) {
            for (String taskName : additionalProject.getTasks().getNames()) {
                selected.put(taskName, new LazyTaskSelectionResult(taskName, additionalProject.getTasks()));
            }
        }

        return selected;
    }


    public static class LazyTaskSelectionResult implements TaskSelectionResult {
        private final TaskContainer taskContainer;
        private final String taskName;

        public LazyTaskSelectionResult(String taskName, TaskContainer tasksContainer) {
            this.taskContainer = tasksContainer;
            this.taskName = taskName;
        }

        public Task getTask() {
            return taskContainer.getByName(taskName);
        }
    }
}
