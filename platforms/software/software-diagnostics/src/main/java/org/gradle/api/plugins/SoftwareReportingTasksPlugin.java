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
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Category;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.diagnostics.ArtifactTransformsReportTask;
import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask;
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.GenerateRepositoriesReportDataTask;
import org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask;
import org.gradle.api.tasks.diagnostics.RepositoriesReportTask;
import org.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.api.tasks.diagnostics.internal.repositories.attributes.DiagnosticAttributes;
import org.gradle.api.tasks.diagnostics.internal.repositories.attributes.DiagnosticType;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds various reporting tasks that provide information about a software project, such as information about
 * configurations and dependency reporting tasks that make use of dependency management.
 *
 * @since 8.13
 */
@Incubating
@NullMarked
public abstract class SoftwareReportingTasksPlugin implements Plugin<Project> {
    @Override
    @SuppressWarnings("deprecation") // Configuration.setVisible(boolean) is deprecated; we use it to hide diagnostic configs from standard reports.
    public void apply(Project project) {
        final TaskContainer tasks = project.getTasks();

        // static classes are used for the actions to avoid implicitly dragging project/tasks into the model registry
        String projectName = project.toString();

        tasks.register(HelpTasksPlugin.DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask.class, new DependencyInsightReportTaskAction(projectName));
        tasks.register(HelpTasksPlugin.DEPENDENCIES_TASK, DependencyReportTask.class, new DependencyReportTaskAction(projectName));
        tasks.register(BuildEnvironmentReportTask.TASK_NAME, BuildEnvironmentReportTask.class, new BuildEnvironmentReportTaskAction(projectName));

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
        // Producer half: registered on every project.
        TaskProvider<GenerateRepositoriesReportDataTask> generateTask =
            tasks.register("generateRepositoriesReportData", GenerateRepositoriesReportDataTask.class, task -> {
                task.setDescription("Captures this project's repository declarations as JSON for consumption by the repositories report.");
                // Intentionally no group — keep this internal-feeling task out of the help group.
                task.getOutputFile().convention(
                    project.getLayout().getBuildDirectory().file("diagnostics/repositories/repositories-report-data.json"));
            });

        ObjectFactory objectFactory = project.getObjects();
        project.getConfigurations().consumable("repositoriesReportElements", config -> {
            config.setDescription("Repository data published for the repositories report.");
            config.setVisible(false);
            config.attributes(attrs -> {
                attrs.attribute(Category.CATEGORY_ATTRIBUTE,
                    objectFactory.named(Category.class, DiagnosticAttributes.DIAGNOSTICS_CATEGORY));
                attrs.attribute(DiagnosticType.DIAGNOSTIC_TYPE_ATTRIBUTE,
                    objectFactory.named(DiagnosticType.class, DiagnosticType.REPOSITORIES));
            });
            config.getOutgoing().artifact(generateTask.flatMap(GenerateRepositoriesReportDataTask::getOutputFile));
        });

        // Consumer half: only on the root project.
        if (project == project.getRootProject()) {
            // Dependency-scope configuration holds the project dependencies (resolvable role disallows direct declarations).
            project.getConfigurations().dependencyScope("repositoriesDataDependencies", config -> {
                config.setDescription("Holds per-project dependencies whose `repositoriesReportElements` outputs feed the repositories report.");
                config.setVisible(false);
            });
            project.getConfigurations().resolvable("repositoriesData", config -> {
                config.setDescription("Resolves per-project repository data for the repositories report.");
                config.setVisible(false);
                config.extendsFrom(project.getConfigurations().getByName("repositoriesDataDependencies"));
                config.attributes(attrs -> {
                    attrs.attribute(Category.CATEGORY_ATTRIBUTE,
                        objectFactory.named(Category.class, DiagnosticAttributes.DIAGNOSTICS_CATEGORY));
                    attrs.attribute(DiagnosticType.DIAGNOSTIC_TYPE_ATTRIBUTE,
                        objectFactory.named(DiagnosticType.class, DiagnosticType.REPOSITORIES));
                });
            });

            // Enumerate every project from Settings (build-wide, IP-safe) and add a project dependency.
            // Settings may be unavailable in unit-test contexts (e.g. `ProjectBuilder`) where the build
            // lifecycle hasn't reached the point of having a Settings object. In that case the resolvable
            // Configuration simply has no dependencies and the report would be empty — graceful degradation
            // is acceptable since real builds always have Settings available when plugins are applied.
            try {
                SettingsInternal settings = ((ProjectInternal) project).getGradle().getSettings();
                DependencyHandler dependencies = project.getDependencies();
                addAllProjectsAsDependencies(settings.getRootProject(), dependencies);
            } catch (IllegalStateException ignored) {
                // Settings unavailable; skip project enumeration.
            }

            tasks.register(HelpTasksPlugin.REPOSITORIES_TASK, RepositoriesReportTask.class, task -> {
                task.setDescription("Displays the repositories available for dependency resolution.");
                task.setGroup(HelpTasksPlugin.HELP_GROUP);
                task.setImpliesSubProjects(true);
                // Wire the resolvable Configuration's resolved files as the data source.
                // Note: `getRepositoriesData()` is `@Internal` (not `@InputFiles`) — see RepositoriesReportTask
                // Javadoc for why. Task ordering is established via `dependsOn(configuration)` so the
                // producer tasks still run before this consumer task.
                task.dependsOn(project.getConfigurations().named("repositoriesData"));
                task.getRepositoriesData().from(
                    project.getConfigurations().named("repositoriesData")
                        .map(c -> c.getIncoming().getFiles()));
            });
        }
        tasks.withType(TaskReportTask.class).configureEach(task -> {
            task.getShowTypes().convention(false);
            task.getShowProvenance().convention(false);
        });
    }

    private static void addAllProjectsAsDependencies(ProjectDescriptor descriptor, DependencyHandler dependencies) {
        Map<String, String> notation = new LinkedHashMap<>();
        notation.put("path", descriptor.getPath());
        dependencies.add("repositoriesDataDependencies", dependencies.project(notation));
        for (ProjectDescriptor child : descriptor.getChildren()) {
            addAllProjectsAsDependencies(child, dependencies);
        }
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
}
