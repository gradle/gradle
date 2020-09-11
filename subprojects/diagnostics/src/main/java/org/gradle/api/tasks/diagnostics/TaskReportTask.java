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
package org.gradle.api.tasks.diagnostics;

import com.google.common.base.Strings;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.diagnostics.internal.AggregateMultiProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.DefaultGroupTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.SingleProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.TaskDetails;
import org.gradle.api.tasks.diagnostics.internal.TaskDetailsFactory;
import org.gradle.api.tasks.diagnostics.internal.TaskReportRenderer;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;

/**
 * <p>Displays a list of tasks in the project. An instance of this type is used when you execute the {@code tasks} task
 * from the command-line.</p>
 *
 * By default, this report shows only those tasks which have been assigned to a task group, so-called <i>visible</i>
 * tasks. Tasks which have not been assigned to a task group, so-called <i>hidden</i> tasks, can be included in the report
 * by enabling the command line option {@code --all}.
 */
public class TaskReportTask extends AbstractReportTask {
    private TaskReportRenderer renderer = new TaskReportRenderer();

    private boolean detail;
    private String group;

    @Override
    public ReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(TaskReportRenderer renderer) {
        this.renderer = renderer;
    }

    @Option(option = "all", description = "Show additional tasks and detail.")
    public void setShowDetail(boolean detail) {
        this.detail = detail;
    }

    @Console
    public boolean isDetail() {
        return detail;
    }

    /**
     * Set a specific task group to be displayed.
     *
     * @since 5.1
     */
    @Incubating
    @Option(option = "group", description = "Show tasks for a specific group.")
    public void setDisplayGroup(String group) {
        this.group = group;
    }

    /**
     * Get the task group to be displayed.
     *
     * @since 5.1
     */
    @Incubating
    @Console
    public String getDisplayGroup() {
        return group;
    }

    @Override
    public void generate(Project project) {
        renderer.showDetail(isDetail());
        renderer.addDefaultTasks(project.getDefaultTasks());

        AggregateMultiProjectTaskReportModel aggregateModel = new AggregateMultiProjectTaskReportModel(!isDetail(), isDetail(), getDisplayGroup());
        final TaskDetailsFactory taskDetailsFactory = new TaskDetailsFactory(project);

        SingleProjectTaskReportModel projectTaskModel = buildTaskReportModelFor(taskDetailsFactory, project);
        aggregateModel.add(projectTaskModel);

        for (final Project subproject : project.getSubprojects()) {
            aggregateModel.add(buildTaskReportModelFor(taskDetailsFactory, subproject));
        }

        aggregateModel.build();

        DefaultGroupTaskReportModel model = new DefaultGroupTaskReportModel();
        model.build(aggregateModel);

        for (String group : model.getGroups()) {
            renderer.startTaskGroup(group);
            for (TaskDetails task : model.getTasksForGroup(group)) {
                renderer.addTask(task);
            }
        }
        renderer.completeTasks();

        if (Strings.isNullOrEmpty(group)) {
            for (Rule rule : project.getTasks().getRules()) {
                renderer.addRule(rule);
            }
        }
    }

    private SingleProjectTaskReportModel buildTaskReportModelFor(final TaskDetailsFactory taskDetailsFactory, final Project subproject) {
        ProjectState projectState = getProjectStateRegistry().stateFor(subproject);
        return projectState.fromMutableState(p -> {
            SingleProjectTaskReportModel subprojectTaskModel = new SingleProjectTaskReportModel(taskDetailsFactory);
            subprojectTaskModel.build(getProjectTaskLister().listProjectTasks(subproject));
            return subprojectTaskModel;
        });
    }

    /**
     * Injects a {@code ProjectStateRegistry} service.
     *
     * @since 5.0
     */
    @Inject
    protected ProjectStateRegistry getProjectStateRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProjectTaskLister getProjectTaskLister() {
        throw new UnsupportedOperationException();
    }
}
