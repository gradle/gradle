/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.reporting.components.ComponentReport;
import org.gradle.api.reporting.dependents.DependentComponentsReport;
import org.gradle.api.reporting.model.ModelReport;
import org.gradle.api.tasks.diagnostics.*;
import org.gradle.configuration.Help;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Defaults;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;

import java.util.concurrent.Callable;

/**
 * Adds various reporting tasks that provide information about the project.
 */
@Incubating
public class HelpTasksPlugin implements Plugin<ProjectInternal> {

    public static final String HELP_GROUP = "help";
    public static final String PROPERTIES_TASK = "properties";
    public static final String DEPENDENCIES_TASK = "dependencies";
    public static final String DEPENDENCY_INSIGHT_TASK = "dependencyInsight";
    public static final String COMPONENTS_TASK = "components";
    public static final String MODEL_TASK = "model";
    public static final String DEPENDENT_COMPONENTS_TASK = "dependentComponents";

    @Override
    public void apply(final ProjectInternal project) {
        final TaskContainerInternal tasks = project.getTasks();

        // static classes are used for the actions to avoid implicitly dragging project/tasks into the model registry
        String projectName = project.toString();
        tasks.addPlaceholderAction(ProjectInternal.HELP_TASK, Help.class, new HelpAction());
        tasks.addPlaceholderAction(ProjectInternal.PROJECTS_TASK, ProjectReportTask.class, new ProjectReportTaskAction(projectName));
        tasks.addPlaceholderAction(ProjectInternal.TASKS_TASK, TaskReportTask.class, new TaskReportTaskAction(projectName, project.getChildProjects().isEmpty()));
        tasks.addPlaceholderAction(PROPERTIES_TASK, PropertyReportTask.class, new PropertyReportTaskAction(projectName));
        tasks.addPlaceholderAction(DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask.class, new DependencyInsightReportTaskAction(projectName));
        tasks.addPlaceholderAction(DEPENDENCIES_TASK, DependencyReportTask.class, new DependencyReportTaskAction(projectName));
        tasks.addPlaceholderAction(BuildEnvironmentReportTask.TASK_NAME, BuildEnvironmentReportTask.class, new BuildEnvironmentReportTaskAction(projectName));
        tasks.addPlaceholderAction(COMPONENTS_TASK, ComponentReport.class, new ComponentReportAction(projectName));
        tasks.addPlaceholderAction(MODEL_TASK, ModelReport.class, new ModelReportAction(projectName));
        tasks.addPlaceholderAction(DEPENDENT_COMPONENTS_TASK, DependentComponentsReport.class, new DependentComponentsReportAction(projectName));
    }

    static class Rules extends RuleSource {
        @Defaults
        void addDefaultDependenciesReportConfiguration(@Path("tasks.dependencyInsight") DependencyInsightReportTask task, final ServiceRegistry services) {
            new DslObject(task).getConventionMapping().map("configuration", new Callable<Object>() {
                public Object call() {
                    BuildableJavaComponent javaProject = services.get(ComponentRegistry.class).getMainComponent();
                    return javaProject == null ? null : javaProject.getCompileDependencies();
                }
            });
        }
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

    private static class DependencyInsightReportTaskAction implements Action<DependencyInsightReportTask> {
        private final String projectName;

        public DependencyInsightReportTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(final DependencyInsightReportTask task) {
            task.setDescription("Displays the insight into a specific dependency in " + projectName + ".");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
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
            task.setGroup(HELP_GROUP);
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
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class ComponentReportAction implements Action<ComponentReport> {
        private final String projectName;

        public ComponentReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(ComponentReport task) {
            task.setDescription("Displays the components produced by " + projectName + ". [incubating]");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class ModelReportAction implements Action<ModelReport> {
        private final String projectName;

        public ModelReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(ModelReport task) {
            task.setDescription("Displays the configuration model of " + projectName + ". [incubating]");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    private static class DependentComponentsReportAction implements Action<DependentComponentsReport> {
        private final String projectName;

        public DependentComponentsReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(DependentComponentsReport task) {
            task.setDescription("Displays the dependent components of components in " + projectName + ". [incubating]");
            task.setGroup(HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }
}
