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
import org.gradle.util.TestUtil

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn

class ProjectReportsPluginTest extends AbstractProjectBuilderSpec {
    private final ProjectReportsPlugin plugin = TestUtil.newInstance(ProjectReportsPlugin.class)

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
        TaskReportTask taskReport = project.tasks.named(ProjectReportsPlugin.TASK_REPORT, TaskReportTask).get();
        taskReport.outputFile.get().asFile == new File(project.layout.buildDirectory.asFile.get(), "reports/project/tasks.txt")
        taskReport.projects.get() == [project] as Set

        PropertyReportTask propertyReport = project.tasks.named(ProjectReportsPlugin.PROPERTY_REPORT, PropertyReportTask).get()
        propertyReport.outputFile.get().asFile == new File(project.layout.buildDirectory.asFile.get(), "reports/project/properties.txt")
        propertyReport.projects.get() == [project] as Set

        DependencyReportTask dependencyReport = project.tasks.named(ProjectReportsPlugin.DEPENDENCY_REPORT, DependencyReportTask).get();
        dependencyReport.outputFile.get().asFile == new File(project.layout.buildDirectory.asFile.get(), "reports/project/dependencies.txt")
        dependencyReport.projects.get() == [project] as Set

        HtmlDependencyReportTask htmlReport = project.tasks.named(ProjectReportsPlugin.HTML_DEPENDENCY_REPORT, HtmlDependencyReportTask).get();
        htmlReport.reports.html.outputLocation.get().asFile == new File(project.layout.buildDirectory.asFile.get(), "reports/project/dependencies")
        htmlReport.projects.get() == [project] as Set

        Task projectReport = project.getTasks().named(ProjectReportsPlugin.PROJECT_REPORT).get();
        projectReport dependsOn(ProjectReportsPlugin.TASK_REPORT, ProjectReportsPlugin.PROPERTY_REPORT, ProjectReportsPlugin.DEPENDENCY_REPORT, ProjectReportsPlugin.HTML_DEPENDENCY_REPORT)
    }
}
