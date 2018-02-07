/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.plugins;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.VisualStudioRootExtension;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.internal.NativeSpecVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioSolutionInternal;
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinaryContainer;

class VisualStudioPluginRules extends RuleSource {
    @Model
    public static VisualStudioExtensionInternal visualStudio(ExtensionContainer extensionContainer) {
        return (VisualStudioExtensionInternal) extensionContainer.getByType(VisualStudioExtension.class);
    }

    @Mutate
    public static void includeBuildFileInProject(VisualStudioExtensionInternal visualStudio, final ProjectIdentifier projectIdentifier) {
        visualStudio.getProjects().all(new Action<VisualStudioProject>() {
            public void execute(VisualStudioProject project) {
                if (projectIdentifier.getBuildFile() != null) {
                    ((DefaultVisualStudioProject) project).addSourceFile(projectIdentifier.getBuildFile());
                }
            }
        });
    }

    @Mutate
    public static void createVisualStudioModelForBinaries(VisualStudioExtensionInternal visualStudioExtension, BinaryContainer binaries, ProjectIdentifier projectIdentifier, ServiceRegistry serviceRegistry) {
        for (NativeBinarySpec binary : binaries.withType(NativeBinarySpec.class)) {
            visualStudioExtension.getProjectRegistry().addProjectConfiguration(new NativeSpecVisualStudioTargetBinary(binary));
        }

        if (isRoot(projectIdentifier)) {
            ensureSubprojectsAreRealized(projectIdentifier, serviceRegistry);
        }
    }

    @Mutate
    public static void createTasksForVisualStudio(TaskContainer tasks, VisualStudioExtensionInternal visualStudioExtension, ProjectIdentifier projectIdentifier) {
        for (VisualStudioProject vsProject : visualStudioExtension.getProjects()) {
            ((VisualStudioProjectInternal)vsProject).builtBy(createProjectsFileTask(tasks, vsProject), createFiltersFileTask(tasks, vsProject));

            Task lifecycleTask = tasks.maybeCreate(((VisualStudioProjectInternal) vsProject).getComponentName() + "VisualStudio");
            lifecycleTask.dependsOn(vsProject);
        }

        if (isRoot(projectIdentifier)) {
            VisualStudioRootExtension rootExtension = (VisualStudioRootExtension) visualStudioExtension;
            VisualStudioSolutionInternal vsSolution = (VisualStudioSolutionInternal) rootExtension.getSolution();

            vsSolution.builtBy(createSolutionTask(tasks, vsSolution));
        }

        addCleanTask(tasks);
    }

    // This ensures that subprojects are realized and register their project and project configuration IDE artifacts
    private static void ensureSubprojectsAreRealized(ProjectIdentifier projectIdentifier, ServiceRegistry serviceRegistry) {
        ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
        ProjectRegistry<ProjectInternal> projectRegistry = Cast.uncheckedCast(serviceRegistry.get(ProjectRegistry.class));

        for (ProjectInternal subproject : projectRegistry.getSubProjects(projectIdentifier.getPath())) {
            projectModelResolver.resolveProjectModel(subproject.getPath()).find("visualStudio", VisualStudioExtension.class);
        }
    }

    private static boolean isRoot(ProjectIdentifier projectIdentifier) {
        return projectIdentifier.getParentIdentifier() == null;
    }

    private static void addCleanTask(TaskContainer tasks) {
        Delete cleanTask = tasks.maybeCreate("cleanVisualStudio", Delete.class);
        for (Task task : tasks.withType(GenerateSolutionFileTask.class)) {
            cleanTask.delete(task.getOutputs().getFiles());
        }
        for (Task task : tasks.withType(GenerateFiltersFileTask.class)) {
            cleanTask.delete(task.getOutputs().getFiles());
        }
        for (Task task : tasks.withType(GenerateProjectFileTask.class)) {
            cleanTask.delete(task.getOutputs().getFiles());
        }
        cleanTask.setGroup("IDE");
        cleanTask.setDescription("Removes all generated Visual Studio project and solution files");
    }

    private static Task createSolutionTask(TaskContainer tasks, VisualStudioSolution solution) {
        GenerateSolutionFileTask solutionFileTask = tasks.create(solution.getName() + "VisualStudioSolution", GenerateSolutionFileTask.class);
        solutionFileTask.setVisualStudioSolution(solution);
        return solutionFileTask;
    }

    private static Task createProjectsFileTask(TaskContainer tasks, VisualStudioProject vsProject) {
        GenerateProjectFileTask task = tasks.create(vsProject.getName() + "VisualStudioProject", GenerateProjectFileTask.class);
        task.setVisualStudioProject(vsProject);
        task.initGradleCommand();
        return task;
    }

    private static Task createFiltersFileTask(TaskContainer tasks, VisualStudioProject vsProject) {
        GenerateFiltersFileTask task = tasks.create(vsProject.getName() + "VisualStudioFilters", GenerateFiltersFileTask.class);
        task.setVisualStudioProject(vsProject);
        return task;
    }
}
