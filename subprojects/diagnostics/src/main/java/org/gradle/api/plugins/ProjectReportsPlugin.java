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
import org.gradle.api.reporting.dependencies.HtmlDependencyReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.WrapUtil;

/**
 * <p>A {@link Plugin} which adds some project visualization report tasks to a project.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/project_report_plugin.html">Project Report plugin reference</a>
 */
public abstract class ProjectReportsPlugin implements Plugin<Project> {
    public static final String TASK_REPORT = "taskReport";
    public static final String PROPERTY_REPORT = "propertyReport";
    public static final String DEPENDENCY_REPORT = "dependencyReport";
    public static final String HTML_DEPENDENCY_REPORT = "htmlDependencyReport";
    public static final String PROJECT_REPORT = "projectReport";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        @SuppressWarnings("deprecation")
        final ProjectReportsPluginConvention convention = project.getObjects().newInstance(org.gradle.api.plugins.internal.DefaultProjectReportsPluginConvention.class, project);

        DeprecationLogger.whileDisabled(new Runnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                project.getConvention().getPlugins().put("projectReports", convention);
            }
        });

        project.getTasks().register(TASK_REPORT, TaskReportTask.class, taskReportTask -> {
            taskReportTask.getProjectReportDirectory().convention(project.getLayout().dir(project.provider(() -> DeprecationLogger.whileDisabled(convention::getProjectReportDir))));
            taskReportTask.setDescription("Generates a report about your tasks.");
            taskReportTask.getOutputFile().convention(taskReportTask.getProjectReportDirectory().file("tasks.txt"));
            taskReportTask.getProjects().convention(WrapUtil.toSet(project));
        });

        project.getTasks().register(PROPERTY_REPORT, PropertyReportTask.class, propertyReportTask -> {
            propertyReportTask.getProjectReportDirectory().convention(project.getLayout().dir(project.provider(() -> DeprecationLogger.whileDisabled(convention::getProjectReportDir))));
            propertyReportTask.setDescription("Generates a report about your properties.");
            propertyReportTask.getOutputFile().convention(propertyReportTask.getProjectReportDirectory().file("properties.txt"));
            propertyReportTask.getProjects().convention(WrapUtil.toSet(project));
        });

        project.getTasks().register(DEPENDENCY_REPORT, DependencyReportTask.class, dependencyReportTask -> {
            dependencyReportTask.getProjectReportDirectory().convention(project.getLayout().dir(project.provider(() -> DeprecationLogger.whileDisabled(convention::getProjectReportDir))));
            dependencyReportTask.setDescription("Generates a report about your library dependencies.");
            dependencyReportTask.getOutputFile().convention(dependencyReportTask.getProjectReportDirectory().file("dependencies.txt"));
            dependencyReportTask.getProjects().convention(WrapUtil.toSet(project));
        });

        project.getTasks().register(HTML_DEPENDENCY_REPORT, HtmlDependencyReportTask.class, htmlDependencyReportTask -> {
            htmlDependencyReportTask.getProjectReportDirectory().convention(project.getLayout().dir(project.provider(() -> DeprecationLogger.whileDisabled(convention::getProjectReportDir))));
            htmlDependencyReportTask.setDescription("Generates an HTML report about your library dependencies.");
            htmlDependencyReportTask.getReports().getHtml().getOutputLocation().convention(htmlDependencyReportTask.getProjectReportDirectory().dir("dependencies"));
            htmlDependencyReportTask.getProjects().convention(WrapUtil.toSet(project));
        });

        project.getTasks().register(PROJECT_REPORT, projectReportTask -> {
            projectReportTask.dependsOn(TASK_REPORT, PROPERTY_REPORT, DEPENDENCY_REPORT, HTML_DEPENDENCY_REPORT);
            projectReportTask.setDescription("Generates a report about your project.");
            projectReportTask.setGroup("reporting");
        });
    }
}
