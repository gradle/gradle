/*
 * Copyright 2008 the original author or authors.
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
import org.gradle.api.Task;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import static org.gradle.util.WrapUtil.*;

import java.io.File;
import java.util.Map;

/**
 * <p>A {@link Plugin} which adds some project visualization report tasks to a project.</p>
 */
public class ProjectReportsPlugin implements Plugin {
    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(ReportingBasePlugin.class, project, customValues);
        
        TaskReportTask taskReportTask = project.getTasks().add("taskReport", TaskReportTask.class);
        taskReportTask.setOutputFile(new File(project.getBuildDir(), "reports/project/tasks.txt"));
        taskReportTask.setDescription("Shows a report about your tasks.");

        PropertyReportTask propertyReportTask = project.getTasks().add("propertyReport", PropertyReportTask.class);
        propertyReportTask.setOutputFile(new File(project.getBuildDir(), "reports/project/properties.txt"));
        propertyReportTask.setDescription("Shows a report about your properties.");

        DependencyReportTask dependencyReportTask = project.getTasks().add("dependencyReport", DependencyReportTask.class);
        dependencyReportTask.setOutputFile(new File(project.getBuildDir(), "reports/project/dependencies.txt"));
        dependencyReportTask.setDescription("Shows a report about your library dependencies.");
    }
}
