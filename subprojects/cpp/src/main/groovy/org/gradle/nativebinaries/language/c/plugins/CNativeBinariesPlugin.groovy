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
package org.gradle.nativebinaries.language.c.plugins
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.c.CSourceSet
import org.gradle.language.c.plugins.CLangPlugin
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.c.tasks.CCompile
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
/**
 * A plugin for projects wishing to build native binary components from C sources.
 *
 * <p>Automatically includes the {@link CLangPlugin} for core C++ support and the {@link NativeBinariesPlugin} for native binary support.</p>
 *
 * <li>Creates a {@link CCompile} task for each {@link CSourceSet} to compile the C sources.</li>
 */
@Incubating
class CNativeBinariesPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(CLangPlugin)

        // TODO:DAZ Clean this up (see CppNativeBinariesPlugin)
        project.executables.all { Executable executable ->
            addLanguageExtensionsToComponent(executable)
        }
        project.libraries.all { Library library ->
            addLanguageExtensionsToComponent(library)
        }

        project.binaries.withType(ProjectNativeBinary) { ProjectNativeBinaryInternal binary ->
            binary.source.withType(CSourceSet).all { CSourceSet sourceSet ->
                if (sourceSet.mayHaveSources) {
                    def compileTask = createCompileTask(project, binary, sourceSet)
                    compileTask.dependsOn sourceSet
                    binary.tasks.add compileTask
                    binary.tasks.builder.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
                }
            }
        }
    }

    private def addLanguageExtensionsToComponent(ProjectNativeComponent component) {
        component.binaries.all { binary ->
            binary.extensions.create("cCompiler", DefaultPreprocessingTool)
        }
    }

    private def createCompileTask(ProjectInternal project, ProjectNativeBinaryInternal binary, CSourceSet sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: CCompile) {
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
        compileTask.macros = binary.cCompiler.macros
        compileTask.compilerArgs = binary.cCompiler.args

        compileTask
    }

}