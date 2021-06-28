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
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.AggregateMultiProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.DefaultGroupTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.RuleDetails;
import org.gradle.api.tasks.diagnostics.internal.SingleProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.TaskDetails;
import org.gradle.api.tasks.diagnostics.internal.TaskDetailsFactory;
import org.gradle.api.tasks.diagnostics.internal.TaskReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.Try;
import org.gradle.internal.serialization.Cached;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * <p>Displays a list of tasks in the project. An instance of this type is used when you execute the {@code tasks} task
 * from the command-line.</p>
 *
 * By default, this report shows only those tasks which have been assigned to a task group, so-called <i>visible</i>
 * tasks. Tasks which have not been assigned to a task group, so-called <i>hidden</i> tasks, can be included in the report
 * by enabling the command line option {@code --all}.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class TaskReportTask extends ConventionReportTask {

    private boolean detail;
    private String group;
    private final Cached<TaskReportModel> model = Cached.of(this::computeTaskReportModel);
    private transient TaskReportRenderer renderer;

    @Override
    public ReportRenderer getRenderer() {
        if (renderer == null) {
            renderer = new TaskReportRenderer();
        }
        return renderer;
    }

    public void setRenderer(TaskReportRenderer renderer) {
        this.renderer = renderer;
    }

    @Option(option = "all", description = "Show additional tasks and detail.")
    public void setShowDetail(boolean detail) {
        this.detail = detail;
    }

    // TODO config-cache - should invalidate the cache or the filtering and merging should be moved to task execution time
    @Console
    public boolean isDetail() {
        return detail;
    }

    /**
     * Set a specific task group to be displayed.
     *
     * @since 5.1
     */
    @Option(option = "group", description = "Show tasks for a specific group.")
    public void setDisplayGroup(String group) {
        this.group = group;
    }

    /**
     * Get the task group to be displayed.
     *
     * @since 5.1
     */
    @Console
    public String getDisplayGroup() {
        return group;
    }

    @TaskAction
    void generate() {
        reportGenerator().generateReport(
            model.get().projects,
            projectModel -> projectModel.get().project,
            projectModel -> {
                render(projectModel.get());
                logClickableOutputFileUrl();
            }
        );
    }

    private TaskReportModel computeTaskReportModel() {
        return new TaskReportModel(computeProjectModels());
    }

    private List<Try<ProjectReportModel>> computeProjectModels() {
        List<Try<ProjectReportModel>> result = new ArrayList<>();
        for (Project project : new TreeSet<>(getProjects())) {
            result.add(Try.ofFailable(() -> projectReportModelFor(project)));
        }
        return result;
    }

    private static class TaskReportModel {
        final List<Try<ProjectReportModel>> projects;

        public TaskReportModel(List<Try<ProjectReportModel>> projects) {
            this.projects = projects;
        }
    }

    private static class ProjectReportModel {
        public final ProjectDetails project;
        public final List<String> defaultTasks;
        public final DefaultGroupTaskReportModel tasks;
        public final List<RuleDetails> rules;

        public ProjectReportModel(
            ProjectDetails project,
            List<String> defaultTasks,
            DefaultGroupTaskReportModel tasks,
            List<RuleDetails> rules
        ) {
            this.project = project;
            this.defaultTasks = defaultTasks;
            this.tasks = tasks;
            this.rules = rules;
        }
    }

    private ProjectReportModel projectReportModelFor(Project project) {
        return new ProjectReportModel(
            ProjectDetails.of(project),
            project.getDefaultTasks(),
            taskReportModelFor(project, isDetail()),
            Strings.isNullOrEmpty(group) ? ruleDetailsFor(project) : emptyList()
        );
    }

    private void render(ProjectReportModel reportModel) {
        renderer.showDetail(isDetail());
        renderer.addDefaultTasks(reportModel.defaultTasks);

        DefaultGroupTaskReportModel model = reportModel.tasks;
        for (String group : model.getGroups()) {
            renderer.startTaskGroup(group);
            for (TaskDetails task : model.getTasksForGroup(group)) {
                renderer.addTask(task);
            }
        }
        renderer.completeTasks();

        for (RuleDetails rule : reportModel.rules) {
            renderer.addRule(rule);
        }
    }

    private List<RuleDetails> ruleDetailsFor(Project project) {
        return project.getTasks().getRules().stream().map(rule -> RuleDetails.of(rule.getDescription())).collect(Collectors.toList());
    }

    private DefaultGroupTaskReportModel taskReportModelFor(Project project, boolean detail) {
        final AggregateMultiProjectTaskReportModel aggregateModel = new AggregateMultiProjectTaskReportModel(!detail, detail, getDisplayGroup());
        final TaskDetailsFactory taskDetailsFactory = new TaskDetailsFactory(project);

        final SingleProjectTaskReportModel projectTaskModel = buildTaskReportModelFor(taskDetailsFactory, project);
        aggregateModel.add(projectTaskModel);

        for (final Project subproject : project.getSubprojects()) {
            aggregateModel.add(buildTaskReportModelFor(taskDetailsFactory, subproject));
        }

        aggregateModel.build();

        return DefaultGroupTaskReportModel.of(aggregateModel);
    }

    private SingleProjectTaskReportModel buildTaskReportModelFor(
        final TaskDetailsFactory taskDetailsFactory,
        final Project subproject
    ) {
        return projectStateFor(subproject).fromMutableState(
            project -> SingleProjectTaskReportModel.forTasks(
                getProjectTaskLister().listProjectTasks(project),
                taskDetailsFactory
            )
        );
    }

    private ProjectState projectStateFor(Project subproject) {
        return getProjectStateRegistry().stateFor(subproject);
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
