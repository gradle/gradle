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
package org.gradle.nativebinaries.language.objectivecpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.objectivecpp.ObjectiveCppSourceSet
import org.gradle.language.objectivecpp.plugins.ObjectiveCppLangPlugin
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.objectivecpp.tasks.ObjectiveCppCompile
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin

/**
 * A plugin for projects wishing to build native binary components from Objective-C++ sources.
 *
 * <p>Automatically includes the {@link org.gradle.language.objectivecpp.plugins.ObjectiveCppLangPlugin} for core Objective-C++ support and the {@link NativeBinariesPlugin} for native binary support.</p>
 *
 * <li>Creates a {@link ObjectiveCppCompile} task for each {@link ObjectiveCppSourceSet} to compile the Objective-C++ sources.</li>
 */
@Incubating
class ObjectiveCppNativeBinariesPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(ObjectiveCppLangPlugin)

        project.executables.all { Executable executable ->
            executable.binaries.all { binary ->
                binary.extensions.create("objcppCompiler", DefaultPreprocessingTool)
            }
        }

        project.libraries.all { Library library ->
            library.binaries.all { binary ->
                binary.extensions.create("objcppCompiler", DefaultPreprocessingTool)
            }
        }

        project.binaries.withType(NativeBinary) { ProjectNativeBinaryInternal binary ->
            binary.source.withType(ObjectiveCppSourceSet).all { ObjectiveCppSourceSet sourceSet ->
                if (sourceSet.mayHaveSources) {
                    def compileTask = createCompileTask(project, binary, sourceSet)
                    binary.tasks.add compileTask
                    binary.tasks.builder.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
                }
            }
        }
    }

    private def createCompileTask(ProjectInternal project, ProjectNativeBinaryInternal binary, ObjectiveCppSourceSet sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: ObjectiveCppCompile) {
            description = "Compiles the $sourceSet of $binary"
        }

        compileTask.toolChain = binary.toolChain
        compileTask.targetPlatform = binary.targetPlatform
        compileTask.positionIndependentCode = binary instanceof SharedLibraryBinary

        compileTask.includes {
            sourceSet.exportedHeaders.srcDirs
        }

        compileTask.source sourceSet.source
        binary.getLibs(sourceSet).each { deps ->
            compileTask.includes deps.includeRoots
        }

        compileTask.objectFileDir = project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}")
        compileTask.macros = binary.objcppCompiler.macros
        compileTask.compilerArgs = binary.objcppCompiler.args

        compileTask
    }
}