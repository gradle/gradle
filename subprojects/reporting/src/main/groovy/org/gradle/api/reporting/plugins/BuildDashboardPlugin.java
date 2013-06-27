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

import org.gradle.api.*;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.GenerateBuildDashboard;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.ReportingExtension;

import java.util.concurrent.Callable;

/**
 * Adds a task, "buildDashboard", that aggregates the output of all tasks executed during the build that produce reports.
 */
@Incubating
public class BuildDashboardPlugin implements Plugin<ProjectInternal> {

    public static final String BUILD_DASHBOARD_TASK_NAME = "buildDashboard";

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(ReportingBasePlugin.class);

        final GenerateBuildDashboard buildDashboardTask = project.getTasks().create(BUILD_DASHBOARD_TASK_NAME, GenerateBuildDashboard.class);

        DirectoryReport htmlReport = buildDashboardTask.getReports().getHtml();
        ConventionMapping htmlReportConventionMapping = new DslObject(htmlReport).getConventionMapping();
        htmlReportConventionMapping.map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return project.getExtensions().getByType(ReportingExtension.class).file("buildDashboard");
            }
        });

        Action<Task> captureReportingTasks = new Action<Task>() {
            public void execute(Task task) {
                if (!(task instanceof Reporting)) {
                    return;
                }

                Reporting reporting = (Reporting) task;

                buildDashboardTask.aggregate(reporting);

                if (!task.equals(buildDashboardTask)) {
                    task.finalizedBy(buildDashboardTask);
                }
            }
        };

        for (Project aProject : project.getAllprojects()) {
            aProject.getTasks().all(captureReportingTasks);
        }
    }

}
