/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.diagnostics.ProjectReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.api.tasks.diagnostics.internal.DiagnosticsTaskNames;
import org.gradle.configuration.Help;

import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

/**
 * Adds various reporting tasks that provide information about the project.
 *
 * @see <a href="https://gradle.org/help/">Getting additional help with Gradle</a>
 */
public abstract class HelpTasksPlugin implements Plugin<Project> {
    public static final String HELP_GROUP = DiagnosticsTaskNames.HELP_GROUP;
    public static final String PROPERTIES_TASK = DiagnosticsTaskNames.PROPERTIES_TASK;
    public static final String DEPENDENCIES_TASK = DiagnosticsTaskNames.DEPENDENCIES_TASK;
    public static final String DEPENDENCY_INSIGHT_TASK = DiagnosticsTaskNames.DEPENDENCY_INSIGHT_TASK;
    public static final String COMPONENTS_TASK = DiagnosticsTaskNames.COMPONENTS_TASK;

    /**
     * The name of the outgoing variants report task.
     *
     * @since 6.0
     */
    public static final String OUTGOING_VARIANTS_TASK = DiagnosticsTaskNames.OUTGOING_VARIANTS_TASK;

    /**
     * The name of the requested configurations report task.
     *
     * @since 7.5
     */
    @Incubating
    public static final String RESOLVABLE_CONFIGURATIONS_TASK = DiagnosticsTaskNames.RESOLVABLE_CONFIGURATIONS_TASK;

    /**
     * The name of the Artifact Transforms report task.
     *
     * @since 8.13
     */
    @Incubating
    public static final String ARTIFACT_TRANSFORMS_TASK = DiagnosticsTaskNames.ARTIFACT_TRANSFORMS_TASK;

    public static final String MODEL_TASK = DiagnosticsTaskNames.MODEL_TASK;
    public static final String DEPENDENT_COMPONENTS_TASK = DiagnosticsTaskNames.DEPENDENT_COMPONENTS_TASK;

    @Override
    public void apply(final Project project) {
        final TaskContainer tasks = project.getTasks();

        // static classes are used for the actions to avoid implicitly dragging project/tasks into the model registry
        String projectName = project.toString();
        tasks.register(ProjectInternal.HELP_TASK, Help.class, new HelpAction());
        tasks.register(ProjectInternal.PROJECTS_TASK, ProjectReportTask.class, new ProjectReportTaskAction(projectName));
        tasks.register(ProjectInternal.TASKS_TASK, TaskReportTask.class, new TaskReportTaskAction(projectName, getChildProjectsForInternalUse(project).isEmpty()));
        tasks.register(PROPERTIES_TASK, PropertyReportTask.class, new PropertyReportTaskAction(projectName));

        registerDeprecatedSoftwareModelTasks(tasks, projectName);

        tasks.withType(TaskReportTask.class).configureEach(task -> {
            task.getShowTypes().convention(false);
        });
    }

    /**
     * Registers the deprecated tasks used by the Software Model.
     * <p>
     * Note that these tasks <strong>are</strong> deprecated, even though they do not emit a deprecation warning when run.
     */
    @SuppressWarnings("deprecation")
    private void registerDeprecatedSoftwareModelTasks(TaskContainer tasks, String projectName) {
        tasks.register(MODEL_TASK, org.gradle.api.reporting.model.ModelReport.class, new ModelReportAction(projectName));
    }

    private static class HelpAction implements Action<Help> {
        @Override
        public void execute(Help task) {
            task.setDescription("Displays a help message.");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class ProjectReportTaskAction implements Action<ProjectReportTask> {
        private final String project;

        public ProjectReportTaskAction(String projectName) {
            this.project = projectName;
        }

        @Override
        public void execute(ProjectReportTask task) {
            task.setDescription("Displays the sub-projects of " + project + ".");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class TaskReportTaskAction implements Action<TaskReportTask> {
        private final String projectName;
        private final boolean noChildren;

        public TaskReportTaskAction(String projectName, boolean noChildren) {
            this.projectName = projectName;
            this.noChildren = noChildren;
        }

        @Override
        public void execute(TaskReportTask task) {
            String description;
            if (noChildren) {
                description = "Displays the tasks runnable from " + projectName + ".";
            } else {
                description = "Displays the tasks runnable from " + projectName + " (some of the displayed tasks may belong to subprojects).";
            }
            task.setDescription(description);
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class PropertyReportTaskAction implements Action<PropertyReportTask> {
        private final String projectName;

        public PropertyReportTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(PropertyReportTask task) {
            task.setDescription("Displays the properties of " + projectName + ".");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    @SuppressWarnings("deprecation")
    private static class ModelReportAction implements Action<org.gradle.api.reporting.model.ModelReport> {
        private final String projectName;

        public ModelReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(org.gradle.api.reporting.model.ModelReport task) {
            task.setDescription("Displays the configuration model of " + projectName + ". [deprecated]");
            task.setImpliesSubProjects(true);
        }
    }
}
