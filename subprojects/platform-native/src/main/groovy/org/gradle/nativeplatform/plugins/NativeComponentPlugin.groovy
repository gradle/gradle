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
package org.gradle.nativeplatform.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin
import org.gradle.platform.base.BinaryContainer

/**
 * A plugin that creates tasks used for constructing native binaries.
 */
@Incubating
public class NativeComponentPlugin implements Plugin<ProjectInternal> {

    public void apply(final ProjectInternal project) {
        project.plugins.apply(NativeComponentModelPlugin.class);
        project.plugins.apply(StandardToolChainsPlugin)

        createTasks(project.tasks, project.binaries)
    }

    // TODO:DAZ Convert to a model rule and use simple iteration - this breaks non-rule code that uses binary.tasks.link
    static void createTasks(TaskContainer tasks, BinaryContainer binaries) {
        binaries.withType(NativeBinarySpec) { NativeBinarySpecInternal binary ->
            createTasksForBinary(tasks, binary)
        }
    }

    private static void createTasksForBinary(TaskContainer tasks, NativeBinarySpecInternal binary) {
        def builderTask
        if (binary instanceof NativeExecutableBinarySpec || binary instanceof NativeTestSuiteBinarySpec) {
            builderTask = createLinkExecutableTask(tasks, binary)
            binary.tasks.add createInstallTask(tasks, binary);
        } else if (binary instanceof SharedLibraryBinarySpec) {
            builderTask = createLinkSharedLibraryTask(tasks, binary)
        } else if (binary instanceof StaticLibraryBinarySpec) {
            builderTask = createStaticLibraryTask(tasks, binary)
        } else {
            throw new RuntimeException("Not a valid binary type for building: " + binary)
        }
        binary.tasks.add builderTask
        binary.builtBy builderTask
    }

    private static LinkExecutable createLinkExecutableTask(TaskContainer tasks, def executable) {
        def binary = executable as NativeBinarySpecInternal
        LinkExecutable linkTask = tasks.create(binary.namingScheme.getTaskName("link"), LinkExecutable)
        linkTask.description = "Links ${executable}"

        linkTask.toolChain = binary.toolChain
        linkTask.targetPlatform = executable.targetPlatform

        linkTask.lib { binary.libs*.linkFiles }

        linkTask.conventionMapping.outputFile = { executable.executableFile }
        linkTask.linkerArgs = binary.linker.args
        return linkTask
    }

    private static LinkSharedLibrary createLinkSharedLibraryTask(TaskContainer tasks, SharedLibraryBinarySpec sharedLibrary) {
        def binary = sharedLibrary as NativeBinarySpecInternal
        LinkSharedLibrary linkTask = tasks.create(binary.namingScheme.getTaskName("link"), LinkSharedLibrary)
        linkTask.description = "Links ${sharedLibrary}"

        linkTask.toolChain = binary.toolChain
        linkTask.targetPlatform = binary.targetPlatform

        linkTask.lib { binary.libs*.linkFiles }

        linkTask.conventionMapping.outputFile = { sharedLibrary.sharedLibraryFile }
        linkTask.conventionMapping.installName = { sharedLibrary.sharedLibraryFile.name }
        linkTask.linkerArgs = binary.linker.args
        return linkTask
    }

    private static CreateStaticLibrary createStaticLibraryTask(TaskContainer tasks, StaticLibraryBinarySpec staticLibrary) {
        def binary = staticLibrary as NativeBinarySpecInternal
        CreateStaticLibrary task = tasks.create(binary.namingScheme.getTaskName("create"), CreateStaticLibrary)
        task.description = "Creates ${staticLibrary}"

        task.toolChain = binary.toolChain
        task.targetPlatform = staticLibrary.targetPlatform
        task.conventionMapping.outputFile = { staticLibrary.staticLibraryFile }
        task.staticLibArgs = binary.staticLibArchiver.args
        return task
    }

    private static createInstallTask(TaskContainer tasks, def executable) {
        def binary = executable as NativeBinarySpecInternal
        InstallExecutable installTask = tasks.create(binary.namingScheme.getTaskName("install"), InstallExecutable)
        installTask.description = "Installs a development image of $executable"
        installTask.group = LifecycleBasePlugin.BUILD_GROUP

        installTask.toolChain = binary.toolChain

        def project = installTask.project
        installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/${binary.namingScheme.outputDirectoryBase}") }

        installTask.conventionMapping.executable = { executable.executableFile }
        installTask.lib { binary.libs*.runtimeFiles }

        installTask.dependsOn(executable)
        return installTask
    }
}