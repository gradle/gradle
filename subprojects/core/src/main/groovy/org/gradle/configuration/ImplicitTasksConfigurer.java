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
package org.gradle.configuration;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.tasks.diagnostics.ProjectReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

public class ImplicitTasksConfigurer implements Action<ProjectInternal> {
    public static final String HELP_GROUP = "help";
    public static final String HELP_TASK = "help";
    public static final String PROJECTS_TASK = "projects";
    public static final String TASKS_TASK = "tasks";
    public static final String PROPERTIES_TASK = "properties";

    public void execute(ProjectInternal project) {
        TaskContainerInternal tasks = project.getImplicitTasks();

        Task task = tasks.add(HELP_TASK, Help.class);
        task.setDescription("Displays a help message");
        task.setGroup(HELP_GROUP);

        task = tasks.add(PROJECTS_TASK, ProjectReportTask.class);
        task.setDescription(String.format("Displays the sub-projects of %s.", project));
        task.setGroup(HELP_GROUP);

        task = tasks.add(TASKS_TASK, TaskReportTask.class);
        task.setDescription(String.format("Displays the tasks runnable from %s (some of the displayed tasks may belong to subprojects).", project));
        task.setGroup(HELP_GROUP);

        task = tasks.add(PROPERTIES_TASK, PropertyReportTask.class);
        task.setDescription(String.format("Displays the properties of %s.", project));
        task.setGroup(HELP_GROUP);

        applyPlugins(project);
    }

    void applyPlugins(ProjectInternal project) {
        project.getPlugins().apply("dependency-reporting");
    }
}
