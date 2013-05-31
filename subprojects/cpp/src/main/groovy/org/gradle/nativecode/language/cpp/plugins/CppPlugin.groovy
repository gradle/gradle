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
package org.gradle.nativecode.language.cpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativecode.base.*
import org.gradle.nativecode.base.internal.NativeBinaryInternal
import org.gradle.nativecode.base.plugins.BinariesPlugin
import org.gradle.nativecode.base.tasks.AbstractLinkTask
import org.gradle.nativecode.base.tasks.CreateStaticLibrary
import org.gradle.nativecode.base.tasks.LinkExecutable
import org.gradle.nativecode.base.tasks.LinkSharedLibrary
import org.gradle.nativecode.language.cpp.CppSourceSet
import org.gradle.nativecode.language.cpp.tasks.CppCompile
import org.gradle.nativecode.toolchain.plugins.GppCompilerPlugin
import org.gradle.nativecode.toolchain.plugins.MicrosoftVisualCppPlugin

/**
 * A plugin for projects wishing to build custom components from C++ sources.
 * <p>Automatically includes {@link MicrosoftVisualCppPlugin} and {@link GppCompilerPlugin} for core toolchain support.</p>
 *
 * <p>
 *     For each {@link NativeBinary} found, this plugin will:
 *     <ul>
 *         <li>Create a {@link CppCompile} task named "${binary-name}Compile" to compile the C++ sources.</li>
 *         <li>Create a {@link LinkExecutable}, {@link LinkSharedLibrary} or {@link CreateStaticLibrary} task name "${binary-name}" to create the binary artifact.</li>
 *         <li>Create an InstallTask named "install${Binary-name}" to install any {@link ExecutableBinary} artifact.
 *     </ul>
 * </p>
 */
@Incubating
class CppPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(BinariesPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.plugins.apply(GppCompilerPlugin)
        project.extensions.create("cpp", CppExtension, project)

        // Defaults for all cpp source sets
        project.cpp.sourceSets.all { sourceSet ->
            sourceSet.source.srcDir "src/${sourceSet.name}/cpp"
            sourceSet.exportedHeaders.srcDir "src/${sourceSet.name}/headers"
        }

        project.binaries.withType(NativeBinary) { binary ->
            createTasks(project, binary)
        }
    }

    def createTasks(ProjectInternal project, NativeBinaryInternal binary) {
        // TODO:DAZ Move this logic into NativeBinary
        binary.sourceSets.withType(CppSourceSet).all { CppSourceSet sourceSet ->
            sourceSet.nativeDependencySets.all { NativeDependencySet nativeDependencySet ->
                binary.lib nativeDependencySet
            }
            sourceSet.libs.all { Library lib ->
                binary.lib lib
            }
        }

        CppCompile compileTask = createCompileTask(project, binary)
        if (binary instanceof StaticLibraryBinary) {
            createStaticLibraryTask(project, binary, compileTask)
        } else if (binary instanceof SharedLibraryBinary) {
            createLinkTask(project, binary, compileTask)
        } else { // ExecutableBinary
            AbstractLinkTask linkTask = createLinkTask(project, binary, compileTask)
            createInstallTask(project, binary, linkTask)
        }
    }

    private CppCompile createCompileTask(ProjectInternal project, NativeBinaryInternal binary) {
        CppCompile compileTask = project.task(binary.getTaskName("compile"), type: CppCompile) {
            description = "Compiles $binary"
        }

        compileTask.toolChain = binary.toolChain
        compileTask.forDynamicLinking = binary instanceof SharedLibraryBinary

        // TODO:DAZ Move some of this logic into NativeBinary
        binary.sourceSets.withType(CppSourceSet).all { CppSourceSet sourceSet ->
            compileTask.includes sourceSet.exportedHeaders

            compileTask.source sourceSet.source

            binary.libs.all { NativeDependencySet deps ->
                compileTask.includes deps.includeRoots
            }
        }

        compileTask.conventionMapping.objectFileDir = { project.file("${project.buildDir}/objectFiles/${binary.name}") }
        compileTask.conventionMapping.macros = { binary.macros }
        compileTask.conventionMapping.compilerArgs = { binary.compilerArgs }

        compileTask
    }

    private AbstractLinkTask createLinkTask(ProjectInternal project, NativeBinaryInternal binary, CppCompile compileTask) {
        AbstractLinkTask linkTask = project.task(binary.getTaskName(null), type: linkTaskType(binary)) {
             description = "Links ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        linkTask.toolChain = binary.toolChain

        linkTask.source compileTask.outputs.files.asFileTree

        binary.libs.all { NativeDependencySet lib ->
            linkTask.lib lib.files
        }

        linkTask.conventionMapping.outputFile = { binary.outputFile }
        linkTask.conventionMapping.linkerArgs = { binary.linkerArgs }

        binary.builtBy(linkTask)
        linkTask
    }

    private void createStaticLibraryTask(ProjectInternal project, NativeBinaryInternal binary, CppCompile compileTask) {
        CreateStaticLibrary task = project.task(binary.getTaskName(null), type: CreateStaticLibrary) {
             description = "Creates ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        task.dependsOn compileTask // TODO:DAZ Avoid this explicit dependency by wiring inputs/outputs better
        task.toolChain = binary.toolChain

        task.source project.fileTree(compileTask.objectFileDir)

        task.conventionMapping.outputFile = { binary.outputFile }

        binary.builtBy(task)
    }

    private static Class<? extends AbstractLinkTask> linkTaskType(NativeBinary binary) {
        if (binary instanceof SharedLibraryBinary) {
            return LinkSharedLibrary
        }
        return LinkExecutable
    }

    def createInstallTask(ProjectInternal project, NativeBinaryInternal executable, Task linkTask) {
        project.task(executable.getTaskName("install"), type: Sync) {
            description = "Installs a development image of $executable"
            group = BasePlugin.BUILD_GROUP
            into { project.file("${project.buildDir}/install/$executable.name") }
            dependsOn linkTask
            if (OperatingSystem.current().windows) {
                from { executable.outputFile }
                from { executable.libs*.files }
            } else {
                into("lib") {
                    from { executable.outputFile }
                    from { executable.libs*.files }
                }
                doLast {
                    def script = new File(destinationDir, executable.outputFile.name)
                    script.text = """
#/bin/sh
APP_BASE_NAME=`dirname "\$0"`
export DYLD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
export LD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
exec "\$APP_BASE_NAME/lib/${executable.outputFile.name}" \"\$@\"
                    """
                    ant.chmod(perm: 'u+x', file: script)
                }
            }
        }
    }
}