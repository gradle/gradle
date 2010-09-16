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
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.ProjectReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

public class ImplicitTasksConfigurer implements Action<ProjectInternal> {
    private static final String HELP_GROUP = "help";
    public static final String HELP_TASK = "help";

    public void execute(ProjectInternal project) {
        TaskContainerInternal tasks = project.getImplicitTasks();

        Task task = tasks.add(HELP_TASK, Help.class);
        task.setDescription("Displays a help message");
        task.setGroup(HELP_GROUP);

        task = tasks.add("projects", ProjectReportTask.class);
        task.setDescription("Displays a list of the projects in this build.");
        task.setGroup(HELP_GROUP);

        task = tasks.add("tasks", TaskReportTask.class);
        task.setDescription(String.format("Displays a list of the tasks in %s.", project));
        task.setGroup(HELP_GROUP);

        task = tasks.add("dependencies", DependencyReportTask.class);
        task.setDescription(String.format("Displays a list of the dependencies of %s.", project));
        task.setGroup(HELP_GROUP);

        task = tasks.add("properties", PropertyReportTask.class);
        task.setDescription(String.format("Displays a list of the properties of %s.", project));
        task.setGroup(HELP_GROUP);
    }
}
