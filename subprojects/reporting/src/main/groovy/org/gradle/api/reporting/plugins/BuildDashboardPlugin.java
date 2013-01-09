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
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.GenerateBuildDashboard;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which allows to generate build dashboard report.</p>
 */
public class BuildDashboardPlugin implements Plugin<ProjectInternal> {
    public static final String BUILD_DASHBOARD_TASK_NAME = "buildDashboard";

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(ReportingBasePlugin.class);

        GenerateBuildDashboard buildDashboardTask = project.getTasks().add(BUILD_DASHBOARD_TASK_NAME, GenerateBuildDashboard.class);

        Set<Project> currentAndSubprojects = new LinkedHashSet<Project>(project.getSubprojects());
        currentAndSubprojects.add(project);

        for (Project p : currentAndSubprojects) {
            aggregateReportings(p, buildDashboardTask);
        }

        addReportDestinationConventionMapping(project, buildDashboardTask.getReports().getHtml());
    }

    private void addReportDestinationConventionMapping(final ProjectInternal project, SingleFileReport buildDashboardReport) {
        IConventionAware report = (IConventionAware) buildDashboardReport;
        report.getConventionMapping().map("destination", new Callable<File>() {
            public File call() throws Exception {
                return project.getExtensions().getByType(ReportingExtension.class).file("buildDashboard/index.html");
            }
        });
    }

    private void aggregateReportings(Project project, final GenerateBuildDashboard buildDashboardTask) {
        project.getTasks().matching(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element instanceof Reporting;
            }
        }).all(new Action<Task>() {
            public void execute(Task task) {
                buildDashboardTask.aggregate((Reporting) task);
            }
        });
    }
}
