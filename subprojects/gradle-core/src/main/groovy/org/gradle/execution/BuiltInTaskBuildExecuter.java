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
import org.gradle.util.WrapUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link BuildExecuter} which executes the built-in tasks which are executable from the command-line.
 */
public abstract class BuiltInTaskBuildExecuter<T extends AbstractReportTask> implements BuildExecuter {
    private GradleInternal gradle;
    public static final String ALL_PROJECTS_WILDCARD = "?";

    private T task;
    private String path;

    public BuiltInTaskBuildExecuter(String path) {
        this.path = path;
    }

    protected abstract Class<T> getTaskType();

    public void select(GradleInternal gradle) {
        this.gradle = gradle;
        ITaskFactory taskFactory = gradle.getServices().get(ITaskFactory.class);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(Task.TASK_NAME, "report");
        args.put(Task.TASK_TYPE, getTaskType());
        task = getTaskType().cast(taskFactory.createTask(gradle.getDefaultProject(), args));
        task.setProjects(getProjectsForReport(path));
        afterConfigure(task);
    }

    protected void afterConfigure(T task) {
    }

    private Set<Project> getProjectsForReport(String path) {
        if (path != null) {
            return path.equals(ALL_PROJECTS_WILDCARD) ? gradle.getRootProject().getAllprojects() : WrapUtil.toSet(
                    gradle.getRootProject().project(path));
        }
        return WrapUtil.<Project>toSet(gradle.getDefaultProject());
    }

    public T getTask() {
        return task;
    }

    public void execute() {
        gradle.getTaskGraph().execute(Collections.singleton(task));
    }
}
