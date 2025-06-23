/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.reporting.dependencies.HtmlDependencyReportTask;
import org.gradle.api.tasks.diagnostics.ConventionReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.util.internal.WrapUtil;
import org.jspecify.annotations.NullMarked;

/**
 * <p>A {@link Plugin} which adds some project visualization report tasks to a project.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/project_report_plugin.html">Project Report plugin reference</a>
 */
@NullMarked
public abstract class ProjectReportsPlugin implements Plugin<Project> {
    public static final String TASK_REPORT = "taskReport";
    public static final String PROPERTY_REPORT = "propertyReport";
    public static final String DEPENDENCY_REPORT = "dependencyReport";
    public static final String HTML_DEPENDENCY_REPORT = "htmlDependencyReport";
    public static final String PROJECT_REPORT = "projectReport";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        project.getTasks().register(TASK_REPORT, TaskReportTask.class, taskReportTask -> {
            configureReportTask(project, taskReportTask, "tasks.txt", "tasks");
        });

        project.getTasks().register(PROPERTY_REPORT, PropertyReportTask.class, propertyReportTask -> {
            configureReportTask(project, propertyReportTask, "properties.txt", "properties");
        });

        project.getTasks().register(DEPENDENCY_REPORT, DependencyReportTask.class, dependencyReportTask -> {
            configureReportTask(project, dependencyReportTask, "dependencies.txt", "library dependencies");
        });

        project.getTasks().register(HTML_DEPENDENCY_REPORT, HtmlDependencyReportTask.class, htmlDependencyReportTask -> {
            htmlDependencyReportTask.getProjectReportDirectory().convention(getProjectFile(project));
            htmlDependencyReportTask.setDescription("Generates an HTML report about your library dependencies.");
            htmlDependencyReportTask.getReports().getHtml().getOutputLocation().convention(htmlDependencyReportTask.getProjectReportDirectory().dir("dependencies"));
            htmlDependencyReportTask.conventionMapping("projects", () -> WrapUtil.toSet(project));
        });

        project.getTasks().register(PROJECT_REPORT, projectReportTask -> {
            projectReportTask.dependsOn(TASK_REPORT, PROPERTY_REPORT, DEPENDENCY_REPORT, HTML_DEPENDENCY_REPORT);
            projectReportTask.setDescription("Generates a report about your project.");
            projectReportTask.setGroup("reporting");
        });
    }

    private static Provider<Directory> getProjectFile(Project project) {
        return project.getExtensions().getByType(ReportingExtension.class).getBaseDirectory().dir("project");
    }

    private static void configureReportTask(Project project, ConventionReportTask reportTask, String outputFileName, String description) {
        reportTask.getProjectReportDirectory().convention(getProjectFile(project));
        reportTask.setDescription("Generates a report about your " + description + " project.");
        reportTask.conventionMapping("outputFile", () -> reportTask.getProjectReportDirectory().file(outputFileName).get().getAsFile());
        reportTask.conventionMapping("projects", () -> WrapUtil.toSet(project));
    }
}
