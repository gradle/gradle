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
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.language.base.BinaryContainer
import org.gradle.language.base.ProjectSourceSet
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.tasks.*
/**
 * A plugin that creates tasks used for constructing native binaries.
 */
@Incubating
public class NativeBinariesPlugin implements Plugin<Project> {

    public void apply(final Project project) {
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
        binaries.withType(NativeBinary) { NativeBinaryInternal binary ->
            if (!binary.toolChain.canTargetPlatform(binary.getTargetPlatform())) {
                binary.setBuildable(false)
            }
            createTasks(project, binary)
        }

    }

    def createTasks(ProjectInternal project, NativeBinaryInternal binary) {
        def builderTask
        if (binary instanceof StaticLibraryBinary) {
            builderTask = createStaticLibraryTask(project, binary)
        } else {
            builderTask = createLinkTask(project, binary)
        }
        binary.tasks.add builderTask
        binary.builtBy builderTask

        if (binary instanceof ExecutableBinary) {
            createInstallTask(project, (NativeBinaryInternal) binary);
        }
    }

    private AbstractLinkTask createLinkTask(ProjectInternal project, NativeBinaryInternal binary) {
        AbstractLinkTask linkTask = project.task(binary.namingScheme.getTaskName("link"), type: linkTaskType(binary)) {
             description = "Links ${binary}"
         }

        linkTask.toolChain = binary.toolChain
        linkTask.targetPlatform = binary.targetPlatform

        binary.libs.each { NativeDependencySet lib ->
            linkTask.lib lib.linkFiles
        }

        linkTask.conventionMapping.outputFile = { binary.outputFile }
        linkTask.linkerArgs = binary.linker.args
        return linkTask
    }

    private static Class<? extends AbstractLinkTask> linkTaskType(NativeBinary binary) {
        if (binary instanceof SharedLibraryBinary) {
            return LinkSharedLibrary
        }
        return LinkExecutable
    }

    private CreateStaticLibrary createStaticLibraryTask(ProjectInternal project, StaticLibraryBinary binary) {
        def namingScheme = ((NativeBinaryInternal) binary).namingScheme
        CreateStaticLibrary task = project.task(namingScheme.getTaskName("create"), type: CreateStaticLibrary) {
             description = "Creates ${binary}"
         }

        task.toolChain = binary.toolChain
        task.targetPlatform = binary.targetPlatform
        task.conventionMapping.outputFile = { binary.outputFile }
        task.staticLibArgs = binary.staticLibArchiver.args
        return task
    }

    def createInstallTask(ProjectInternal project, NativeBinaryInternal executable) {
        InstallExecutable installTask = project.task(executable.namingScheme.getTaskName("install"), type: InstallExecutable) {
            description = "Installs a development image of $executable"
            group = BasePlugin.BUILD_GROUP
        }

        installTask.toolChain = executable.toolChain
        installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/$executable.namingScheme.outputDirectoryBase") }

        installTask.conventionMapping.executable = { executable.outputFile }
        installTask.lib { executable.libs*.runtimeFiles }

        installTask.dependsOn(executable)
    }
}