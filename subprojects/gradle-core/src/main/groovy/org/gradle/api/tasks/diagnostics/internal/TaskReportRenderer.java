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

package org.gradle.api.tasks.diagnostics.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.util.GUtil;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * <p>A {@code TaskReportRenderer} is responsible for rendering the model of a project task report.</p>
 *
 * @author Hans Dockter
 */
public class TaskReportRenderer extends TextProjectReportRenderer {
    private boolean currentProjectHasTasks;
    private boolean currentProjectHasRules;
    private boolean hasContent;
    private boolean detail;

    public TaskReportRenderer() {
    }

    public TaskReportRenderer(Appendable writer) {
        super(writer);
    }

    @Override
    public void startProject(Project project) {
        currentProjectHasTasks = false;
        currentProjectHasRules = false;
        hasContent = true;
        detail = false;
        super.startProject(project);
    }

    public void showDetail(boolean detail) {
        this.detail = detail;
    }
    
    /**
     * Writes the default task names for the current project.
     *
     * @param defaultTaskNames The default task names (must not be null)
     */
    public void addDefaultTasks(List<String> defaultTaskNames) {
        if (defaultTaskNames.size() > 0) {
            getFormatter().format("Default tasks: %s%n", GUtil.join(defaultTaskNames, ", "));
            hasContent = true;
        }
    }

    public void startTaskGroup(String taskGroup) {
        if (!GUtil.isTrue(taskGroup)) {
            addHeader(currentProjectHasTasks ? "Other tasks" : "Tasks");
        } else {
            addHeader(StringUtils.capitalize(taskGroup) + " tasks");
        }
        currentProjectHasTasks = true;
    }

    /**
     * Writes a task for the current project.
     *
     * @param task The task
     */
    public void addTask(TaskDetails task) {
        writeTask(task, "");
    }

    public void addChildTask(TaskDetails task) {
        if (detail) {
            writeTask(task, "    ");
        }
    }

    private void writeTask(TaskDetails task, String prefix) {
        getFormatter().format("%s%s%s", prefix, task.getPath(), getDescription(task));
        if (detail) {
            SortedSet<String> sortedDependencies = new TreeSet<String>();
            for (String dependency : task.getDependencies()) {
                sortedDependencies.add(dependency);
            }
            if (sortedDependencies.size() > 0) {
                getFormatter().format(" [%s]", GUtil.join(sortedDependencies, ", "));
            }
        }
        getFormatter().format("%n");
    }

    private void addHeader(String header) {
        if (hasContent) {
            getFormatter().format("%n");
        }
        hasContent = true;
        getFormatter().format("%s%n", header);
        for (int i = 0; i < header.length(); i++) {
            getFormatter().format("-");
        }
        getFormatter().format("%n");
    }

    private String getDescription(TaskDetails task) {
        return GUtil.isTrue(task.getDescription()) ? " - " + task.getDescription() : "";
    }

    /**
     * Marks the end of the tasks for the current project.
     */
    public void completeTasks() {
        if (!currentProjectHasTasks) {
            getFormatter().format("No tasks%n");
            hasContent = true;
        }
    }

    /**
     * Writes a rule for the current project.
     *
     * @param rule The rule
     */
    public void addRule(Rule rule) {
        if (!currentProjectHasRules) {
            addHeader("Rules");
        }
        getFormatter().format("%s%n", GUtil.elvis(rule.getDescription(), ""));
        currentProjectHasRules = true;
    }
}
