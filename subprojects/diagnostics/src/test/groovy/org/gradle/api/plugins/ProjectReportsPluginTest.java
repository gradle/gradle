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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.HtmlDependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.junit.Test;

import java.io.File;

import static org.gradle.util.Matchers.dependsOn;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ProjectReportsPluginTest {
    private final Project project = HelperUtil.createRootProject();
    private final ProjectReportsPlugin plugin = new ProjectReportsPlugin();

    @Test
    public void appliesBaseReportingPluginAndAddsConventionObject() {
        plugin.apply(project);

        assertTrue(project.getPlugins().hasPlugin(ReportingBasePlugin.class));
        assertThat(project.getConvention().getPlugin(ProjectReportsPluginConvention.class), notNullValue());
    }

    @Test
    public void addsTasksToProject() {
        plugin.apply(project);

        Task task = project.getTasks().getByName(ProjectReportsPlugin.TASK_REPORT);
        assertThat(task, instanceOf(TaskReportTask.class));
        assertThat(task.property("outputFile"), equalTo((Object) new File(project.getBuildDir(), "reports/project/tasks.txt")));
        assertThat(task.property("projects"), equalTo((Object) WrapUtil.toSet(project)));

        task = project.getTasks().getByName(ProjectReportsPlugin.PROPERTY_REPORT);
        assertThat(task, instanceOf(PropertyReportTask.class));
        assertThat(task.property("outputFile"), equalTo((Object) new File(project.getBuildDir(), "reports/project/properties.txt")));
        assertThat(task.property("projects"), equalTo((Object) WrapUtil.toSet(project)));

        task = project.getTasks().getByName(ProjectReportsPlugin.DEPENDENCY_REPORT);
        assertThat(task, instanceOf(DependencyReportTask.class));
        assertThat(task.property("outputFile"), equalTo((Object) new File(project.getBuildDir(), "reports/project/dependencies.txt")));
        assertThat(task.property("projects"), equalTo((Object) WrapUtil.toSet(project)));

        task = project.getTasks().getByName(ProjectReportsPlugin.HTML_DEPENDENCY_REPORT);
        assertThat(task, instanceOf(HtmlDependencyReportTask.class));
        assertThat(task.property("outputDirectory"), equalTo((Object) new File(project.getBuildDir(), "reports/project/dependencies")));
        assertThat(task.property("projects"), equalTo((Object) WrapUtil.toSet(project)));
        
        task = project.getTasks().getByName(ProjectReportsPlugin.PROJECT_REPORT);
        assertThat(task, dependsOn(ProjectReportsPlugin.TASK_REPORT, ProjectReportsPlugin.PROPERTY_REPORT, ProjectReportsPlugin.DEPENDENCY_REPORT, ProjectReportsPlugin.HTML_DEPENDENCY_REPORT));
    }
}
