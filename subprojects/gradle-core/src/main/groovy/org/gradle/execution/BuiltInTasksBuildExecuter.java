/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.util.WrapUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link BuildExecuter} which executes the built-in tasks which are executable from the command-line.
 */
public class BuiltInTasksBuildExecuter implements BuildExecuter {
    private GradleInternal gradle;
    public static final String ALL_PROJECTS_WILDCARD = "?";

    public enum Options {
        TASKS {
            @Override
            public String toString() {
                return "task list";
            }
            @Override
            Class<? extends AbstractReportTask> getTaskType() {
                return TaskReportTask.class;
            }},
        PROPERTIES {
            @Override
            public String toString() {
                return "property list";
            }
            @Override
            Class<? extends AbstractReportTask> getTaskType() {
                return PropertyReportTask.class;
            }},
        DEPENDENCIES {
            @Override
            public String toString() {
                return "dependency list";
            }
            @Override
            Class<? extends AbstractReportTask> getTaskType() {
                return DependencyReportTask.class;
            }};

        abstract Class<? extends AbstractReportTask> getTaskType();
    }

    private Options options;
    private AbstractReportTask task;
    private String path;

    public BuiltInTasksBuildExecuter(Options options, String path) {
        this.options = options;
        this.path = path;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public void select(GradleInternal gradle) {
        this.gradle = gradle;
        ITaskFactory taskFactory = gradle.getServiceRegistryFactory().get(ITaskFactory.class);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(Task.TASK_NAME, "report");
        args.put(Task.TASK_TYPE, options.getTaskType());
        task = (AbstractReportTask) taskFactory.createTask(gradle.getDefaultProject(), args);
        task.setProjects(getProjectsForReport(path));
    }

    private Set<Project> getProjectsForReport(String path) {
        if (path != null) {
            return path.equals(ALL_PROJECTS_WILDCARD) ? gradle.getRootProject().getAllprojects() : WrapUtil.toSet(
                    gradle.getRootProject().project(path));
        }
        return WrapUtil.<Project>toSet(gradle.getDefaultProject());
    }

    public String getDisplayName() {
        return options.toString();
    }

    public AbstractReportTask getTask() {
        return task;
    }

    public void execute() {
        gradle.getTaskGraph().execute(Collections.singleton(task));
    }
}
