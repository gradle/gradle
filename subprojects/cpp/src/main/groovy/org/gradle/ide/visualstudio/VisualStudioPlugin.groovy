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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.tasks.Delete
import org.gradle.ide.cdt.tasks.GenerateMetadataFileTask
import org.gradle.ide.visualstudio.model.*
import org.gradle.nativebinaries.Library
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin

class VisualStudioPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply(NativeBinariesModelPlugin)

        final solution = new VisualStudioSolution()

        final cleanTask = project.task("cleanVisualStudio", type: Delete)
        final lifecycleTask = project.task("visualStudio")

        project.executables.all {
            final exeProject = new VisualStudioExeProject(it)
            solution.exeProjects << exeProject

            attachToLifecycle projectsFileTask(project, exeProject, solution), lifecycleTask, cleanTask
            attachToLifecycle filtersFileTask(project, exeProject), lifecycleTask, cleanTask
        }
        project.libraries.all {
            final libProject = new VisualStudioLibraryProject(it)
            solution.libraryProjects << libProject

            attachToLifecycle projectsFileTask(project, libProject, solution), lifecycleTask, cleanTask
            attachToLifecycle filtersFileTask(project, libProject), lifecycleTask, cleanTask
        }
        attachToLifecycle solutionFileTask(project, solution), lifecycleTask, cleanTask
    }

    private void attachToLifecycle(Task solutionTask, Task lifecycleTask, Task cleanTask) {
        cleanTask.delete solutionTask.outputs.files
        lifecycleTask.dependsOn solutionTask
    }

    private solutionFileTask(Project project, VisualStudioSolution solution) {
        def taskName = "generateSolutionFile"
        project.task("generateSolutionFile", type:GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, project.name + ".sln")
            factory { new SolutionFile() }
            onConfigure { SolutionFile solutionFile ->
                solutionFile.uuid = solution.uuid
                solution.exeProjects.each {
                    solutionFile.addProject(it)
                }
                solution.libraryProjects.each {
                    solutionFile.addProject(it)
                }
            }
        }
    }

    private projectsFileTask(Project project, VisualStudioProject vsProject, VisualStudioSolution solution) {
        def taskName = "generateProjectFileFor${vsProject.name}"
        project.task(taskName, type: GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, vsProject.projectFile)
            factory { new ProjectFile(new ProjectRelativeFileTransformer(project)) }
            onConfigure { ProjectFile projectFile ->
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

                vsProject.libraryDependencies.each { Library lib ->
                    def dependencyProject = solution.libraryProjects.find {it.component == lib}
                    projectFile.addProjectReference(dependencyProject)
                }
            }
        }
    }

    private filtersFileTask(Project project, VisualStudioProject vsProject) {
        def taskName = "generateFiltersFileFor${vsProject.name}"
        project.task(taskName, type: GenerateMetadataFileTask) {
            inputFile = new File("not a file")
            outputFile = configFile(project, vsProject.filtersFile)
            factory { new FiltersFile(new ProjectRelativeFileTransformer(project)) }
            onConfigure { FiltersFile filtersFile ->
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
        return project.file(fileName)
    }

    private static class ProjectRelativeFileTransformer implements Transformer<String, File> {
        private final Project project

        ProjectRelativeFileTransformer(Project project) {
            this.project = project
        }

        String transform(File file) {
            return project.projectDir.toURI().relativize(file.toURI()).getPath()
        }
    }

}

