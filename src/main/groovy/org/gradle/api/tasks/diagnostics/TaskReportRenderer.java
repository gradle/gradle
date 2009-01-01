/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.Task;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Hans Dockter
 */
public class TaskReportRenderer extends TextProjectReportRenderer {
    private boolean currentProjectHasTasks;

    public TaskReportRenderer() {
    }

    public TaskReportRenderer(Appendable writer) {
        super(writer);
    }

    @Override
    public void startProject(Project project) {
        currentProjectHasTasks = false;
        super.startProject(project);
    }

    @Override
    public void completeProject(Project project) {
        if (!currentProjectHasTasks) {
            getFormatter().format("No tasks%n");
        }
        super.completeProject(project);
    }

    /**
     * Writes a task for the current project.
     *
     * @param task The task
     */
    public void addTask(Task task) {
        SortedSet<String> sortedDependencies = new TreeSet<String>();
        for (Task dependency : task.getTaskDependencies().getDependencies(task)) {
            sortedDependencies.add(dependency.getPath());
        }
        getFormatter().format("Task %s %s%n", task.getPath(), sortedDependencies);
        currentProjectHasTasks = true;
    }
}
