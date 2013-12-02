/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.Delete
import org.gradle.ide.visualstudio.internal.VisualStudioProject
import org.gradle.ide.visualstudio.internal.VisualStudioProjectRegistry
import org.gradle.ide.visualstudio.internal.VisualStudioSolution
import org.gradle.ide.visualstudio.internal.VisualStudioSolutionBuilder
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask
import org.gradle.nativebinaries.FlavorContainer
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.NativeComponent
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin

@Incubating
class VisualStudioPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesModelPlugin)

        // TODO:DAZ Use a model rule
        project.afterEvaluate {
            final flavors = project.modelRegistry.get("flavors", FlavorContainer)
            VisualStudioProjectRegistry projectRegistry = new VisualStudioProjectRegistry(project, flavors);
            VisualStudioSolutionBuilder solutionBuilder = new VisualStudioSolutionBuilder(project, projectRegistry);

            project.binaries.all { NativeBinary binary ->
                projectRegistry.addProjectConfiguration(binary)
            }

            project.executables.all { NativeComponent component ->
                createVisualStudioSolution(solutionBuilder, component, project)
            }

            project.libraries.all { NativeComponent component ->
                createVisualStudioSolution(solutionBuilder, component, project)
            }

            // TODO:DAZ For now, all vs project files are created within this project:
            // this will change so that we have more of a global vsproject registry and the 'owning' gradle project is responsible for building
            projectRegistry.allProjects.each { vsProject ->
                vsProject.builtBy addProjectsFileTask(vsProject)
                vsProject.builtBy addFiltersFileTask(vsProject)
            }
        }

        project.task("cleanVisualStudio", type: Delete) {
            delete "visualStudio"
        }
    }

    private void createVisualStudioSolution(VisualStudioSolutionBuilder builder, NativeComponent component, Project project) {
        def rootBinary = chooseDevelopmentVariant(component)
        if (rootBinary != null) {
            final solution = createVisualStudioSolution(builder, rootBinary, project)
            project.task("${component.name}VisualStudio").dependsOn solution
        }
    }

    // TODO:DAZ This should be a service, and should allow user to override default
    // TODO:DAZ Should probably choose VisualC++ variant...
    private NativeBinaryInternal chooseDevelopmentVariant(NativeComponent component) {
        component.binaries.find {
            it.buildable
        } as NativeBinaryInternal
    }

    private VisualStudioSolution createVisualStudioSolution(VisualStudioSolutionBuilder builder, NativeBinaryInternal developmentBinary, Project project) {
        VisualStudioSolution solution = builder.createSolution(developmentBinary)
        solution.lifecycleTask = project.task("${solution.name}VisualStudio")
        solution.builtBy createSolutionTask(project, solution)
        return solution
    }

    private createSolutionTask(Project project, VisualStudioSolution solution) {
        project.task("${solution.name}VisualStudioSolution", type: GenerateSolutionFileTask) {
            visualStudioSolution = solution
        }
    }

    private addProjectsFileTask(VisualStudioProject vsProject) {
        vsProject.project.task("${vsProject.name}VisualStudioProject", type: GenerateProjectFileTask) {
            visualStudioProject = vsProject
        }
    }

    private addFiltersFileTask(VisualStudioProject vsProject) {
        vsProject.project.task("${vsProject.name}VisualStudioFilters", type: GenerateFiltersFileTask) {
            visualStudioProject = vsProject
        }
    }
}

