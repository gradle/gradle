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
package org.gradle.nativebinaries.language.cpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.plugins.CppLangPlugin
import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.ProjectNativeComponent
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.cpp.tasks.CppCompile
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool
import org.gradle.nativebinaries.plugins.NativeComponentPlugin

/**
 * A plugin for projects wishing to build native binary components from C++ sources.
 *
 * <p>Automatically includes the {@link CppLangPlugin} for core C++ support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link CppCompile} task for each {@link CppSourceSet} to compile the C++ sources.</li>
 */
@Incubating
class CppPlugin implements Plugin<ProjectInternal> {
    void apply(ProjectInternal project) {
        project.plugins.apply(NativeComponentPlugin)
        project.plugins.apply(CppLangPlugin)

        project.nativeComponents.all { ProjectNativeComponent component ->
            component.binaries.all { binary ->
                binary.extensions.create("cppCompiler", DefaultPreprocessingTool)
            }
        }

        project.binaries.withType(ProjectNativeBinary) { ProjectNativeBinaryInternal binary ->
            binary.source.withType(CppSourceSet).all { CppSourceSet sourceSet ->
                if (sourceSet.mayHaveSources) {
                    def compileTask = createCompileTask(project, binary, sourceSet)
                    compileTask.dependsOn sourceSet
                    binary.tasks.add compileTask
                    binary.tasks.createOrLink.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
                }
            }
        }
    }

    private def createCompileTask(ProjectInternal project, ProjectNativeBinaryInternal binary, CppSourceSet sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: CppCompile) {
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
        compileTask.macros = binary.cppCompiler.macros
        compileTask.compilerArgs = binary.cppCompiler.args

        compileTask
    }
}