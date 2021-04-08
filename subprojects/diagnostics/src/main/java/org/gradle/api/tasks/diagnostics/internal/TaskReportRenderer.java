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
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;

import java.util.List;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.*;

/**
 * <p>A {@code TaskReportRenderer} is responsible for rendering the model of a project task report.</p>
 */
public class TaskReportRenderer extends TextReportRenderer {
    private boolean currentProjectHasTasks;
    private boolean currentProjectHasRules;
    private boolean hasContent;
    private boolean detail;

    @Override
    public void startProject(ProjectDetails project) {
        currentProjectHasTasks = false;
        currentProjectHasRules = false;
        hasContent = false;
        detail = false;
        super.startProject(project);
    }

    @Override
    protected String createHeader(ProjectDetails project) {
        String header = super.createHeader(project);
        return "Tasks runnable from " + StringUtils.uncapitalize(header);
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
            getTextOutput().formatln("Default tasks: %s", CollectionUtils.join(", ", defaultTaskNames));
            hasContent = true;
        }
    }

    public void startTaskGroup(String taskGroup) {
        if (!GUtil.isTrue(taskGroup)) {
            addSubheading("Tasks");
        } else {
            addSubheading(StringUtils.capitalize(taskGroup) + " tasks");
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

    private void writeTask(TaskDetails task, String prefix) {
        getTextOutput().text(prefix);
        getTextOutput().withStyle(Identifier).text(task.getPath());
        if (GUtil.isTrue(task.getDescription())) {
            getTextOutput().withStyle(Description).format(" - %s", task.getDescription());
        }
        getTextOutput().println();
    }

    private void addSubheading(String header) {
        if (hasContent) {
            getTextOutput().println();
        }
        hasContent = true;
        getBuilder().subheading(header);
    }

    /**
     * Marks the end of the tasks for the current project.
     */
    public void completeTasks() {
        if (!currentProjectHasTasks) {
            getTextOutput().withStyle(Info).println("No tasks");
            hasContent = true;
        }
    }

    /**
     * Writes a rule for the current project.
     *
     * @param rule The rule
     */
    public void addRule(RuleDetails rule) {
        if (!currentProjectHasRules) {
            addSubheading("Rules");
        }
        getTextOutput().println(GUtil.elvis(rule.getDescription(), ""));
        currentProjectHasRules = true;
    }

    @Override
    public void complete() {
        if (!detail) {
            StyledTextOutput output = getTextOutput();
            BuildClientMetaData clientMetaData = getClientMetaData();

            output.println();
            output.text("To see all tasks and more detail, run ");
            clientMetaData.describeCommand(output.withStyle(UserInput), "tasks --all");
            output.println();
            output.println();
            output.text("To see more detail about a task, run ");
            clientMetaData.describeCommand(output.withStyle(UserInput), "help --task <task>");
            output.println();
        }
        super.complete();
    }
}
