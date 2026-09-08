/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildProjectRegistry;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.util.Collections.emptySet;

public class TasksFactory {
    Map<Project, Set<Task>> allTasks;
    private final boolean includeTasks;

    public TasksFactory(boolean includeTasks) {
        this.includeTasks = includeTasks;
    }

    public void collectTasks(Project root) {
        // Project.getAllTasks(true) reads the task container of every subproject, which is reported
        // as a cross-project access with Isolated Projects enabled. Collect the tasks of each
        // project under the lock that covers all of them instead.
        BuildProjectRegistry projects = ((ProjectInternal) root).getOwner().getOwner().getProjects();
        allTasks = projects.fromMutableStateOfAllProjects(access -> {
            // Same shape as DefaultProject.getAllTasks(true): tasks are discovered per project and
            // collected into a sorted set.
            Map<Project, Set<Task>> result = new TreeMap<>();
            for (ProjectState projectState : projects.getAllProjects()) {
                projectState.ensureTasksDiscovered();
                ProjectInternal project = access.getMutableModel(projectState);
                result.put(project, new TreeSet<>(project.getTasks()));
            }
            return result;
        });
    }

    public Set<Task> getTasks(Project project) {
        if (includeTasks) {
            return allTasks.get(project);
        } else {
            return emptySet();
        }
    }

}
