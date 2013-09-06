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
package org.gradle.binaries.language.cpp.plugins
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.cpp.plugins.CppLangPlugin
import org.gradle.binaries.nativebinaries.NativeBinary
import org.gradle.binaries.nativebinaries.NativeDependencySet
import org.gradle.binaries.nativebinaries.SharedLibraryBinary
import org.gradle.binaries.nativebinaries.ToolChainTool
import org.gradle.binaries.nativebinaries.internal.NativeBinaryInternal
import org.gradle.binaries.nativebinaries.plugins.NativeBinariesPlugin
import org.gradle.language.cpp.CppSourceSet
import org.gradle.binaries.language.cpp.tasks.CppCompile
import org.gradle.binaries.nativebinaries.toolchain.plugins.GppCompilerPlugin
import org.gradle.binaries.nativebinaries.toolchain.plugins.MicrosoftVisualCppPlugin

/**
 * A plugin for projects wishing to build native binary components from C++ sources.
 *
 * <p>Automatically includes the {@link org.gradle.language.cpp.plugins.CppLangPlugin} for core C++ support and the {@link NativeBinariesPlugin} for native binary support,
 * together with the {@link MicrosoftVisualCppPlugin} and {@link GppCompilerPlugin} for core toolchain support.</p>
 *
 * <li>Creates a {@link CppCompile} task for each {@link CppSourceSet} to compile the C++ sources.</li>
 */
@Incubating
class CppPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.plugins.apply(GppCompilerPlugin)

        project.plugins.apply(CppLangPlugin)

        // TODO:DAZ It's ugly that we can't do this as project.binaries.all, but this is the way I could
        // add the cppCompiler in time to allow it to be configured within the component.binaries.all block.
        project.executables.all {
            it.binaries.all { binary ->
                binary.ext.cppCompiler = new ToolChainTool()
            }
        }
        project.libraries.all {
            it.binaries.all { binary ->
                binary.ext.cppCompiler = new ToolChainTool()
            }
        }

        project.binaries.withType(NativeBinary) { NativeBinaryInternal binary ->
            binary.source.withType(CppSourceSet).all { CppSourceSet sourceSet ->
                def compileTask = createCompileTask(project, binary, sourceSet)
                binary.builderTask.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
            }
        }
    }

    private def createCompileTask(ProjectInternal project, NativeBinaryInternal binary, def sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: CppCompile) {
            description = "Compiles the $sourceSet sources of $binary"
        }

        compileTask.toolChain = binary.toolChain
        compileTask.positionIndependentCode = binary instanceof SharedLibraryBinary

        compileTask.includes sourceSet.exportedHeaders
        compileTask.source sourceSet.source
        binary.libs.each { NativeDependencySet deps ->
            compileTask.includes deps.includeRoots
        }

        compileTask.conventionMapping.objectFileDir = { project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}") }
        compileTask.macros = binary.macros
        compileTask.compilerArgs = binary.cppCompiler.args

        compileTask
    }
}