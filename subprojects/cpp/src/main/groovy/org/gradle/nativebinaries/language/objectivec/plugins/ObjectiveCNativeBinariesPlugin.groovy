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
package org.gradle.nativebinaries.language.objectivec.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.objectivec.ObjectiveCSourceSet
import org.gradle.language.objectivec.plugins.ObjectiveCLangPlugin
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.objectivec.tasks.ObjectiveCCompile
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
/**
 * A plugin for projects wishing to build native binary components from Objective-C sources.
 *
 * <p>Automatically includes the {@link org.gradle.language.objectivec.plugins.ObjectiveCLangPlugin} for core Objective-C support and the {@link NativeBinariesPlugin} for native binary support.</p>
 *
 * <li>Creates a {@link ObjectiveCCompile} task for each {@link ObjectiveCSourceSet} to compile the Objective-C sources.</li>
 */
@Incubating
class ObjectiveCNativeBinariesPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(ObjectiveCLangPlugin)

        project.executables.all { Executable executable ->
            executable.binaries.all { binary ->
                binary.extensions.create("objcCompiler", DefaultPreprocessingTool)
            }
        }

        project.libraries.all { Library library ->
            library.binaries.all { binary ->
                binary.extensions.create("objcCompiler", DefaultPreprocessingTool)
            }
        }

        project.binaries.withType(ProjectNativeBinary) { ProjectNativeBinary binary ->
            binary.source.withType(ObjectiveCSourceSet).all { ObjectiveCSourceSet sourceSet ->
                if (sourceSet.mayHaveSources) {
                    def compileTask = createCompileTask(project, binary, sourceSet)
                    binary.tasks.add compileTask
                    binary.tasks.builder.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
                }
            }
        }
    }

    private def createCompileTask(ProjectInternal project, ProjectNativeBinaryInternal binary, ObjectiveCSourceSet sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: ObjectiveCCompile) {
            description = "Compiles the $sourceSet of $binary"
        }

        compileTask.toolChain = binary.toolChain
        compileTask.targetPlatform = binary.targetPlatform
        compileTask.positionIndependentCode = binary instanceof SharedLibraryBinary

        compileTask.includes {
            sourceSet.exportedHeaders.srcDirs
        }
        compileTask.includes {
            binary.getLibs(sourceSet)*.includeRoots
        }

        compileTask.source sourceSet.source

        compileTask.objectFileDir = project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}")
        compileTask.macros = binary.objcCompiler.macros
        compileTask.compilerArgs = binary.objcCompiler.args

        compileTask
    }
}