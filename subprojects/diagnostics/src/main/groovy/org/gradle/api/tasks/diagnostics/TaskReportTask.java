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

import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.tasks.diagnostics.internal.*;

import java.io.IOException;

/**
 * <p>Displays a list of tasks in the project. An instance of this type is used when you execute the {@code tasks} task
 * from the command-line.</p>
 */
public class TaskReportTask extends AbstractReportTask {
    private TaskReportRenderer renderer = new TaskReportRenderer();

    private boolean detail;

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

    public boolean isDetail() {
        return detail;
    }

    public void generate(Project project) throws IOException {
        renderer.showDetail(isDetail());
        renderer.addDefaultTasks(project.getDefaultTasks());

        AggregateMultiProjectTaskReportModel aggregateModel = new AggregateMultiProjectTaskReportModel(!isDetail());
        TaskDetailsFactory taskDetailsFactory = new TaskDetailsFactory(project);

        SingleProjectTaskReportModel projectTaskModel = new SingleProjectTaskReportModel(taskDetailsFactory);
        ProjectInternal projectInternal = (ProjectInternal) project;
        TaskContainerInternal tasks = projectInternal.getTasks();
        tasks.actualize();
        projectTaskModel.build(Sets.union(tasks, projectInternal.getImplicitTasks()));
        aggregateModel.add(projectTaskModel);

        for (Project subproject : project.getSubprojects()) {
            SingleProjectTaskReportModel subprojectTaskModel = new SingleProjectTaskReportModel(taskDetailsFactory);
            ProjectInternal subprojectInternal = (ProjectInternal) subproject;
            TaskContainerInternal subprojectTasks = subprojectInternal.getTasks();
            subprojectTasks.actualize();
            subprojectTaskModel.build(subprojectTasks);
            aggregateModel.add(subprojectTaskModel);
        }

        aggregateModel.build();

        DefaultGroupTaskReportModel model = new DefaultGroupTaskReportModel();
        model.build(aggregateModel);

        for (String group : model.getGroups()) {
            renderer.startTaskGroup(group);
            for (TaskDetails task : model.getTasksForGroup(group)) {
                renderer.addTask(task);
                for (TaskDetails child : task.getChildren()) {
                    renderer.addChildTask(child);
                }
            }
        }
        renderer.completeTasks();

        for (Rule rule : project.getTasks().getRules()) {
            renderer.addRule(rule);
        }
    }
}
