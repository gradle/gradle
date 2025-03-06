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

import org.apache.groovy.lang.annotation.Incubating;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.diagnostics.ArtifactTransformsReportTask;
import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask;
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask;
import org.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;

import javax.annotation.Nonnull;

/**
 * Adds various reporting tasks that provide information about a software project, such as information about
 * configurations and dependency reporting tasks that make use of dependency management.
 *
 * @since 8.13
 */
@Incubating
@Nonnull
public abstract class SoftwareReportingTasksPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        final TaskContainer tasks = project.getTasks();

        // static classes are used for the actions to avoid implicitly dragging project/tasks into the model registry
        String projectName = project.toString();

        tasks.register(HelpTasksPlugin.DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask.class, new DependencyInsightReportTaskAction(projectName));
        tasks.register(HelpTasksPlugin.DEPENDENCIES_TASK, DependencyReportTask.class, new DependencyReportTaskAction(projectName));
        tasks.register(BuildEnvironmentReportTask.TASK_NAME, BuildEnvironmentReportTask.class, new BuildEnvironmentReportTaskAction(projectName));
        registerDeprecatedSoftwareModelTasks(tasks, projectName);
        tasks.register(HelpTasksPlugin.OUTGOING_VARIANTS_TASK, OutgoingVariantsReportTask.class, task -> {
            task.setDescription("Displays the outgoing variants of " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
            task.getShowAll().convention(false);
        });
        tasks.register(HelpTasksPlugin.RESOLVABLE_CONFIGURATIONS_TASK, ResolvableConfigurationsReportTask.class, task -> {
            task.setDescription("Displays the configurations that can be resolved in " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
            task.getShowAll().convention(false);
            task.getRecursive().convention(false);
        });
        tasks.register(HelpTasksPlugin.ARTIFACT_TRANSFORMS_TASK, ArtifactTransformsReportTask.class, task -> {
            task.setDescription("Displays the Artifact Transforms that can be executed in " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
        });
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
        tasks.register(HelpTasksPlugin.COMPONENTS_TASK, org.gradle.api.reporting.components.ComponentReport.class, new ComponentReportAction(projectName));
        tasks.register(HelpTasksPlugin.DEPENDENT_COMPONENTS_TASK, org.gradle.api.reporting.dependents.DependentComponentsReport.class, new DependentComponentsReportAction(projectName));
    }

    private static class DependencyInsightReportTaskAction implements Action<DependencyInsightReportTask> {
        private final String projectName;

        public DependencyInsightReportTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(final DependencyInsightReportTask task) {
            task.setDescription("Displays the insight into a specific dependency in " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
            task.getShowingAllVariants().convention(false);
        }
    }

    private static class DependencyReportTaskAction implements Action<DependencyReportTask> {
        private final String projectName;

        public DependencyReportTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(DependencyReportTask task) {
            task.setDescription("Displays all dependencies declared in " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class BuildEnvironmentReportTaskAction implements Action<BuildEnvironmentReportTask> {
        private final String projectName;

        public BuildEnvironmentReportTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(BuildEnvironmentReportTask task) {
            task.setDescription("Displays all buildscript dependencies declared in " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    @SuppressWarnings("deprecation")
    private static class ComponentReportAction implements Action<org.gradle.api.reporting.components.ComponentReport> {
        private final String projectName;

        public ComponentReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(org.gradle.api.reporting.components.ComponentReport task) {
            task.setDescription("Displays the components produced by " + projectName + ". [deprecated]");
            task.setImpliesSubProjects(true);
        }
    }

    @SuppressWarnings("deprecation")
    private static class DependentComponentsReportAction implements Action<org.gradle.api.reporting.dependents.DependentComponentsReport> {
        private final String projectName;

        public DependentComponentsReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(org.gradle.api.reporting.dependents.DependentComponentsReport task) {
            task.setDescription("Displays the dependent components of components in " + projectName + ". [deprecated]");
            task.setImpliesSubProjects(true);
        }
    }
}
