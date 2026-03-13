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

import com.google.common.collect.ImmutableSetMultimap;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.AggregateMultiProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.DefaultGroupTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.RuleDetails;
import org.gradle.api.tasks.diagnostics.internal.SingleProjectTaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.TaskDetails;
import org.gradle.api.tasks.diagnostics.internal.TaskReportModel;
import org.gradle.api.tasks.diagnostics.internal.TaskReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.Try;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.serialization.Cached;
import org.gradle.util.Path;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public abstract class TaskReportTask extends ConventionReportTask {
    private final Cached<ComputedTaskReportModel> model = Cached.of(this::computeTaskReportModel);

    public TaskReportTask() {
        getRenderer().convention(new TaskReportRenderer(getClientMetaData())).finalizeValueOnRead();
        getShowDetail().convention(false);
    }

    @Override
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = ReplacedAccessor.AccessorType.SETTER, name = "setRenderer", originalType = TaskReportRenderer.class)
    })
    public abstract Property<TaskReportRenderer> getRenderer();

    // TODO config-cache - should invalidate the cache or the filtering and merging should be moved to task execution time
    /**
     * Sets whether to show "invisible" tasks without a group or dependent tasks.
     *
     * @since 9.0
     */
    @Input
    @Option(option = "all", description = "Show additional tasks and detail.")
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = ReplacedAccessor.AccessorType.GETTER, name = "isDetail", originalType = boolean.class),
        @ReplacedAccessor(value = ReplacedAccessor.AccessorType.SETTER, name = "setShowDetail", originalType = boolean.class)
    })
    public abstract Property<Boolean> getShowDetail();

    // kotlin source compatibility
    @Internal
    @Deprecated
    public Property<Boolean> getIsShowDetail() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsShowDetail()", "getShowDetail()");
        return getShowDetail();
    }

    /**
     * Returns the task group to be displayed.
     *
     * @since 5.1
     */
    @Console
    @Option(option = "group", description = "Show tasks for a specific group.")
    @ReplacesEagerProperty
    public abstract Property<String> getDisplayGroup();

    /**
     * Returns the task groups to be displayed.
     *
     * @since 7.5
     */
    @Console
    @Option(option = "groups", description = "Show tasks for specific groups (can be used multiple times to specify multiple groups).")
    public abstract ListProperty<String> getDisplayGroups();

    /**
     * Whether to show the task types next to their names in the output.
     *
     * @since 7.4
     */
    @Console
    @Option(option = "types", description = "Show task class types")
    public abstract Property<Boolean> getShowTypes();

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

    private ComputedTaskReportModel computeTaskReportModel() {
        return new ComputedTaskReportModel(computeProjectModels());
    }

    private List<Try<ProjectReportModel>> computeProjectModels() {
        List<Try<ProjectReportModel>> result = new ArrayList<>();
        for (Project project : new TreeSet<>(getProjects().get())) {
            result.add(Try.ofFailable(() -> projectReportModelFor(project)));
        }
        return result;
    }

    private static class ComputedTaskReportModel {
        final List<Try<ProjectReportModel>> projects;

        public ComputedTaskReportModel(List<Try<ProjectReportModel>> projects) {
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
        List<String> displayGroups = getDisplayGroups().getOrElse(emptyList());
        if (getDisplayGroup().isPresent()) {
            displayGroups = new ArrayList<>();
            displayGroups.add(getDisplayGroup().get());
            displayGroups.addAll(getDisplayGroups().get());
        }
        return new ProjectReportModel(
            ProjectDetails.of(project),
            project.getDefaultTasks(),
            taskReportModelFor(project, getShowDetail().get(), displayGroups),
            displayGroups.isEmpty() ? ruleDetailsFor(project) : emptyList()
        );
    }

    private void render(ProjectReportModel reportModel) {
        TaskReportRenderer renderer = getRenderer().get();
        renderer.showDetail(getShowDetail().get());
        renderer.showTypes(getShowTypes().get());
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

    private DefaultGroupTaskReportModel taskReportModelFor(Project project, boolean detail, List<String> displayGroups) {
        final AggregateMultiProjectTaskReportModel aggregateModel = new AggregateMultiProjectTaskReportModel(!detail, detail, displayGroups);

        ProjectIdentity relativeProjectIdentity = ((ProjectInternal) project).getProjectIdentity();

        Stream.concat(Stream.of(project), project.getSubprojects().stream())
            .map(p -> buildTaskReportModelFor(relativeProjectIdentity, p))
            .forEach(aggregateModel::add);

        aggregateModel.build();

        return DefaultGroupTaskReportModel.of(aggregateModel);
    }

    private SingleProjectTaskReportModel buildTaskReportModelFor(ProjectIdentity relativeProjectIdentity, Project p) {
        return ((ProjectInternal) p).getOwner().fromMutableState(project -> {
            ImmutableSetMultimap.Builder<String, TaskDetails> groups = ImmutableSetMultimap.<String, TaskDetails>builder()
                .orderKeysBy(String::compareToIgnoreCase)
                .orderValuesBy(Comparator.comparing(TaskDetails::getPath));

            for (Task task : getProjectTaskLister().listProjectTasks(project)) {
                groups.put(
                    getGroupFor(task),
                    TaskDetails.of(getPathFor(relativeProjectIdentity, task), task)
                );
            }

            return new SingleProjectTaskReportModel(groups.build());
        });
    }

    /**
     * Determines the path of this task, relative to the given project ID.
     */
    private static Path getPathFor(ProjectIdentity relativeProjectIdentity, Task task) {
        TaskIdentity<?> taskId = ((TaskInternal) task).getTaskIdentity();
        ProjectIdentity taskProjectIdentity = taskId.getProjectIdentity();

        boolean isParentProject = taskProjectIdentity.getBuildTreePath()
            .startsWith(relativeProjectIdentity.getBuildTreePath());

        return isParentProject
            ? relativeProjectIdentity.getProjectPath().relativePath(taskId.getPath())
            : taskId.getPath();
    }

    /**
     * Determines which group this task should be displayed under in the report.
     */
    private static String getGroupFor(Task task) {
        String group = task.getGroup();
        if (group != null && !group.isEmpty()) {
            return group;
        }

        return TaskReportModel.DEFAULT_GROUP;
    }

    @Inject
    protected abstract ProjectTaskLister getProjectTaskLister();

}
