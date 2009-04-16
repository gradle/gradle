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
import org.gradle.api.Rule;
import org.gradle.util.GUtil;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.List;

/**
 * <p>A {@code TaskReportRenderer} is responsible for rendering the model of a project task report.</p>
 *
 * @author Hans Dockter
 */
public class TaskReportRenderer extends TextProjectReportRenderer {
    private boolean currentProjectHasTasksOrRules;

    public TaskReportRenderer() {
    }

    public TaskReportRenderer(Appendable writer) {
        super(writer);
    }

    @Override
    public void startProject(Project project) {
        currentProjectHasTasksOrRules = false;
        super.startProject(project);
    }

    @Override
    public void completeProject(Project project) {
        if (!currentProjectHasTasksOrRules) {
            getFormatter().format("No tasks%n");
        }
        super.completeProject(project);
    }

    /**
     * Writes the default task names for the current project.
     *
     * @param defaultTaskNames The default task names (must not be null)
     */
    public void addDefaultTasks(List<String> defaultTaskNames) {
        if (defaultTaskNames.size() > 0) {
            getFormatter().format("Default Tasks: %s%n%n", GUtil.join(defaultTaskNames, ", "));
        }
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
        getFormatter().format("%s %s%n", task.getPath(), getDescription(task));
        if (sortedDependencies.size() > 0) {
            getFormatter().format("   -> %s%n", GUtil.join(sortedDependencies, ", "));
        }
        currentProjectHasTasksOrRules = true;
    }

    private String getDescription(Task task) {
        return GUtil.isTrue(task.getDescription()) ? "- " + task.getDescription() : "";
    }

    /**
     * Writes a rule for the current project.
     *
     * @param rule The rule
     */
    public void addRule(Rule rule) {
        getFormatter().format("rule - %s%n", GUtil.elvis(rule.getDescription(), ""));
        currentProjectHasTasksOrRules = true;
    }
}
