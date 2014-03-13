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
package org.gradle.nativebinaries.plugins
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.language.base.BinaryContainer
import org.gradle.language.base.ProjectSourceSet
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.tasks.CreateStaticLibrary
import org.gradle.nativebinaries.tasks.InstallExecutable
import org.gradle.nativebinaries.tasks.LinkExecutable
import org.gradle.nativebinaries.tasks.LinkSharedLibrary
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
/**
 * A plugin that creates tasks used for constructing native binaries.
 */
@Incubating
public class NativeBinariesPlugin implements Plugin<ProjectInternal> {

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesModelPlugin.class);

        // Create a functionalSourceSet for each native component, with the same name
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        project.getExtensions().getByType(ExecutableContainer).all { Executable exe ->
            exe.source projectSourceSet.maybeCreate(exe.name)
        }
        project.getExtensions().getByType(LibraryContainer).all { Library lib ->
            lib.source projectSourceSet.maybeCreate(lib.name)
        }

        final BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        binaries.withType(ProjectNativeBinary) { ProjectNativeBinaryInternal binary ->
            binary.conventionMapping.buildable = { isBuildableBinary(binary) }
            createTasks(project, binary)
        }
    }

    static boolean isBuildableBinary(ProjectNativeBinaryInternal binary) {
        final chain = binary.toolChain as ToolChainInternal
        chain.target(binary.getTargetPlatform()).available
    }

    def createTasks(ProjectInternal project, ProjectNativeBinaryInternal binary) {
        def builderTask
        if (binary instanceof ExecutableBinary) {
            builderTask = createLinkExecutableTask(project, binary as ExecutableBinary)
            binary.tasks.add createInstallTask(project, binary as ExecutableBinary);
        } else if (binary instanceof SharedLibraryBinary) {
            builderTask = createLinkSharedLibraryTask(project, binary)
        } else if (binary instanceof StaticLibraryBinary) {
            builderTask = createStaticLibraryTask(project, binary)
        } else {
            throw new RuntimeException("Not a valid binary type for building: " + binary)
        }
        binary.tasks.add builderTask
        binary.builtBy builderTask
    }

    private LinkExecutable createLinkExecutableTask(ProjectInternal project, ExecutableBinary executable) {
        def binary = executable as ProjectNativeBinaryInternal
        LinkExecutable linkTask = project.task(binary.namingScheme.getTaskName("link"), type: LinkExecutable) {
             description = "Links ${executable}"
         }

        linkTask.toolChain = binary.toolChain
        linkTask.targetPlatform = executable.targetPlatform

        linkTask.lib { binary.libs*.linkFiles }

        linkTask.conventionMapping.outputFile = { executable.executableFile }
        linkTask.linkerArgs = binary.linker.args
        return linkTask
    }

    private LinkSharedLibrary createLinkSharedLibraryTask(ProjectInternal project, SharedLibraryBinary sharedLibrary) {
        def binary = sharedLibrary as ProjectNativeBinaryInternal
        LinkSharedLibrary linkTask = project.task(binary.namingScheme.getTaskName("link"), type: LinkSharedLibrary) {
             description = "Links ${sharedLibrary}"
         }

        linkTask.toolChain = binary.toolChain
        linkTask.targetPlatform = binary.targetPlatform

        linkTask.lib { binary.libs*.linkFiles }

        linkTask.conventionMapping.outputFile = { sharedLibrary.sharedLibraryFile }
        linkTask.conventionMapping.installName = { sharedLibrary.sharedLibraryFile.name }
        linkTask.linkerArgs = binary.linker.args
        return linkTask
    }

    private CreateStaticLibrary createStaticLibraryTask(ProjectInternal project, StaticLibraryBinary staticLibrary) {
        def binary = staticLibrary as ProjectNativeBinaryInternal
        CreateStaticLibrary task = project.task(binary.namingScheme.getTaskName("create"), type: CreateStaticLibrary) {
             description = "Creates ${staticLibrary}"
         }

        task.toolChain = binary.toolChain
        task.targetPlatform = staticLibrary.targetPlatform
        task.conventionMapping.outputFile = { staticLibrary.staticLibraryFile }
        task.staticLibArgs = binary.staticLibArchiver.args
        return task
    }

    def createInstallTask(ProjectInternal project, ExecutableBinary executable) {
        def binary = executable as ProjectNativeBinaryInternal
        InstallExecutable installTask = project.task(binary.namingScheme.getTaskName("install"), type: InstallExecutable) {
            description = "Installs a development image of $executable"
            group = BasePlugin.BUILD_GROUP
        }

        installTask.toolChain = binary.toolChain
        installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/${binary.namingScheme.outputDirectoryBase}") }

        installTask.conventionMapping.executable = { executable.executableFile }
        installTask.lib { binary.libs*.runtimeFiles }

        installTask.dependsOn(executable)
        return installTask
    }
}