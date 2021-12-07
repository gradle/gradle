/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.reporting.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.GenerateBuildDashboard;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.TaskProvider;

/**
 * Adds a task, "buildDashboard", that aggregates the output of all tasks that produce reports.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/build_dashboard_plugin.html">Build Dashboard plugin reference</a>
 */
public class BuildDashboardPlugin implements Plugin<Project> {

    public static final String BUILD_DASHBOARD_TASK_NAME = "buildDashboard";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);

        final TaskProvider<GenerateBuildDashboard> buildDashboard = project.getTasks().register(BUILD_DASHBOARD_TASK_NAME, GenerateBuildDashboard.class, new Action<GenerateBuildDashboard>() {
            @Override
            public void execute(final GenerateBuildDashboard buildDashboardTask) {
                buildDashboardTask.setDescription("Generates a dashboard of all the reports produced by this build.");
                buildDashboardTask.setGroup("reporting");

                DirectoryReport htmlReport = buildDashboardTask.getReports().getHtml();
                htmlReport.getOutputLocation().convention(project.getLayout().getProjectDirectory().dir(project.provider(() -> project.getExtensions().getByType(ReportingExtension.class).file("buildDashboard").getAbsolutePath())));
            }
        });

        for (Project aProject : project.getAllprojects()) {
            aProject.getTasks().configureEach(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    if (!(task instanceof Reporting)) {
                        return;
                    }

                    if (!task.getName().equals(BUILD_DASHBOARD_TASK_NAME)) {
                        task.finalizedBy(buildDashboard);
                    }
                }
            });
        }
    }
}
