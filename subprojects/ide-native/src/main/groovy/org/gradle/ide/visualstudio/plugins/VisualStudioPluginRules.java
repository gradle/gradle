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
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.internal.NativeSpecVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioSolutionInternal;
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask;
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
    public static void createVisualStudioModelForBinaries(VisualStudioExtensionInternal visualStudioExtension, BinaryContainer binaries) {
        for (NativeBinarySpec binary : binaries.withType(NativeBinarySpec.class)) {
            VisualStudioProjectConfiguration configuration = visualStudioExtension.getProjectRegistry().addProjectConfiguration(new NativeSpecVisualStudioTargetBinary(binary));

            // Only create a solution if one of the binaries is buildable
            if (binary.isBuildable()) {
                DefaultVisualStudioProject visualStudioProject = configuration.getProject();
                visualStudioExtension.getSolutionRegistry().addSolution(visualStudioProject);
            }
        }
    }

    @Mutate
    public static void createTasksForVisualStudio(TaskContainer tasks, VisualStudioExtensionInternal visualStudioExtension) {
        for (VisualStudioProject vsProject : visualStudioExtension.getProjects()) {
            ((VisualStudioProjectInternal)vsProject).builtBy(createProjectsFileTask(tasks, vsProject), createFiltersFileTask(tasks, vsProject));
        }

        for (VisualStudioSolution solution : visualStudioExtension.getSolutions()) {
            VisualStudioSolutionInternal vsSolution = (VisualStudioSolutionInternal) solution;

            Task solutionTask = tasks.create(vsSolution.getName() + "VisualStudio");
            solutionTask.setDescription("Generates the '" + vsSolution.getName() + "' Visual Studio solution file.");
            solutionTask.dependsOn(createSolutionTask(tasks, vsSolution));
            vsSolution.builtBy(solutionTask);

            // Lifecycle task for component
            final Task lifecycleTask = tasks.maybeCreate(vsSolution.getComponentName() + "VisualStudio");
            lifecycleTask.dependsOn(vsSolution);
            lifecycleTask.setGroup("IDE");
            lifecycleTask.setDescription("Generates the Visual Studio solution for " + vsSolution.getName() + ".");
        }

        addCleanTask(tasks);
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
