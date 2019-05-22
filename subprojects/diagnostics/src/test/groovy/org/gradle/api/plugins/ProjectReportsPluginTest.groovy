/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.Task
import org.gradle.api.reporting.dependencies.HtmlDependencyReportTask
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.PropertyReportTask
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.instanceOf

class ProjectReportsPluginTest extends AbstractProjectBuilderSpec {
    private final ProjectReportsPlugin plugin = new ProjectReportsPlugin()

    def appliesBaseReportingPluginAndAddsConventionObject() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(ReportingBasePlugin.class)
        project.convention.getPlugin(ProjectReportsPluginConvention.class)
    }

    def addsTasksToProject() {
        when:
        plugin.apply(project);

        then:
        Task taskReport = project.tasks.getByName(ProjectReportsPlugin.TASK_REPORT);
        taskReport instanceOf(TaskReportTask.class)
        taskReport.outputFile == new File(project.buildDir, "reports/project/tasks.txt")
        taskReport.projects == [project] as Set

        Task propertyReport = project.tasks.getByName(ProjectReportsPlugin.PROPERTY_REPORT)
        propertyReport instanceOf(PropertyReportTask.class)
        propertyReport.outputFile == new File(project.buildDir, "reports/project/properties.txt")
        propertyReport.projects == [project] as Set

        Task dependencyReport = project.tasks.getByName(ProjectReportsPlugin.DEPENDENCY_REPORT);
        dependencyReport instanceOf(DependencyReportTask.class)
        dependencyReport.outputFile == new File(project.getBuildDir(), "reports/project/dependencies.txt")
        dependencyReport.projects == [project] as Set

        Task htmlReport = project.tasks.getByName(ProjectReportsPlugin.HTML_DEPENDENCY_REPORT);
        htmlReport instanceOf(HtmlDependencyReportTask.class)
        htmlReport.reports.html.destination == new File(project.buildDir, "reports/project/dependencies")
        htmlReport.projects == [project] as Set

        Task projectReport = project.getTasks().getByName(ProjectReportsPlugin.PROJECT_REPORT);
        projectReport dependsOn(ProjectReportsPlugin.TASK_REPORT, ProjectReportsPlugin.PROPERTY_REPORT, ProjectReportsPlugin.DEPENDENCY_REPORT, ProjectReportsPlugin.HTML_DEPENDENCY_REPORT)
    }
}
