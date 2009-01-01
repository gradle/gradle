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
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.util.WrapUtil;

import java.util.Map;

/**
 * <p>A {@link Plugin} which adds some project visualization report tasks to a project.</p>
 */
public class ProjectReportsPlugin implements Plugin {
    public void apply(Project project, PluginRegistry pluginRegistry, Map customValues) {
        project.createTask(WrapUtil.toMap(Task.TASK_TYPE, TaskReportTask.class), "taskReport");
        project.createTask(WrapUtil.toMap(Task.TASK_TYPE, PropertyReportTask.class), "propertyReport");
        project.createTask(WrapUtil.toMap(Task.TASK_TYPE, DependencyReportTask.class), "dependencyReport");
    }
}
