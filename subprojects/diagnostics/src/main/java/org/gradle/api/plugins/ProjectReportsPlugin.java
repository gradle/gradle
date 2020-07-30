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
import org.gradle.api.plugins.internal.DefaultProjectReportsPluginConvention;
import org.gradle.api.reporting.dependencies.HtmlDependencyReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

import java.io.File;

/**
 * <p>A {@link Plugin} which adds some project visualization report tasks to a project.</p>
 */
public class ProjectReportsPlugin implements Plugin<Project> {
    public static final String TASK_REPORT = "taskReport";
    public static final String PROPERTY_REPORT = "propertyReport";
    public static final String DEPENDENCY_REPORT = "dependencyReport";
    public static final String HTML_DEPENDENCY_REPORT = "htmlDependencyReport";
    public static final String PROJECT_REPORT = "projectReport";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        final ProjectReportsPluginConvention convention = new DefaultProjectReportsPluginConvention(project);
        project.getConvention().getPlugins().put("projectReports", convention);

        project.getTasks().register(TASK_REPORT, TaskReportTask.class, taskReportTask -> {
            taskReportTask.setDescription("Generates a report about your tasks.");
            taskReportTask.conventionMapping("outputFile", () -> new File(convention.getProjectReportDir(), "tasks.txt"));
            taskReportTask.conventionMapping("projects", convention::getProjects);
        });

        project.getTasks().register(PROPERTY_REPORT, PropertyReportTask.class, propertyReportTask -> {
            propertyReportTask.setDescription("Generates a report about your properties.");
            propertyReportTask.conventionMapping("outputFile", () -> new File(convention.getProjectReportDir(), "properties.txt"));
            propertyReportTask.conventionMapping("projects", convention::getProjects);
        });

        project.getTasks().register(DEPENDENCY_REPORT, DependencyReportTask.class, dependencyReportTask -> {
            dependencyReportTask.setDescription("Generates a report about your library dependencies.");
            dependencyReportTask.conventionMapping("outputFile", () -> new File(convention.getProjectReportDir(), "dependencies.txt"));
            dependencyReportTask.conventionMapping("projects", convention::getProjects);
        });

        project.getTasks().create(HTML_DEPENDENCY_REPORT, HtmlDependencyReportTask.class, htmlDependencyReportTask -> {
            htmlDependencyReportTask.setDescription("Generates an HTML report about your library dependencies.");
            htmlDependencyReportTask.getReports().getHtml().getOutputLocation().convention(project.getLayout().getProjectDirectory().dir(project.provider(() -> new File(convention.getProjectReportDir(), "dependencies").getAbsolutePath())));
            htmlDependencyReportTask.conventionMapping("projects", convention::getProjects);
        });

        project.getTasks().register(PROJECT_REPORT, projectReportTask -> {
            projectReportTask.dependsOn(TASK_REPORT, PROPERTY_REPORT, DEPENDENCY_REPORT, HTML_DEPENDENCY_REPORT);
            projectReportTask.setDescription("Generates a report about your project.");
            projectReportTask.setGroup("reporting");
        });
    }
}
