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
import org.gradle.api.Transformer
import org.gradle.api.tasks.Delete
import org.gradle.ide.cdt.tasks.GenerateMetadataFileTask
import org.gradle.ide.visualstudio.internal.*
import org.gradle.nativebinaries.NativeComponent
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin

@Incubating
class VisualStudioPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply(NativeBinariesModelPlugin)

        // TODO:DAZ Use a model rule
        project.afterEvaluate {
            VisualStudioProjectRegistry projectRegistry = new VisualStudioProjectRegistry();
            VisualStudioSolutionBuilder solutionBuilder = new VisualStudioSolutionBuilder(projectRegistry);

            project.executables.all { NativeComponent component ->
                createVisualStudioSolution(solutionBuilder, component, project)
            }

            project.libraries.all { NativeComponent component ->
                createVisualStudioSolution(solutionBuilder, component, project)
            }

            // TODO:DAZ For now, all vs project files are created within this project:
            // this will change so that we have more of a global vsproject registry and the 'owning' gradle project is responsible for building
            projectRegistry.allProjects.each { vsProject ->
                vsProject.builtBy addProjectsFileTask(project, vsProject, projectRegistry)
                vsProject.builtBy addFiltersFileTask(project, vsProject)
            }
        }

        project.task("cleanVisualStudio", type: Delete) {
            delete "visualStudio"
        }
    }

    private void createVisualStudioSolution(VisualStudioSolutionBuilder builder, NativeComponent component, Project project) {
        def rootBinary = chooseDevelopmentVariant(component)
        if (rootBinary != null) {
            createVisualStudioSolution(builder, rootBinary, project)
        }
    }

    // TODO:DAZ This should be a service, and should allow user to override default
    // TODO:DAZ Should probably choose VisualC++ variant...
    private NativeBinaryInternal chooseDevelopmentVariant(NativeComponent component) {
        component.binaries.find {
            it.buildable
        } as NativeBinaryInternal
    }

    private void createVisualStudioSolution(VisualStudioSolutionBuilder builder, NativeBinaryInternal developmentBinary, Project project) {
        VisualStudioSolution solution = builder.createSolution(developmentBinary)
        def solutionTask = project.task("${solution.name}VisualStudio")
        solution.lifecycleTask = solutionTask
        solution.builtBy createSolutionTask(project, solution)
    }

    private createSolutionTask(Project project, VisualStudioSolution solution) {
        String taskName = "${solution.name}VisualStudioSolution"
        project.task(taskName, type:GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, solution.solutionFile)
            factory { new VisualStudioSolutionFile() }
            onConfigure { VisualStudioSolutionFile solutionFile ->
                solution.projectConfigurations.each {
                    solutionFile.addProjectConfiguration(it)
                }
            }
        }
    }

    private addProjectsFileTask(Project project, VisualStudioProject vsProject, VisualStudioProjectRegistry vsProjectRegistry) {
        String taskName = "${vsProject.name}VisualStudioProject"
        project.task(taskName, type: GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, vsProject.projectFile)
            factory { new VisualStudioProjectFile(new ProjectRelativeFileTransformer(project)) }
            onConfigure { VisualStudioProjectFile projectFile ->
                projectFile.setProjectUuid(vsProject.uuid)
                vsProject.sourceFiles.each {
                    projectFile.addSourceFile(it)
                }
                vsProject.headerFiles.each {
                    projectFile.addHeaderFile(it)
                }

                vsProject.configurations.each {
                    projectFile.addConfiguration(it)
                }

                vsProject.projectReferences.each { projectKey ->
                    projectFile.addProjectReference(vsProjectRegistry.getProject(projectKey))
                }
            }
        }
    }

    private addFiltersFileTask(Project project, VisualStudioProject vsProject) {
        String taskName = "${vsProject.name}VisualStudioFilters"
        project.task(taskName, type: GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, vsProject.filtersFile)
            factory { new VisualStudioFiltersFile(new ProjectRelativeFileTransformer(project)) }
            onConfigure { VisualStudioFiltersFile filtersFile ->
                vsProject.sourceFiles.each {
                    filtersFile.addSource(it)
                }
                vsProject.headerFiles.each {
                    filtersFile.addHeader(it)
                }
            }
        }
    }

    private File configFile(Project project, String fileName) {
        // TODO:DAZ Allow the output directory to be configured
        return project.file("visualStudio/${fileName}")
    }

    // TODO:DAZ Decide if this is necessary and implement, or remove
    private static class ProjectRelativeFileTransformer implements Transformer<String, File> {
        private final Project project

        ProjectRelativeFileTransformer(Project project) {
            this.project = project
        }

        String transform(File file) {
            return file.absolutePath
        }
    }

}

