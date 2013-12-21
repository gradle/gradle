/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.ide.cdt

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Delete

import org.gradle.ide.cdt.model.ProjectSettings
import org.gradle.ide.cdt.model.ProjectDescriptor
import org.gradle.ide.cdt.model.CprojectSettings
import org.gradle.ide.cdt.model.CprojectDescriptor

import org.gradle.ide.cdt.tasks.GenerateMetadataFileTask

@Incubating
class CdtIdePlugin implements Plugin<Project> {

    void apply(Project project) {
        project.apply(plugin: "native-binaries")
        def metadataFileTasks = [addCreateProjectDescriptor(project), addCreateCprojectDescriptor(project)]

        project.task("cleanCdt", type: Delete) {
            delete metadataFileTasks*.outputs*.files
        }

        project.task("cdt", dependsOn: metadataFileTasks)
    }

    private addCreateProjectDescriptor(Project project) {
        project.task("cdtProject", type: GenerateMetadataFileTask) {
            inputFile = project.file(".project")
            outputFile = project.file(".project")
            factory { new ProjectDescriptor() }
            onConfigure { new ProjectSettings(name: project.name).applyTo(it) }
        }
    }

    private addCreateCprojectDescriptor(Project project) {
        project.task("cdtCproject", type: GenerateMetadataFileTask) { task ->
            
            [project.executables, project.libraries]*.all { binary ->
                if (binary.name == "main") {
                    task.settings = new CprojectSettings(binary, project)
                }
            }
            
            doFirst {
                if (task.settings == null) {
                    throw new InvalidUserDataException("There is neither a main binary or library")
                }
            }
            
            inputs.files { task.settings.includeRoots }
            inputFile = project.file(".cproject")
            outputFile = project.file(".cproject")
            factory { new CprojectDescriptor() }
            onConfigure { descriptor ->
                task.settings.applyTo(descriptor)
            }
        }
    }

}