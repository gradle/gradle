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
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.SharedLibraryBinarySpecInternal
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal
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
        project.pluginManager.apply(NativeComponentModelPlugin.class);
        project.pluginManager.apply(StandardToolChainsPlugin)

        createTasks(project.extensions.getByType(BinaryContainer))
    }

    // TODO:DAZ Convert to a model rule and use simple iteration - this breaks non-rule code that uses binary.tasks.link
    static void createTasks(BinaryContainer binaries) {
        binaries.withType(NativeBinarySpec) { NativeBinarySpecInternal binary ->
            createTasksForBinary(binary)
        }
    }

    private static void createTasksForBinary(NativeBinarySpecInternal binary) {
        def builderTask
        if (binary instanceof NativeExecutableBinarySpec || binary instanceof NativeTestSuiteBinarySpec) {
            builderTask = createLinkExecutableTask(binary)
            createInstallTask(binary)
        } else if (binary instanceof SharedLibraryBinarySpecInternal) {
            builderTask = createLinkSharedLibraryTask(binary)
        } else if (binary instanceof StaticLibraryBinarySpecInternal) {
            builderTask = createStaticLibraryTask(binary)
        } else {
            throw new RuntimeException("Not a valid binary type for building: " + binary)
        }
        binary.builtBy builderTask
    }

    private static LinkExecutable createLinkExecutableTask(NativeBinarySpecInternal binary) {
        binary.tasks.create(binary.namingScheme.getTaskName("link"), LinkExecutable) { linkTask ->
            linkTask.description = "Links ${binary}"

            linkTask.toolChain = binary.toolChain
            linkTask.targetPlatform = binary.targetPlatform

            linkTask.lib { binary.libs*.linkFiles }

            linkTask.conventionMapping.outputFile = { binary.executableFile }
            linkTask.linkerArgs = binary.linker.args
        }
    }

    private static LinkSharedLibrary createLinkSharedLibraryTask(SharedLibraryBinarySpecInternal binary) {
        binary.tasks.create(binary.namingScheme.getTaskName("link"), LinkSharedLibrary) { linkTask ->
            linkTask.description = "Links ${binary}"

            linkTask.toolChain = binary.toolChain
            linkTask.targetPlatform = binary.targetPlatform

            linkTask.lib { binary.libs*.linkFiles }

            linkTask.conventionMapping.outputFile = { binary.sharedLibraryFile }
            linkTask.conventionMapping.installName = { binary.sharedLibraryFile.name }
            linkTask.linkerArgs = binary.linker.args
        }
    }

    private static CreateStaticLibrary createStaticLibraryTask(StaticLibraryBinarySpecInternal binary) {
        binary.tasks.create(binary.namingScheme.getTaskName("create"), CreateStaticLibrary) { task ->
            task.description = "Creates ${binary}"
            task.toolChain = binary.toolChain
            task.targetPlatform = binary.targetPlatform
            task.conventionMapping.outputFile = { binary.staticLibraryFile }
            task.staticLibArgs = binary.staticLibArchiver.args
        }
    }

    private static createInstallTask(NativeBinarySpecInternal binary) {
        binary.tasks.create(binary.namingScheme.getTaskName("install"), InstallExecutable) { InstallExecutable installTask ->
            installTask.description = "Installs a development image of $binary"
            installTask.group = LifecycleBasePlugin.BUILD_GROUP

            installTask.toolChain = binary.toolChain

            def project = installTask.project
            installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/${binary.namingScheme.outputDirectoryBase}") }

            installTask.conventionMapping.executable = { binary.executableFile }
            installTask.lib { binary.libs*.runtimeFiles }

            installTask.dependsOn(binary)
        }
    }
}